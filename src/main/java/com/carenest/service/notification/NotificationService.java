package com.carenest.service.notification;

import com.carenest.dto.notification.DeviceTokenRequest;
import com.carenest.dto.notification.NotificationListResponse;
import com.carenest.dto.notification.NotificationResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.NotificationType;
import java.util.Collection;
import java.util.List;

/**
 * Thông báo cho người dùng (phụ huynh trên App): lưu để App hiện danh sách, đánh dấu đã đọc; đẩy tới thiết bị đã đăng ký
 * sau khi giao dịch tạo thông báo lưu xong. Module khác gọi {@link #notifyUsers} trong giao dịch của mình.
 */
public interface NotificationService {

    /** Thông báo vừa lưu, để đẩy tới thiết bị sau khi giao dịch tạo chúng đã lưu. */
    record NotificationsCreatedEvent(List<Long> notificationIds) {}

    /** Tạo thông báo cho các người dùng (bỏ trùng); chạy trong giao dịch của người gọi. */
    void notifyUsers(Collection<Long> userIds, NotificationType type, String title, String body, Long childId);

    /**
     * Thông báo có đường dẫn Web (kế hoạch giáo dục, spec 003). {@code once}: bỏ người đã có thông báo cùng loại + đường
     * dẫn (nhắc "Gần đến hạn" một lần).
     */
    void notifyUsers(
            Collection<Long> userIds, NotificationType type, String title, String body, String link, boolean once);

    NotificationListResponse listMyNotifications(CurrentUser currentUser, int limit);

    NotificationResponse markRead(CurrentUser currentUser, Long notificationId);

    NotificationListResponse markAllRead(CurrentUser currentUser);

    void registerDevice(CurrentUser currentUser, DeviceTokenRequest request);

    void unregisterDevice(CurrentUser currentUser, DeviceTokenRequest request);
}
