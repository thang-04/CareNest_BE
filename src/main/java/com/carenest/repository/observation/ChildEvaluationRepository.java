package com.carenest.repository.observation;

import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.EvaluationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildEvaluationRepository extends JpaRepository<ChildEvaluation, Long> {

    List<ChildEvaluation> findByClassroomIdInAndPeriodType(
            Collection<Long> classroomIds, EvaluationPeriodType periodType);

    List<ChildEvaluation> findByChildIdAndStatusAndPeriodEndGreaterThanEqualAndPeriodStartLessThanEqual(
            Long childId, EvaluationStatus status, LocalDate from, LocalDate to);

    List<ChildEvaluation> findByChildIdAndPeriodTypeAndStatusAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long childId, EvaluationPeriodType periodType, EvaluationStatus status, LocalDate from, LocalDate to);
}
