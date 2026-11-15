package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlanBranchTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduPlanBranchTemplateRepository extends JpaRepository<EduPlanBranchTemplate, Long> {
    List<EduPlanBranchTemplate> findAllByYearPlanIdOrderBySortOrderAsc(Long planId);

    void deleteAllByYearPlanId(Long planId);
}
