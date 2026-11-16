package com.carenest.service.notification.impl;

import com.carenest.entity.notification.DeviceToken;
import com.carenest.entity.notification.Notification;
import com.carenest.entity.notification.NotificationPushDelivery;
import com.carenest.integration.push.PushException;
import com.carenest.integration.push.PushGateway;
import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationPushDeliveryRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.service.notification.PushDeliveryService;
import com.carenest.utils.enums.PushState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeliveryServiceImpl implements PushDeliveryService {
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final NotificationPushDeliveryRepository deliveries;
    private final PushGateway gateway;
    private final Clock clock;
    private final Duration maxAge;

    public PushDeliveryServiceImpl(
            NotificationRepository notifications,
            DeviceTokenRepository devices,
            NotificationPushDeliveryRepository deliveries,
            PushGateway gateway,
            Clock clock,
            @Value("${carenest.push.max-age:60m}") Duration maxAge) {
        this.notifications = notifications;
        this.devices = devices;
        this.deliveries = deliveries;
        this.gateway = gateway;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PushState pushOne(Long id) {
        Notification n = notifications.lockForPush(id, PushState.PENDING).orElse(null);
        if (n == null) return null;
        Instant now = clock.instant();
        Instant deadline = n.getCreatedAt().plus(maxAge);
        List<NotificationPushDelivery> rows = deliveries.findByNotificationIdOrderByIdAsc(id);
        if (!now.isBefore(deadline)) {
            Instant expiredAt = now;
            rows.stream()
                    .filter(row -> row.getState() == PushState.PENDING)
                    .forEach(row -> row.finish(PushState.FAILED, "EXPIRED", expiredAt));
            if (rows.isEmpty()) n.updatePush(PushState.FAILED, null, 0);
            else aggregate(n, rows, deadline);
            return n.getPushState();
        }
        if (n.getPushInitializedAt() == null) {
            rows = new ArrayList<>();
            for (DeviceToken device : devices.findByUserId(n.getUserId())) {
                rows.add(deliveries.save(NotificationPushDelivery.create(id, device.getId(), now)));
            }
            n.initializePush(now);
            if (rows.isEmpty()) {
                n.updatePush(PushState.SKIPPED, null, 0);
                return PushState.SKIPPED;
            }
        }
        Instant dueAt = now;
        NotificationPushDelivery row = rows.stream()
                .filter(d -> d.getState() == PushState.PENDING)
                .filter(d ->
                        d.getNextAttemptAt() == null || !d.getNextAttemptAt().isAfter(dueAt))
                .findFirst()
                .orElse(null);
        if (row != null) {
            row = deliveries.lockById(row.getId()).orElse(null);
        }
        if (row != null) {
            DeviceToken device = devices.lockById(row.getDeviceId()).orElse(null);
            now = clock.instant();
            if (!now.isBefore(deadline)) {
                Instant expiredAt = now;
                rows.stream()
                        .filter(delivery -> delivery.getState() == PushState.PENDING)
                        .forEach(delivery -> delivery.finish(PushState.FAILED, "EXPIRED", expiredAt));
            } else if (device == null && !devices.existsById(row.getDeviceId()))
                row.finish(PushState.SKIPPED, "DEVICE_MISSING", now);
            else if (device == null) {
                /* A locked device will be retried on a later tick. */
            } else if (!device.getUserId().equals(n.getUserId())) row.finish(PushState.SKIPPED, "OWNER_CHANGED", now);
            else send(n, row, device, now, deadline);
        }
        aggregate(n, rows, deadline);
        return n.getPushState();
    }

    private void send(Notification n, NotificationPushDelivery row, DeviceToken device, Instant now, Instant deadline) {
        PushBanner banner = PushBanner.of(n);
        Map<String, String> data = new HashMap<>();
        data.put("type", n.getType().name());
        data.put("notificationId", String.valueOf(n.getId()));
        if (n.getChildId() != null) data.put("childId", String.valueOf(n.getChildId()));
        try {
            gateway.send(new PushGateway.PushMessage(
                    device.getToken(), device.getPlatform(), banner.title(), banner.body(), data));
            row.sent(now);
        } catch (PushException ex) {
            switch (ex.kind()) {
                case INVALID_TOKEN -> {
                    devices.delete(device);
                    row.failedAttempt("INVALID_TOKEN", now);
                }
                case PERMANENT -> row.failedAttempt("PERMANENT", now);
                case RETRYABLE -> {
                    Duration wait = backoff(row.getAttempts() + 1);
                    if (ex.retryAfter() != null && ex.retryAfter().compareTo(wait) > 0) wait = ex.retryAfter();
                    Instant next = now.plus(wait);
                    if (!next.isBefore(deadline)) row.failedAttempt("EXPIRED", now);
                    else row.retry(now, next);
                }
            }
        }
    }

    private void aggregate(Notification n, List<NotificationPushDelivery> rows, Instant deadline) {
        int attempts = (int) Math.min(
                Short.MAX_VALUE,
                rows.stream().mapToLong(NotificationPushDelivery::getAttempts).sum());
        List<NotificationPushDelivery> pending =
                rows.stream().filter(d -> d.getState() == PushState.PENDING).toList();
        if (!pending.isEmpty()) {
            Instant next = pending.stream().anyMatch(d -> d.getNextAttemptAt() == null)
                    ? null
                    : pending.stream()
                            .map(NotificationPushDelivery::getNextAttemptAt)
                            .min(Instant::compareTo)
                            .orElse(deadline);
            n.updatePush(PushState.PENDING, next, attempts);
        } else if (rows.stream().anyMatch(d -> d.getState() == PushState.SENT))
            n.updatePush(PushState.SENT, null, attempts);
        else if (rows.stream().anyMatch(d -> d.getState() == PushState.FAILED))
            n.updatePush(PushState.FAILED, null, attempts);
        else n.updatePush(PushState.SKIPPED, null, attempts);
    }

    static Duration backoff(int attempt) {
        long base = Duration.ofMinutes(1).toMillis() << Math.min(attempt - 1, 10);
        long capped = Math.min(base, Duration.ofMinutes(30).toMillis());
        return Duration.ofMillis(
                (long) (capped * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4)));
    }
}
