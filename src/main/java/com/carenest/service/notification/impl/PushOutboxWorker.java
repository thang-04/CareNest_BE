package com.carenest.service.notification.impl;

import com.carenest.entity.notification.DeviceToken;
import com.carenest.entity.notification.Notification;
import com.carenest.integration.push.PushException;
import com.carenest.integration.push.PushGateway;
import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.utils.enums.PushState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Worker hàng chờ đẩy (spec 019, research R1–R2): lấy thông báo PENDING đến hạn, mỗi dòng một giao dịch ngắn có khóa
 * SKIP LOCKED (nhiều máy chủ chạy cùng lúc không đẩy trùng), đẩy tới mọi thiết bị của người nhận rồi ghi trạng thái.
 */
@Slf4j
@Component
public class PushOutboxWorker {
    private static final String LOG_PREFIX = "[PushOutboxWorker]";
    private static final Duration FIRST_RETRY = Duration.ofMinutes(1);
    private static final Duration MAX_RETRY = Duration.ofMinutes(30);

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushGateway pushGateway;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final Duration maxAge;
    private final Duration deviceMaxIdle;

    public PushOutboxWorker(
            NotificationRepository notificationRepository,
            DeviceTokenRepository deviceTokenRepository,
            PushGateway pushGateway,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${carenest.push.batch-size:100}") int batchSize,
            @Value("${carenest.push.max-age:60m}") Duration maxAge,
            @Value("${carenest.push.device-max-idle:270d}") Duration deviceMaxIdle) {
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushGateway = pushGateway;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAge = maxAge;
        this.deviceMaxIdle = deviceMaxIdle;
    }

    @Scheduled(
            fixedDelayString = "${carenest.push.worker-delay:5s}",
            initialDelayString = "${carenest.push.worker-delay:5s}")
    public void pushDue() {
        List<Long> ids = notificationRepository.findDuePushIds(clock.instant(), batchSize);
        if (ids.isEmpty()) {
            return;
        }
        String logPrefix = LOG_PREFIX + "|pushDue|due=" + ids.size();
        log.info("{}|START", logPrefix);
        Map<PushState, Integer> outcome = new HashMap<>();
        for (Long id : ids) {
            try {
                PushState state = tx.execute(status -> pushOne(id));
                if (state != null) {
                    outcome.merge(state, 1, Integer::sum);
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "{}|notificationId={}|ERROR|type={}",
                        logPrefix,
                        id,
                        ex.getClass().getSimpleName());
            }
        }
        log.info("{}|outcome={}|END", logPrefix, outcome);
    }

    /** Dọn thiết bị không cập nhật quá 270 ngày (FR-005, khuyến nghị FCM). */
    @Scheduled(cron = "0 10 3 * * *", zone = "${carenest.time-zone}")
    public void removeStaleDevices() {
        String logPrefix = LOG_PREFIX + "|removeStaleDevices";
        log.info("{}|START", logPrefix);
        Long removed = tx.execute(status ->
                deviceTokenRepository.deleteByUpdatedAtBefore(clock.instant().minus(deviceMaxIdle)));
        log.info("{}|removed={}|END", logPrefix, removed);
    }

    /** Khóa và đẩy một thông báo; null nếu máy khác đang xử lý hoặc đã xử lý xong. Trả trạng thái sau lượt này. */
    PushState pushOne(Long id) {
        Notification n =
                notificationRepository.lockForPush(id, PushState.PENDING).orElse(null);
        if (n == null) {
            return null;
        }
        List<DeviceToken> devices = deviceTokenRepository.findByUserId(n.getUserId());
        if (devices.isEmpty()) {
            n.finishPush(PushState.SKIPPED);
            return PushState.SKIPPED;
        }
        PushBanner banner = PushBanner.of(n);
        Map<String, String> data = new HashMap<>();
        data.put("type", n.getType().name());
        data.put("notificationId", String.valueOf(n.getId()));
        if (n.getChildId() != null) {
            data.put("childId", String.valueOf(n.getChildId()));
        }
        int delivered = 0;
        Duration retryAfter = null;
        boolean retryable = false;
        for (DeviceToken d : devices) {
            try {
                pushGateway.send(new PushGateway.PushMessage(
                        d.getToken(), d.getPlatform(), banner.title(), banner.body(), data));
                delivered++;
            } catch (PushException ex) {
                log.warn(
                        "{}|pushOne|notificationId={}|deviceId={}|kind={}|ERROR|type={}",
                        LOG_PREFIX,
                        id,
                        d.getId(),
                        ex.kind(),
                        ex.getMessage());
                switch (ex.kind()) {
                    case INVALID_TOKEN -> deviceTokenRepository.delete(d);
                    case RETRYABLE -> {
                        retryable = true;
                        retryAfter = max(retryAfter, ex.retryAfter());
                    }
                    case PERMANENT -> {
                        // iOS thiếu khóa APNs hoặc lỗi không thử lại được: giữ thiết bị, bỏ lượt này
                    }
                }
            }
        }
        // Đã có thiết bị nhận ⇒ xong, không thử lại để thiết bị đã nhận không bị đẩy trùng (research R2)
        if (delivered > 0) {
            n.finishPush(PushState.SENT);
            return PushState.SENT;
        }
        Instant now = clock.instant();
        Instant next = now.plus(max(backoff(n.getPushAttempts() + 1), retryAfter));
        if (!retryable || next.isAfter(n.getCreatedAt().plus(maxAge))) {
            n.finishPush(PushState.FAILED);
            return PushState.FAILED;
        }
        n.retryPushAt(next);
        return PushState.PENDING;
    }

    /** 1 phút × 2^(lần-1), tối đa 30 phút, ±20% ngẫu nhiên để các máy không thử lại cùng lúc. */
    static Duration backoff(int attempt) {
        long base = FIRST_RETRY.toMillis() << Math.min(attempt - 1, 10);
        long capped = Math.min(base, MAX_RETRY.toMillis());
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Duration.ofMillis((long) (capped * jitter));
    }

    private static Duration max(Duration a, Duration b) {
        if (a == null) {
            return b;
        }
        return b == null || a.compareTo(b) >= 0 ? a : b;
    }
}
