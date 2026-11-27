package com.carenest.service.eduplan.impl;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.service.eduplan.EduPreviousYearContentService;
import com.carenest.service.organization.SchoolStructureService;
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

/** Đọc nội dung kế hoạch năm học năm liền trước cho AI gợi ý (EDU-106/115). */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduPreviousYearContentServiceImpl implements EduPreviousYearContentService {
    private static final String LOG_PREFIX = "[EduPreviousYearContentServiceImpl]";

    private final SchoolStructureService schoolStructureService;
    private final EduPlanRepository planRepository;
    private final EduPlanGoalRepository goalRepository;
    private final EduGoalContentRepository goalContentRepository;

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

    private Optional<EduPlan> latestApproved(EduPlanLevel level, Long schoolYearId, Long ageGroupId) {
        return planRepository
                .findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(level, schoolYearId, ageGroupId)
                .stream()
                .filter(p -> p.getStatus() == EduPlanStatus.APPROVED)
                .max(Comparator.comparingInt(EduPlan::getVersionNo));
    }

    // Nhóm tuổi năm trước có thể là bản ghi khác nhưng cùng khoảng tháng tuổi
    private Optional<EduPlan> sameAgeRange(EduPlanLevel level, Long schoolYearId, Long ageGroupId) {
        Optional<AgeGroupResponse> current = schoolStructureService.findAgeGroup(ageGroupId);
        if (current.isEmpty()
                || current.get().ageFromMonths() == null
                || current.get().ageToMonths() == null) return Optional.empty();
        AgeGroupResponse age = current.get();
        return schoolStructureService.findAgeGroups().stream()
                .filter(g -> !Objects.equals(g.id(), ageGroupId)
                        && Objects.equals(g.ageFromMonths(), age.ageFromMonths())
                        && Objects.equals(g.ageToMonths(), age.ageToMonths()))
                .map(g -> latestApproved(level, schoolYearId, g.id()))
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(EduPlan::getVersionNo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PreviousContent> previousYearContents(Long schoolYearId, Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|previousYearContents|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Optional<EduPlan> source = previousYear(schoolYearId)
                    .flatMap(y -> latestApproved(EduPlanLevel.SCHOOL_YEAR, y.id(), ageGroupId)
                            .or(() -> sameAgeRange(EduPlanLevel.SCHOOL_YEAR, y.id(), ageGroupId)));
            if (source.isEmpty()) {
                log.info("{}|END|contents=0", logPrefix);
                return List.of();
            }
            Map<Long, EduPlanGoal> goals = new HashMap<>();
            goalRepository
                    .findAllByYearPlanIdOrderBySortOrderAsc(source.get().getId())
                    .forEach(g -> goals.put(g.getId(), g));
            List<PreviousContent> result = new ArrayList<>();
            if (!goals.isEmpty())
                for (EduGoalContent c : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(goals.keySet())) {
                    EduPlanGoal g = goals.get(c.getGoalRowId());
                    if (g == null || c.getText() == null || c.getText().isBlank()) continue;
                    result.add(new PreviousContent(
                            g.getDomainId(), g.getText(), c.getText().trim()));
                }
            log.info(
                    "{}|END|sourcePlanId={}|contents={}",
                    logPrefix,
                    source.get().getId(),
                    result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
