package com.carenest.entity.attendance;

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

/** Một lần sửa giờ trả trẻ (PCK-04): giờ cũ, giờ mới, lý do, người sửa. */
@Getter
@Entity
@Table(name = "pickup_time_changes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickupTimeChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pickup_record_id", nullable = false, updatable = false)
    private Long pickupRecordId;

    @Column(name = "old_handed_over_at", nullable = false, updatable = false)
    private Instant oldHandedOverAt;

    @Column(name = "new_handed_over_at", nullable = false, updatable = false)
    private Instant newHandedOverAt;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static PickupTimeChange of(
            Long pickupRecordId, Instant oldTime, Instant newTime, String reason, Long changedBy, Instant at) {
        PickupTimeChange change = new PickupTimeChange();
        change.pickupRecordId = pickupRecordId;
        change.oldHandedOverAt = oldTime;
        change.newHandedOverAt = newTime;
        change.reason = reason;
        change.changedBy = changedBy;
        change.changedAt = at;
        return change;
    }
}
