package com.carenest.repository.notification;

import com.carenest.entity.notification.NotificationPushDelivery;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface NotificationPushDeliveryRepository extends JpaRepository<NotificationPushDelivery, Long> {
    List<NotificationPushDelivery> findByNotificationIdOrderByIdAsc(Long notificationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT delivery FROM NotificationPushDelivery delivery WHERE delivery.id = :id")
    Optional<NotificationPushDelivery> lockById(@Param("id") Long id);
}
