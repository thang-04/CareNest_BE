package com.carenest.service.eduplan.impl;

import com.carenest.entity.eduplan.*;
import com.carenest.repository.eduplan.*;
import com.carenest.service.eduplan.EduTeamPlanService;
import com.carenest.utils.enums.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class EduTeamUpdateGenerator {
    private static final String LOG_PREFIX = "[EduTeamUpdateGenerator]";
    private final EduPlanRepository plans;
    private final EduPlanSubDomainRepository subDomainRepository;
    private final EduPlanExpectedResultRepository expectedResultRepository;
    private final EduPlanGoalRepository goalRepository;
    private final EduGoalContentRepository goalContentRepository;
    private final EduThemeRepository themeRepository;
    private final EduBreakRepository breakRepository;
    private final EduContentThemeTickRepository tickRepository;
    private final EduPlanBranchTemplateRepository templateRepository;
    private final EduTeamPlanService teamService;
    private final com.carenest.service.organization.StaffAssignmentService assignments;
    private final com.carenest.service.account.AccountService accounts;
    private final java.time.Clock clock;
    private final EduPlanTimeLock timeLock;

    void updateAfterPublication(EduPlan annual, Instant now) {
        String prefix = LOG_PREFIX + "|updateAfterPublication|planId=" + annual.getId();
        log.info("{}|START", prefix);
        try {
            if (annual.getPlanLevel() != EduPlanLevel.SCHOOL_YEAR || annual.getStatus() != EduPlanStatus.APPROVED)
                return;
            var years = plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                    EduPlanLevel.SCHOOL_YEAR, annual.getSchoolYearId(), annual.getAgeGroupId());
            if (years.stream()
                    .anyMatch(p -> p.getStatus() == EduPlanStatus.APPROVED && p.getVersionNo() > annual.getVersionNo()))
                return;
            Set<Long> sources = new HashSet<>();
            years.forEach(p -> sources.add(p.getId()));
            var chain = plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                    EduPlanLevel.TEAM, annual.getSchoolYearId(), annual.getAgeGroupId());
            if (chain.isEmpty()) {
                var leaders = accounts.findActiveUserIds(assignments.findActiveTeamLeaderIds(
                        annual.getAgeGroupId(), now.atZone(clock.getZone()).toLocalDate()));
                if (leaders.size() != 1)
                    throw com.carenest.exception.GlobalException.of(
                            com.carenest.utils.ApiCode.INVALID_VALUE,
                            "cần đúng một tổ trưởng đang được phân công cho nhóm tuổi để soạn kế hoạch tổ");
                Long ownerId = leaders.iterator().next();
                EduPlan target = EduPlan.createDraft(
                        EduPlanLevel.TEAM,
                        years.getFirst().getCode().replaceFirst("^KHN-", ""),
                        annual.getSchoolYearId(),
                        annual.getAgeGroupId(),
                        "Kế hoạch giáo dục tổ",
                        "",
                        ownerId,
                        "Tổ trưởng",
                        now);
                target.initializePlanSource(
                        EduPlanLevel.TEAM, annual.getId(), false, annual.getDurationPolicySnapshot());
                plans.saveAndFlush(target);
                copyContent(annual.getId(), target.getId());
                log.info("{}|END", prefix);
                return;
            }
            var open = chain.stream()
                    .filter(p -> p.getStatus() != EduPlanStatus.APPROVED)
                    .findFirst();
            EduPlan prior = open.orElseGet(() -> chain.stream()
                    .filter(p -> p.getStatus() == EduPlanStatus.APPROVED)
                    .max(Comparator.comparingInt(EduPlan::getVersionNo))
                    .orElse(null));
            // Spec 004 (Q6): kế hoạch tổ đã khóa theo năm học thì không nhận cập nhật nguồn, tới khi HT mở khóa
            if (prior != null && timeLock.locked(prior)) return;
            if (prior == null
                    || prior.getCurriculumSourcePlanId() == null
                    || !sources.contains(prior.getCurriculumSourcePlanId())
                    || Objects.equals(prior.getCurriculumSourcePlanId(), annual.getId())) return;
            EduTeamPlan target;
            if (open.isPresent()) {
                target = (EduTeamPlan) plans.findForUpdateById(prior.getId()).orElseThrow();
            } else {
                target = (EduTeamPlan) EduPlan.createVersion(
                        prior,
                        chain.getFirst().getCode() + "-V" + (prior.getVersionNo() + 1),
                        prior.getOwnerId(),
                        prior.getOwnerTitle(),
                        now);
                target.initializePlanSource(
                        EduPlanLevel.TEAM, prior.getCurriculumSourcePlanId(), false, prior.getDurationPolicySnapshot());
                plans.saveAndFlush(target);
                copyContent(prior.getId(), target.getId());
            }
            teamService.synchronizeSource(target, annual);
            plans.save(target);
            log.info("{}|END", prefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", prefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    void copyContent(Long sourceId, Long targetId) {
        EduPlan owner = requirePlan(targetId);
        Map<Long, Long> themeIds = new HashMap<>();
        for (EduTheme theme : themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId)) {
            themeIds.put(
                    theme.getId(),
                    themeRepository.save(ownedTheme(owner, theme)).getId());
        }
        breakRepository.saveAll(breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId).stream()
                .map(b -> {
                    var copy = EduBreak.copyFor(targetId, b);
                    copy.initializeOwner(owner);
                    return copy;
                })
                .toList());
        List<EduGoalRow> sourceRows = findRows(sourceId);
        Map<Long, Long> rowIds = new HashMap<>();
        for (EduGoalRow row : sourceRows) {
            EduGoalRow copy = EduGoalRow.copyFor(targetId, row);
            copy.initializeOwner(owner);
            copy.attachParents(rowIds.get(row.getSubDomainId()), rowIds.get(row.getExpectedResultId()));
            rowIds.put(row.getId(), saveRow(copy).getId());
        }
        Map<Long, Long> contentIds = new HashMap<>();
        if (!rowIds.isEmpty()) {
            for (EduGoalContent content : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(
                    sourceRows.stream().map(EduGoalRow::getId).toList())) {
                EduGoalContent copy = EduGoalContent.copyFor(rowIds.get(content.getGoalRowId()), content);
                contentIds.put(content.getId(), goalContentRepository.save(copy).getId());
            }
        }
        goalContentRepository.flush();
        if (!themeIds.isEmpty()) {
            tickRepository.saveAll(tickRepository.findAllByThemeIdIn(themeIds.keySet()).stream()
                    .filter(t -> contentIds.containsKey(t.getContentId()))
                    .map(t -> EduContentThemeTick.of(contentIds.get(t.getContentId()), themeIds.get(t.getThemeId())))
                    .toList());
        }
        EduPlan target = requirePlan(targetId);
        for (EduPlanBranchTemplate t : templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId)) {
            var template = EduPlanBranchTemplate.create(
                    targetId,
                    themeIds.get(t.getThemeId()),
                    Objects.equals(target.getCurriculumSourcePlanId(), sourceId) ? t.getId() : t.getSourceTemplateId(),
                    t.lineage(),
                    t.getName(),
                    t.getStartDate(),
                    t.getEndDate(),
                    t.getSortOrder());
            template.initializeOwner(owner);
            templateRepository.save(template);
        }
    }

    private EduTheme ownedTheme(EduPlan owner, EduTheme source) {
        var copy = EduTheme.copyFor(owner.getId(), source);
        copy.initializeOwner(owner);
        return copy;
    }

    private EduPlan requirePlan(Long id) {
        return plans.findById(id).orElseThrow();
    }

    private List<EduGoalRow> findRows(Long id) {
        List<EduGoalRow> out = new ArrayList<>();
        out.addAll(subDomainRepository.findAllByYearPlanIdOrderBySortOrderAsc(id));
        out.addAll(expectedResultRepository.findAllByYearPlanIdOrderBySortOrderAsc(id));
        out.addAll(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(id));
        out.sort(Comparator.comparing(EduGoalRow::getDomainId).thenComparing(EduGoalRow::getSortOrder));
        return out;
    }

    private EduGoalRow saveRow(EduGoalRow row) {
        return row instanceof EduPlanGoal g
                ? goalRepository.save(g)
                : row instanceof EduPlanSubDomain s
                        ? subDomainRepository.save(s)
                        : expectedResultRepository.save((EduPlanExpectedResult) row);
    }
}
