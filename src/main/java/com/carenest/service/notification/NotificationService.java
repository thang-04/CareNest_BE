package com.carenest.service.notification;

import com.carenest.dto.notification.DeviceTokenRequest;
import com.carenest.dto.notification.NotificationListResponse;
import com.carenest.dto.notification.NotificationResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.NotificationType;
import java.util.Collection;

/**
 * Thông báo cho người dùng: lưu để App/Web hiện danh sách, đánh dấu đã đọc. Mỗi dòng lưu ở trạng thái chờ đẩy; worker
 * hàng chờ đẩy tới thiết bị sau khi giao dịch tạo đã lưu (spec 019). Module khác gọi {@link #notifyUsers} trong giao dịch
 * của mình.
 */
public interface NotificationService {

    /** Tạo thông báo cho các người dùng (bỏ trùng); chạy trong giao dịch của người gọi. */
    void notifyUsers(Collection<Long> userIds, NotificationType type, String title, String body, Long childId);

    /**
     * Thông báo có đường dẫn Web (kế hoạch giáo dục, spec 003). {@code once}: bỏ người đã có thông báo cùng loại + đường
     * dẫn (nhắc "Gần đến hạn" một lần).
     */
    void notifyUsers(
            Collection<Long> userIds, NotificationType type, String title, String body, String link, boolean once);

    /**
     * Thông báo của trường cho từng người nhận; bỏ người đã có dòng của thông báo này (chạy lại sau khi máy chết an toàn).
     * Trả số người nhận.
     */
    int notifyAnnouncement(Long announcementId, Collection<Long> userIds, String title, String body);

    /** Người dùng có nhận thông báo trường này không (quyền đọc nội dung, tải tệp — FR-016). */
    boolean isAnnouncementRecipient(Long userId, Long announcementId);

    NotificationListResponse listMyNotifications(CurrentUser currentUser, int limit);

    /** Một thông báo của chính người dùng (mở từ banner); của người khác ⇒ NOTIFICATION_NOT_FOUND. */
    NotificationResponse getMyNotification(CurrentUser currentUser, Long notificationId);

    NotificationResponse markRead(CurrentUser currentUser, Long notificationId);

    NotificationListResponse markAllRead(CurrentUser currentUser);

    void registerDevice(CurrentUser currentUser, DeviceTokenRequest request);

    void unregisterDevice(CurrentUser currentUser, DeviceTokenRequest request);
}
