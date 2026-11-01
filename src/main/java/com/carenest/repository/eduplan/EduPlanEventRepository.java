package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlanEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduPlanEventRepository extends JpaRepository<EduPlanEvent, Long> {

    List<EduPlanEvent> findAllByYearPlanIdOrderByOccurredAtAscIdAsc(Long yearPlanId);

    Optional<EduPlanEvent> findFirstByYearPlanIdOrderByOccurredAtDescIdDesc(Long yearPlanId);
}
