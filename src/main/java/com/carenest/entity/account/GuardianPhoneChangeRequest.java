package com.carenest.entity.account;

import com.carenest.utils.enums.PhoneChangeStatus;
import com.carenest.utils.enums.SmsStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Yêu cầu đổi SĐT của một phụ huynh trên hồ sơ trẻ (G94-180/AUTH-21, user chốt 2026-11-10, sửa 2026-11-11): có tài
 * khoản ⇒ {@code userId}, SĐT = tên đăng nhập; chưa có ⇒ {@code userId} null. Hồ sơ giữ số cũ tới khi HT, hoặc PHT có
 * quyền quản lý tài khoản phụ huynh ở điểm trường của trẻ, duyệt. {@code guardianId} null khi phụ huynh đã bị bỏ khỏi
 * hồ sơ (V88). Không lưu mật khẩu.
 */
@Getter
@Entity
@Table(name = "guardian_phone_change_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianPhoneChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Column(name = "guardian_id", updatable = false)
    private Long guardianId;

    @Column(name = "old_phone", nullable = false, updatable = false, length = 10)
    private String oldPhone;

    @Column(name = "new_phone", nullable = false, updatable = false, length = 10)
    private String newPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhoneChangeStatus status;

    @Column(nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "sms_status", length = 10)
    private SmsStatus smsStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static GuardianPhoneChangeRequest open(
            Long userId,
            Long childId,
            Long guardianId,
            String oldPhone,
            String newPhone,
            String reason,
            Long requestedBy,
            Instant at) {
        GuardianPhoneChangeRequest request = new GuardianPhoneChangeRequest();
        request.userId = userId;
        request.childId = childId;
        request.guardianId = guardianId;
        request.oldPhone = oldPhone;
        request.newPhone = newPhone;
        request.status = PhoneChangeStatus.PENDING;
        request.reason = reason;
        request.requestedBy = requestedBy;
        request.requestedAt = at;
        return request;
    }

    public boolean isPending() {
        return status == PhoneChangeStatus.PENDING;
    }

    public void approve(Long userId, Instant at) {
        decide(PhoneChangeStatus.APPROVED, userId, null, at);
    }

    public void reject(Long userId, String note, Instant at) {
        decide(PhoneChangeStatus.REJECTED, userId, note, at);
    }

    /** Yêu cầu mới cho cùng tài khoản thay thế yêu cầu đang chờ (không có người quyết định). */
    public void cancel() {
        this.status = PhoneChangeStatus.CANCELLED;
    }

    public void recordSms(SmsStatus status) {
        this.smsStatus = status;
    }

    private void decide(PhoneChangeStatus result, Long userId, String note, Instant at) {
        this.status = result;
        this.decidedBy = userId;
        this.decidedAt = at;
        this.decisionNote = note;
    }

    @Override
    public String toString() {
        return "GuardianPhoneChangeRequest[id=" + id + ", status=" + status + "]";
    }
}
