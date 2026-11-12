package com.carenest.dto.notification;

import com.carenest.utils.enums.NotificationType;
import java.time.Instant;

/** Một thông báo; {@code childId}: trẻ liên quan (mở đúng màn hình), {@code readAt} null khi chưa đọc. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Long childId,
        Instant createdAt,
        Instant readAt,
        // Web mở thẳng hồ sơ kế hoạch giáo dục (spec 003)
        String link) {}
