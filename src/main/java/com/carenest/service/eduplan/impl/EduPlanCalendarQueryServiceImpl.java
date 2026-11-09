package com.carenest.service.eduplan.impl;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.service.eduplan.EduPlanCalendarQueryService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.EduClassDocStatus;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class EduPlanCalendarQueryServiceImpl implements EduPlanCalendarQueryService {

    private static final String LOG_PREFIX = "[EduPlanCalendarQueryServiceImpl]";

    private final ClassroomService classroomService;
    private final EduPlanRepository yearPlanRepository;
    private final EduClassDocRepository classDocRepository;
    private final EduThemeRepository themeRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<EffectiveTheme> findApprovedThemes(Long classroomId) {
        String logPrefix = LOG_PREFIX + "|findApprovedThemes|classId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            Optional<ClassroomInfoResponse> clsOpt = classroomService.findClassroomInfo(classroomId);
            if (clsOpt.isEmpty()) {
                log.info("{}|END|classNotFound", logPrefix);
                return List.of();
            }
            ClassroomInfoResponse cls = clsOpt.get();

            Optional<EduPlan> currentTeamPlan = yearPlanRepository
                    .findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                            EduPlanLevel.TEAM, cls.schoolYearId(), cls.ageGroupId())
                    .stream()
                    .filter(p -> p.getStatus() == EduPlanStatus.APPROVED)
                    .reduce((first, second) -> second);

            if (currentTeamPlan.isEmpty()) {
                log.info("{}|END|noApprovedTeamPlan", logPrefix);
                return List.of();
            }

            EduPlan teamPlan = currentTeamPlan.get();
            List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(teamPlan.getId());
            Map<Long, EduTheme> themesById =
                    themes.stream().collect(Collectors.toMap(EduTheme::getId, Function.identity(), (a, b) -> a));

            List<EduClassDoc> branchDocs =
                    classDocRepository.findAllByClassroomIdAndYearPlanId(classroomId, teamPlan.getId());
            Optional<EduClassDoc> approvedBranch = branchDocs.stream()
                    .filter(d -> EduClassDoc.BRANCH.equals(d.getKind()) && d.getStatus() == EduClassDocStatus.APPROVED)
                    .findFirst();

            if (approvedBranch.isPresent()) {
                try {
                    JsonNode root = objectMapper.readTree(approvedBranch.get().getContent());
                    JsonNode schedules = root.path("themeSchedules");
                    if (schedules.isArray() && schedules.size() > 0) {
                        List<EffectiveTheme> classThemes = new ArrayList<>();
                        for (JsonNode s : schedules) {
                            Long themeId = s.path("themeId").asLong();
                            EduTheme theme = themesById.get(themeId);
                            if (theme != null) {
                                LocalDate start = s.has("startDate")
                                                && !s.path("startDate").isNull()
                                        ? LocalDate.parse(s.path("startDate").asText())
                                        : theme.getStartDate();
                                LocalDate end = s.has("endDate")
                                                && !s.path("endDate").isNull()
                                        ? LocalDate.parse(s.path("endDate").asText())
                                        : theme.getEndDate();
                                classThemes.add(new EffectiveTheme(
                                        theme.getId(), theme.lineage(), teamPlan.getId(), theme.getName(), start, end));
                            }
                        }
                        if (!classThemes.isEmpty()) {
                            classThemes.sort(Comparator.comparing(
                                    EffectiveTheme::startDate, Comparator.nullsLast(Comparator.naturalOrder())));
                            log.info("{}|END|classBranchScheduleCount={}", logPrefix, classThemes.size());
                            return List.copyOf(classThemes);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("{}|parseBranchCalendarError|msg={}", logPrefix, ex.getMessage());
                }
            }

            // Fallback to TEAM plan themes
            List<EffectiveTheme> fallback = themes.stream()
                    .filter(t -> t.getStartDate() != null && t.getEndDate() != null)
                    .sorted(Comparator.comparing(EduTheme::getStartDate))
                    .map(t -> new EffectiveTheme(
                            t.getId(), t.lineage(), teamPlan.getId(), t.getName(), t.getStartDate(), t.getEndDate()))
                    .toList();

            log.info("{}|END|fallbackTeamCount={}", logPrefix, fallback.size());
            return fallback;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
