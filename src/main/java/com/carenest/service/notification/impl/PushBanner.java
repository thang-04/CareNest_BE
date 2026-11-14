package com.carenest.service.notification.impl;

import com.carenest.entity.notification.Notification;
import com.carenest.utils.enums.NotificationType;
import java.util.EnumSet;
import java.util.Set;

/**
 * Chữ trên banner màn hình khóa. Loại nhạy cảm (sức khỏe) chỉ hiện câu chung; nội dung đầy đủ xem trong App (FR-006).
 */
record PushBanner(String title, String body) {

    static final Set<NotificationType> HEALTH_TYPES = EnumSet.of(
            NotificationType.CHILD_TB_CONTACT,
            NotificationType.CHILD_TB_CONTACT_DATE_CHANGED,
            NotificationType.HEALTH_RESULT_PUBLISHED,
            NotificationType.HEALTH_RESULT_UPDATED);

    static PushBanner of(Notification n) {
        return HEALTH_TYPES.contains(n.getType())
                ? new PushBanner("Thông tin sức khỏe", "Có thông tin sức khỏe mới. Mở ứng dụng để xem.")
                : new PushBanner(n.getTitle(), n.getBody());
    }
}
