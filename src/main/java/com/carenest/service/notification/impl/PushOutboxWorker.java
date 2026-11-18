package com.carenest.service.notification.impl;

import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.service.notification.PushDeliveryService;
import com.carenest.utils.enums.PushState;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class PushOutboxWorker {
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final PushDeliveryService deliveryService;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final Duration maxAge;
    private final Duration deviceMaxIdle;
    private final boolean enabled;
    private final ExecutorService executor;
    private long cursor;
    private long ceiling;

    public PushOutboxWorker(
            NotificationRepository notifications,
            DeviceTokenRepository devices,
            PushDeliveryService deliveryService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${carenest.push.batch-size:300}") int batchSize,
            @Value("${carenest.push.max-age}") Duration maxAge,
            @Value("${carenest.push.device-max-idle}") Duration deviceMaxIdle,
            @Value("${carenest.push.worker-enabled:true}") boolean enabled,
            @Value("${carenest.push.worker-threads:4}") int threads) {
        this.notifications = notifications;
        this.devices = devices;
        this.deliveryService = deliveryService;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAge = maxAge;
        this.deviceMaxIdle = deviceMaxIdle;
        this.enabled = enabled;
        this.executor = Executors.newFixedThreadPool(Math.max(1, Math.min(4, threads)));
    }

    @Scheduled(fixedDelayString = "${carenest.push.worker-delay}", initialDelayString = "${carenest.push.worker-delay}")
    public synchronized void pushDue() {
        if (!enabled) return;
        Instant now = clock.instant();
        if (ceiling == 0) ceiling = notifications.findPushCeiling();
        List<Long> ids = notifications.findDuePushIdsAfter(now, now.minus(maxAge), cursor, ceiling, batchSize);
        if (ids.isEmpty() && cursor != 0) {
            cursor = 0;
            ceiling = notifications.findPushCeiling();
            ids = notifications.findDuePushIdsAfter(now, now.minus(maxAge), cursor, ceiling, batchSize);
        }
        if (ids.isEmpty()) {
            cursor = 0;
            ceiling = 0;
            return;
        }
        cursor = ids.getLast();
        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        for (Long id : ids) {
            jobs.add(CompletableFuture.runAsync(
                    () -> {
                        try {
                            tx.execute(status -> pushOne(id));
                        } catch (RuntimeException ex) {
                            log.warn(
                                    "[PushOutboxWorker]|pushDue|notificationId={}|type={}",
                                    id,
                                    ex.getClass().getSimpleName());
                        }
                    },
                    executor));
        }
        CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).join();
    }

    @Scheduled(cron = "${carenest.push.device-cleanup-cron}", zone = "${carenest.time-zone}")
    public void removeStaleDevices() {
        tx.execute(status -> devices.deleteByUpdatedAtBefore(clock.instant().minus(deviceMaxIdle)));
    }

    @PreDestroy
    void close() {
        executor.shutdown();
    }

    PushState pushOne(Long id) {
        return deliveryService.pushOne(id);
    }
}
