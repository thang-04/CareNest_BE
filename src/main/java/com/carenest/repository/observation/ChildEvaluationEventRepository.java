package com.carenest.repository.observation;

import com.carenest.entity.observation.ChildEvaluationEvent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildEvaluationEventRepository extends JpaRepository<ChildEvaluationEvent, Long> {

    List<ChildEvaluationEvent> findByEvaluationIdInOrderByAtAscIdAsc(Collection<Long> evaluationIds);
}
