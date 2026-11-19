package com.carenest.entity.account;

import com.carenest.utils.enums.ProfileChangeKind;
import com.carenest.utils.enums.ProfileChangeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Yêu cầu sửa hồ sơ/khai báo sức khỏe trẻ do GV được HT cấp thêm CHILD_PROFILE_EDIT gửi (CHD-11, user chốt 2026-11-15).
 * {@code payload} giữ nguyên request GV gửi (JSON); hồ sơ chỉ đổi khi HT hoặc PHT điểm trường của trẻ duyệt.
 */
@Getter
@Entity
@Table(name = "child_profile_change_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private ProfileChangeKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileChangeStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChildProfileChangeRequest open(
            Long childId, ProfileChangeKind kind, String payload, Long requestedBy, Instant at) {
        ChildProfileChangeRequest request = new ChildProfileChangeRequest();
        request.childId = childId;
        request.kind = kind;
        request.payload = payload;
        request.status = ProfileChangeStatus.PENDING;
        request.requestedBy = requestedBy;
        request.requestedAt = at;
        return request;
    }

    public boolean isPending() {
        return status == ProfileChangeStatus.PENDING;
    }

    public void approve(Long userId, Instant at) {
        decide(ProfileChangeStatus.APPROVED, userId, null, at);
    }

    public void reject(Long userId, String note, Instant at) {
        decide(ProfileChangeStatus.REJECTED, userId, note, at);
    }

    /** Yêu cầu mới cùng loại cho cùng trẻ thay thế yêu cầu đang chờ (không có người quyết định). */
    public void cancel() {
        this.status = ProfileChangeStatus.CANCELLED;
    }

    private void decide(ProfileChangeStatus result, Long userId, String note, Instant at) {
        this.status = result;
        this.decidedBy = userId;
        this.decidedAt = at;
        this.decisionNote = note;
    }

    @Override
    public String toString() {
        return "ChildProfileChangeRequest[id=" + id + ", kind=" + kind + ", status=" + status + "]";
    }
}
