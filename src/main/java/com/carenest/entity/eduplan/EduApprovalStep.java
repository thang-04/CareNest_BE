package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Thứ tự người ký theo loại tài liệu; kế hoạch năm chỉ dùng để in khối chữ ký. */
@Getter
@Entity
@Table(name = "edu_approval_steps")
@IdClass(EduApprovalStep.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduApprovalStep {

    @Id
    @Column(name = "doc_type", length = 20)
    private String docType;

    @Id
    @Column(name = "step_order")
    private Short stepOrder;

    @Column(name = "approver_role", nullable = false, length = 30)
    private String approverRole;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String docType;
        private Short stepOrder;
    }
}
