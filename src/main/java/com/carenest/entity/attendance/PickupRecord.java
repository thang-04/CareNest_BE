package com.carenest.entity.attendance;

import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.PickupOutcome;
import com.carenest.utils.enums.PickupVerification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một lần GV xác minh người đón (PCK-02, PCK-04, ATT-10): khớp ảnh phụ huynh hoặc phụ huynh xác nhận qua điện thoại thì
 * trả trẻ; lần không trả được vẫn lưu. Trả thành công tối đa một lần/trẻ/ngày; không sửa, không hủy.
 */
@Getter
@Entity
@Table(name = "pickup_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false, updatable = false)
    private Long campusId;

    @Column(nullable = false, updatable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PickupOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PickupVerification verification;

    /** Phụ huynh khớp ảnh (vị trí trên hồ sơ trẻ); họ tên, quan hệ, SĐT chép từ hồ sơ lúc trả. */
    @Column(name = "guardian_position", updatable = false)
    private Short guardianPosition;

    @Column(name = "pickup_person_name", nullable = false, updatable = false, length = 150)
    private String pickupPersonName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_person_relationship", updatable = false, length = 30)
    private GuardianRelationship pickupPersonRelationship;

    @Column(name = "pickup_person_phone", nullable = false, updatable = false, length = 10)
    private String pickupPersonPhone;

    @Column(name = "handed_over_at", updatable = false)
    private Instant handedOverAt;

    @Column(nullable = false, updatable = false, length = 500)
    private String note;

    @Column(name = "notified_parents", nullable = false)
    private short notifiedParents;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Builder
    private PickupRecord(
            Long childId,
            Long classroomId,
            Long campusId,
            LocalDate date,
            PickupVerification verification,
            Integer guardianPosition,
            String pickupPersonName,
            GuardianRelationship pickupPersonRelationship,
            String pickupPersonPhone,
            Instant handedOverAt,
            String note,
            Long recordedBy,
            Instant recordedAt) {
        this.childId = childId;
        this.classroomId = classroomId;
        this.campusId = campusId;
        this.date = date;
        this.verification = verification;
        this.outcome = verification.outcome();
        this.guardianPosition = guardianPosition == null ? null : guardianPosition.shortValue();
        this.pickupPersonName = pickupPersonName == null ? "" : pickupPersonName;
        this.pickupPersonRelationship = pickupPersonRelationship;
        this.pickupPersonPhone = pickupPersonPhone == null ? "" : pickupPersonPhone;
        this.handedOverAt = handedOverAt;
        this.note = note == null ? "" : note;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public boolean isHandedOver() {
        return outcome == PickupOutcome.HANDED_OVER;
    }

    /** Số tài khoản phụ huynh đã được báo trẻ được đón. */
    public void markNotified(int parents) {
        this.notifiedParents = (short) parents;
    }
}
