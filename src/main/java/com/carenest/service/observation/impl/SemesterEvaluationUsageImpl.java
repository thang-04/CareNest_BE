package com.carenest.service.observation.impl;

import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.service.organization.SemesterUsageCheck;
import com.carenest.utils.enums.EvaluationPeriodType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Học kỳ đã có đánh giá học kỳ (bản nháp hoặc đã xác nhận) thì khóa ngày học kỳ (OBS-06). */
@Component
@RequiredArgsConstructor
public class SemesterEvaluationUsageImpl implements SemesterUsageCheck {

    private final ChildEvaluationRepository evaluationRepository;

    @Override
    public boolean usedBySemester(Long schoolYearId, LocalDate start, LocalDate end) {
        return evaluationRepository.existsBySchoolYearIdAndPeriodTypeAndPeriodStartBetween(
                schoolYearId, EvaluationPeriodType.SEMESTER, start, end);
    }
}
