package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Lượt duyệt/ban hành, dùng in khối chữ ký. */
@Getter
@Entity
@Table(name = "edu_plan_approvals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduPlanApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_plan_id", nullable = false, updatable = false)
    private Long yearPlanId;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Column(name = "approver_role", nullable = false, length = 30)
    private String approverRole;

    @Column(name = "approver_title", length = 100)
    private String approverTitle;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    public static EduPlanApproval of(
            Long yearPlanId,
            Long approverId,
            String approverRole,
            String approverTitle,
            Instant approvedAt,
            String note) {
        EduPlanApproval approval = new EduPlanApproval();
        approval.yearPlanId = yearPlanId;
        approval.approverId = approverId;
        approval.approverRole = approverRole;
        approval.approverTitle = approverTitle;
        approval.approvedAt = approvedAt;
        approval.note = note;
        return approval;
    }
}
