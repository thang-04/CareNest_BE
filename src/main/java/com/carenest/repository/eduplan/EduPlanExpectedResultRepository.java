package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlanExpectedResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduPlanExpectedResultRepository extends JpaRepository<EduPlanExpectedResult, Long> {
    List<EduPlanExpectedResult> findAllByYearPlanIdOrderBySortOrderAsc(Long yearPlanId);
}
