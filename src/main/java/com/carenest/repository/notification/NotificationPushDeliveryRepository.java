package com.carenest.repository.notification;

import com.carenest.entity.notification.NotificationPushDelivery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPushDeliveryRepository extends JpaRepository<NotificationPushDelivery, Long> {
    List<NotificationPushDelivery> findByNotificationIdOrderByIdAsc(Long notificationId);
}
