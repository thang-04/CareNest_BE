package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduPlanApproval;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduPlanApprovalRepository extends JpaRepository<EduPlanApproval, Long> {

    List<EduPlanApproval> findAllByYearPlanIdOrderByApprovedAtAsc(Long yearPlanId);
}
