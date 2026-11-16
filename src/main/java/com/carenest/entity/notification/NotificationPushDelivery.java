package com.carenest.entity.notification;

import com.carenest.utils.enums.PushState;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_push_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPushDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private Long notificationId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PushState state;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error_kind", length = 32)
    private String lastErrorKind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static NotificationPushDelivery create(Long notificationId, Long deviceId, Instant now) {
        NotificationPushDelivery delivery = new NotificationPushDelivery();
        delivery.notificationId = notificationId;
        delivery.deviceId = deviceId;
        delivery.createdAt = now;
        delivery.state = PushState.PENDING;
        return delivery;
    }

    public void sent(Instant now) {
        attempts++;
        lastAttemptAt = now;
        finish(PushState.SENT, null, now);
    }

    public void retry(Instant now, Instant next) {
        attempts++;
        lastAttemptAt = now;
        lastErrorKind = "RETRYABLE";
        nextAttemptAt = next;
    }

    public void failedAttempt(String kind, Instant now) {
        attempts++;
        lastAttemptAt = now;
        finish(PushState.FAILED, kind, now);
    }

    public void finish(PushState terminal, String kind, Instant now) {
        state = terminal;
        lastErrorKind = kind;
        completedAt = now;
        nextAttemptAt = null;
    }
}
