package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduApprovalStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduApprovalStepRepository extends JpaRepository<EduApprovalStep, EduApprovalStep.Key> {

    List<EduApprovalStep> findAllByDocTypeOrderByStepOrderAsc(String docType);
}
