package com.carenest.entity.notification;

import com.carenest.utils.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Phần HT đã chỉnh của một quy tắc thông báo tự động (PAR-09); câu chữ null = mặc định trong danh mục. */
@Getter
@Entity
@Table(name = "notification_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRule {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private NotificationType type;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "title_template", length = 150)
    private String titleTemplate;

    @Column(name = "body_template", length = 500)
    private String bodyTemplate;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationRule of(NotificationType type) {
        NotificationRule r = new NotificationRule();
        r.type = type;
        r.enabled = true;
        return r;
    }

    public void update(boolean enabled, String titleTemplate, String bodyTemplate, Long userId, Instant at) {
        this.enabled = enabled;
        this.titleTemplate = titleTemplate;
        this.bodyTemplate = bodyTemplate;
        this.updatedBy = userId;
        this.updatedAt = at;
    }
}
