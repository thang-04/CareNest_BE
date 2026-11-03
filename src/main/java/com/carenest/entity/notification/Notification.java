package com.carenest.entity.notification;

import com.carenest.utils.enums.NotificationType;
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

/** Một thông báo của một người dùng; App hiện danh sách, đánh dấu đã đọc. */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = 150)
    private String title;

    @Column(nullable = false, updatable = false, length = 500)
    private String body;

    @Column(name = "child_id", updatable = false)
    private Long childId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    public static Notification of(
            Long userId, NotificationType type, String title, String body, Long childId, Instant at) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.childId = childId;
        n.createdAt = at;
        return n;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }
}
