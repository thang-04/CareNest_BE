package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlanEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduPlanEventRepository extends JpaRepository<EduPlanEvent, Long> {

    List<EduPlanEvent> findAllByYearPlanIdOrderByOccurredAtAscIdAsc(Long yearPlanId);

    @Query("""
            select e.yearPlanId, e.action from EduPlanEvent e
            where e.yearPlanId in :planIds and e.action in ('SUBMIT', 'ISSUE', 'RETURN')
            order by e.occurredAt desc, e.id desc
            """)
    List<Object[]> findReviewTransitions(@Param("planIds") Collection<Long> planIds);

    Optional<EduPlanEvent> findFirstByYearPlanIdOrderByOccurredAtDescIdDesc(Long yearPlanId);
}
