package com.carenest.service.observation.impl;

import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.RewardListRepository;
import com.carenest.service.organization.SemesterUsageCheck;
import com.carenest.utils.enums.EvaluationPeriodType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Học kỳ đã có đánh giá học kỳ (bản nháp hoặc đã xác nhận) thì khóa ngày học kỳ (OBS-06). Đánh giá cuối năm và đề
 * xuất khen thưởng mở từ ngày kết thúc học kỳ II nên đã có thì khóa ngày học kỳ II.
 */
@Component
@RequiredArgsConstructor
public class SemesterEvaluationUsageImpl implements SemesterUsageCheck {

    private final ChildEvaluationRepository evaluationRepository;
    private final com.carenest.repository.observation.SemesterAssessmentRepository semesterRepository;
    private final RewardListRepository rewardListRepository;

    @Override
    public boolean usedBySemester(Long schoolYearId, LocalDate start, LocalDate end) {
        return semesterRepository.existsBySchoolYearIdAndStartBetween(schoolYearId, start, end)
                || evaluationRepository.existsBySchoolYearIdAndPeriodTypeAndPeriodStartBetween(
                        schoolYearId, EvaluationPeriodType.SEMESTER, start, end);
    }

    @Override
    public boolean usedByYearEnd(Long schoolYearId) {
        return evaluationRepository.existsBySchoolYearIdAndPeriodType(schoolYearId, EvaluationPeriodType.YEAR)
                || rewardListRepository.existsBySchoolYearId(schoolYearId);
    }
}
