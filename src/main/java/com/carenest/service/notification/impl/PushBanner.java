package com.carenest.service.notification.impl;

import com.carenest.entity.notification.Notification;
import com.carenest.utils.enums.NotificationType;
import java.util.EnumSet;
import java.util.Set;

/**
 * Chữ trên banner màn hình khóa. Loại nhạy cảm (sức khỏe, kết quả công bố PAR-03) chỉ hiện câu chung; nội dung đầy đủ xem
 * trong App (FR-006, clarify Q5).
 */
record PushBanner(String title, String body) {

    static final Set<NotificationType> HEALTH_TYPES = EnumSet.of(
            NotificationType.CHILD_TB_CONTACT,
            NotificationType.CHILD_TB_CONTACT_DATE_CHANGED,
            NotificationType.HEALTH_RESULT_PUBLISHED,
            NotificationType.HEALTH_RESULT_UPDATED,
            NotificationType.DAILY_HEALTH_NOTED);

    static final Set<NotificationType> RESULT_TYPES =
            EnumSet.of(NotificationType.CHILD_EVALUATION_CONFIRMED, NotificationType.THEME_EVALUATION_CONFIRMED);

    static PushBanner of(Notification n) {
        if (HEALTH_TYPES.contains(n.getType())) {
            return new PushBanner("Thông tin sức khỏe", "Có thông tin sức khỏe mới. Mở ứng dụng để xem.");
        }
        if (RESULT_TYPES.contains(n.getType())) {
            return new PushBanner("Kết quả mới của bé", "Có kết quả đánh giá mới. Mở ứng dụng để xem.");
        }
        return new PushBanner(n.getTitle(), n.getBody());
    }
}
