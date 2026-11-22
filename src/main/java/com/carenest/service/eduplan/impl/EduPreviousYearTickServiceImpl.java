package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.PreviousYearTicksResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanExpectedResultRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.repository.eduplan.EduPlanSubDomainRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduPreviousYearTickService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EduPreviousYearTickServiceImpl implements EduPreviousYearTickService {
    private static final String LOG_PREFIX = "[EduPreviousYearTickServiceImpl]";

    private final EduYearPlanService yearPlanService;
    private final SchoolStructureService schoolStructureService;
    private final EduPlanRepository planRepository;
    private final EduThemeRepository themeRepository;
    private final EduPlanSubDomainRepository subDomainRepository;
    private final EduPlanExpectedResultRepository expectedResultRepository;
    private final EduPlanGoalRepository goalRepository;
    private final EduGoalContentRepository goalContentRepository;
    private final EduContentThemeTickRepository tickRepository;

    @Override
    @Transactional(readOnly = true)
    public PreviousYearTicksResponse getPreviousYearTicks(CurrentUser currentUser, Long planId) {
        String logPrefix = LOG_PREFIX + "|getPreviousYearTicks|userId=" + currentUser.userId() + "|planId=" + planId;
        log.info("{}|START", logPrefix);
        try {
            // Quyền theo kế hoạch đang soạn (người soạn kế hoạch tổ, bản DRAFT); nguồn năm trước không qua canView (EDU-104 Q1=A)
            YearPlanDetailResponse plan = yearPlanService.getYearPlan(currentUser, planId);
            if (plan.planLevel() != EduPlanLevel.TEAM || !plan.canAssignContents())
                throw GlobalException.of(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
            Optional<SchoolYearResponse> previous = previousYear(plan.schoolYearId());
            if (previous.isEmpty()) {
                log.info("{}|END|reason={}", logPrefix, PreviousYearTicksResponse.NO_PREVIOUS_YEAR);
                return PreviousYearTicksResponse.none(PreviousYearTicksResponse.NO_PREVIOUS_YEAR);
            }
            SchoolYearResponse year = previous.get();
            Optional<EduPlan> source = latestApproved(year.id(), plan.ageGroupId());
            if (source.isEmpty()) source = sameAgeRange(year.id(), plan.ageGroupId());
            if (source.isEmpty()) {
                log.info("{}|END|reason={}", logPrefix, PreviousYearTicksResponse.NO_APPROVED_PLAN);
                return PreviousYearTicksResponse.none(PreviousYearTicksResponse.NO_APPROVED_PLAN);
            }
            PreviousYearTicksResponse result = read(source.get(), year);
            log.info(
                    "{}|END|sourcePlanId={}|ticks={}",
                    logPrefix,
                    source.get().getId(),
                    result.ticks().size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Năm học liền trước = năm có ngày bắt đầu lớn nhất còn nhỏ hơn ngày bắt đầu năm của kế hoạch
    private Optional<SchoolYearResponse> previousYear(Long schoolYearId) {
        List<SchoolYearResponse> years = schoolStructureService.findSchoolYears();
        return years.stream()
                .filter(y -> Objects.equals(y.id(), schoolYearId))
                .findFirst()
                .filter(current -> current.startDate() != null)
                .flatMap(current -> years.stream()
                        .filter(y -> y.startDate() != null && y.startDate().isBefore(current.startDate()))
                        .max(Comparator.comparing(SchoolYearResponse::startDate)));
    }

    private Optional<EduPlan> latestApproved(Long schoolYearId, Long ageGroupId) {
        return planRepository
                .findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                        EduPlanLevel.TEAM, schoolYearId, ageGroupId)
                .stream()
                .filter(p -> p.getStatus() == EduPlanStatus.APPROVED)
                .max(Comparator.comparingInt(EduPlan::getVersionNo));
    }

    // Nhóm tuổi năm trước có thể là bản ghi khác nhưng cùng khoảng tháng tuổi
    private Optional<EduPlan> sameAgeRange(Long schoolYearId, Long ageGroupId) {
        Optional<AgeGroupResponse> current = schoolStructureService.findAgeGroup(ageGroupId);
        if (current.isEmpty() || current.get().ageFromMonths() == null || current.get().ageToMonths() == null)
            return Optional.empty();
        AgeGroupResponse age = current.get();
        return schoolStructureService.findAgeGroups().stream()
                .filter(g -> !Objects.equals(g.id(), ageGroupId)
                        && Objects.equals(g.ageFromMonths(), age.ageFromMonths())
                        && Objects.equals(g.ageToMonths(), age.ageToMonths()))
                .map(g -> latestApproved(schoolYearId, g.id()))
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(EduPlan::getVersionNo));
    }

    private PreviousYearTicksResponse read(EduPlan source, SchoolYearResponse year) {
        Long sourceId = source.getId();
        List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId);
        List<EduGoalRow> rows = new ArrayList<>();
        rows.addAll(subDomainRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId));
        rows.addAll(expectedResultRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId));
        rows.addAll(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(sourceId));
        Map<Long, Long> domainByRow = new HashMap<>();
        rows.forEach(r -> domainByRow.put(r.getId(), r.getDomainId()));
        Map<Long, EduGoalContent> contents = new HashMap<>();
        if (!rows.isEmpty())
            goalContentRepository
                    .findAllByGoalRowIdInOrderBySortOrderAsc(domainByRow.keySet())
                    .forEach(c -> contents.put(c.getId(), c));
        List<PreviousYearTicksResponse.Tick> ticks = new ArrayList<>();
        List<Long> themeIds = themes.stream().map(EduTheme::getId).toList();
        if (!themeIds.isEmpty())
            for (EduContentThemeTick t : tickRepository.findAllByThemeIdIn(themeIds)) {
                EduGoalContent c = contents.get(t.getContentId());
                // Chỉ tick trên nội dung của chính kế hoạch nguồn; nội dung trống không ghép được (EDU-50)
                if (c == null || c.getText() == null || c.getText().isBlank()) continue;
                ticks.add(new PreviousYearTicksResponse.Tick(
                        domainByRow.get(c.getGoalRowId()), c.getText().trim(), t.getThemeId()));
            }
        return new PreviousYearTicksResponse(
                new PreviousYearTicksResponse.Source(
                        sourceId, source.getCode(), year.id(), year.name(), source.getVersionNo()),
                null,
                themes.stream()
                        .map(t -> new PreviousYearTicksResponse.Theme(t.getId(), t.getName()))
                        .toList(),
                ticks);
    }
}
