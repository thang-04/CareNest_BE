package com.carenest.service.observation.impl;

import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.service.child.ProgramCompletionLookup;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.EvaluationStatus;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Đánh dấu hoàn thành chương trình ở đánh giá cuối năm đã xác nhận của năm học (OBS-14, mục 8 sổ chỉ đạo). */
@Component
@RequiredArgsConstructor
public class ProgramCompletionLookupImpl implements ProgramCompletionLookup {

    private final ChildEvaluationRepository evaluationRepository;

    @Override
    public Map<Long, Boolean> programCompletion(Long schoolYearId, Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return Map.of();
        }
        return evaluationRepository
                .findBySchoolYearIdAndPeriodTypeAndStatusAndChildIdIn(
                        schoolYearId, EvaluationPeriodType.YEAR, EvaluationStatus.CONFIRMED, childIds)
                .stream()
                .filter(e -> e.getProgramCompleted() != null)
                // Trẻ chuyển lớp có thể có hai đánh giá cuối năm: có một bản hoàn thành là hoàn thành
                .collect(Collectors.toMap(
                        ChildEvaluation::getChildId, ChildEvaluation::getProgramCompleted, Boolean::logicalOr));
    }
}
