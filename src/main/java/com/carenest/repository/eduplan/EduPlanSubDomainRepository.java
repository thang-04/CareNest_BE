package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlanSubDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduPlanSubDomainRepository extends JpaRepository<EduPlanSubDomain, Long> {
    List<EduPlanSubDomain> findAllByYearPlanIdOrderBySortOrderAsc(Long yearPlanId);
}
