package com.carenest.service.notification.impl;

import com.carenest.entity.notification.DeviceToken;
import com.carenest.entity.notification.Notification;
import com.carenest.integration.push.PushGateway;
import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.service.notification.NotificationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy thông báo vừa lưu tới các thiết bị của người nhận, sau khi giao dịch tạo thông báo đã lưu. Lỗi gửi một thiết bị
 * chỉ ghi log: thông báo đã lưu, App vẫn thấy trong danh sách.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PushDispatchListener {
    private static final String LOG_PREFIX = "[PushDispatchListener]";

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushGateway pushGateway;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onNotificationsCreated(NotificationService.NotificationsCreatedEvent event) {
        String logPrefix = LOG_PREFIX + "|onNotificationsCreated|notifications="
                + event.notificationIds().size();
        log.info("{}|START", logPrefix);
        List<Notification> notifications = notificationRepository.findAllById(event.notificationIds());
        Map<Long, List<DeviceToken>> devices = deviceTokenRepository
                .findByUserIdIn(
                        notifications.stream().map(Notification::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(DeviceToken::getUserId));
        int sent = 0;
        int failed = 0;
        for (Notification n : notifications) {
            Map<String, String> data = new HashMap<>();
            data.put("type", n.getType().name());
            data.put("notificationId", String.valueOf(n.getId()));
            if (n.getChildId() != null) {
                data.put("childId", String.valueOf(n.getChildId()));
            }
            for (DeviceToken d : devices.getOrDefault(n.getUserId(), List.of())) {
                try {
                    pushGateway.send(new PushGateway.PushMessage(
                            d.getToken(), d.getPlatform(), n.getTitle(), n.getBody(), data));
                    sent++;
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn(
                            "{}|deviceId={}|ERROR|type={}",
                            logPrefix,
                            d.getId(),
                            ex.getClass().getSimpleName());
                }
            }
        }
        log.info("{}|sent={}|failed={}|END", logPrefix, sent, failed);
    }
}
