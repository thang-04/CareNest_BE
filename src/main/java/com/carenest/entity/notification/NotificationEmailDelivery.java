package com.carenest.entity.notification;

import com.carenest.utils.enums.PushState;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Hàng chờ email của một thông báo (G94-386): tạo cùng giao dịch với thông báo, worker gửi sau commit. */
@Entity
@Table(name = "notification_email_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEmailDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private Long notificationId;

    // Phạm vi hồ sơ (năm học, nhóm tuổi, lớp) hiện trong email; thông báo trong ứng dụng không cần
    @Column(updatable = false, length = 300)
    private String scope;

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

    public static NotificationEmailDelivery create(Long notificationId, String scope, Instant now) {
        NotificationEmailDelivery delivery = new NotificationEmailDelivery();
        delivery.notificationId = notificationId;
        delivery.scope = scope;
        delivery.state = PushState.PENDING;
        delivery.createdAt = now;
        return delivery;
    }

    public void sent(Instant now) {
        attempts++;
        lastAttemptAt = now;
        finish(PushState.SENT, null, now);
    }

    /** Lỗi gửi: còn lượt thì giữ PENDING tới {@code next}, hết lượt thì FAILED. */
    public void failed(String kind, Instant now, Instant next) {
        attempts++;
        lastAttemptAt = now;
        if (next == null) {
            finish(PushState.FAILED, kind, now);
        } else {
            lastErrorKind = kind;
            nextAttemptAt = next;
        }
    }

    /** Không gửi (vd. người nhận không có email); không tính là một lần thử. */
    public void skipped(String kind, Instant now) {
        finish(PushState.SKIPPED, kind, now);
    }

    private void finish(PushState terminal, String kind, Instant now) {
        state = terminal;
        lastErrorKind = kind;
        completedAt = now;
        nextAttemptAt = null;
    }
}
