package com.carenest.service.notification.impl;

import com.carenest.entity.notification.Notification;
import com.carenest.entity.notification.NotificationEmailDelivery;
import com.carenest.integration.email.EmailDeliveryException;
import com.carenest.integration.email.EmailGateway;
import com.carenest.integration.email.EmailMessage;
import com.carenest.repository.notification.NotificationEmailDeliveryRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.utils.enums.PushState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gửi email trong hàng chờ {@code notification_email_deliveries} sau khi giao dịch tạo đã lưu (G94-386). Mỗi dòng một
 * người nhận, một giao dịch: lỗi một người không chặn người khác, lỗi SMTP không đụng tới nghiệp vụ đã lưu. Không log
 * địa chỉ, tiêu đề, nội dung email.
 */
@Slf4j
@Component
public class EmailOutboxWorker {
    static final String NO_EMAIL = "NO_EMAIL";
    static final String NO_NOTIFICATION = "NO_NOTIFICATION";
    static final String SEND_FAILED = "SEND_FAILED";
    static final String INVALID_MESSAGE = "INVALID_MESSAGE";

    private final NotificationEmailDeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final EmailGateway emailGateway;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String webBaseUrl;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration maxRetryDelay;

    public EmailOutboxWorker(
            NotificationEmailDeliveryRepository deliveries,
            NotificationRepository notifications,
            EmailGateway emailGateway,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${carenest.email.web-base-url:}") String webBaseUrl,
            @Value("${carenest.email.outbox.enabled:true}") boolean enabled,
            @Value("${carenest.email.outbox.batch-size:50}") int batchSize,
            @Value("${carenest.email.outbox.max-attempts:5}") int maxAttempts,
            @Value("${carenest.email.outbox.retry-delay:1m}") Duration retryDelay,
            @Value("${carenest.email.outbox.max-retry-delay:1h}") Duration maxRetryDelay) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.emailGateway = emailGateway;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.webBaseUrl = webBaseUrl;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelay = retryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    @Scheduled(
            fixedDelayString = "${carenest.email.outbox.delay:30s}",
            initialDelayString = "${carenest.email.outbox.delay:30s}")
    public synchronized void sendDue() {
        if (!enabled) return;
        List<Long> ids = deliveries.findDueIds(clock.instant(), batchSize);
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(status -> sendOne(id));
            } catch (RuntimeException ex) {
                log.warn(
                        "[EmailOutboxWorker]|sendDue|deliveryId={}|type={}",
                        id,
                        ex.getClass().getSimpleName());
            }
        }
    }

    void sendOne(Long id) {
        NotificationEmailDelivery delivery =
                deliveries.lockForSend(id, PushState.PENDING).orElse(null);
        if (delivery == null) return;
        Instant now = clock.instant();
        Notification n = notifications.findById(delivery.getNotificationId()).orElse(null);
        if (n == null) {
            delivery.skipped(NO_NOTIFICATION, now);
            return;
        }
        NotificationEmailDeliveryRepository.Recipient recipient =
                deliveries.findRecipient(n.getUserId()).orElse(null);
        if (recipient == null) {
            delivery.skipped(NO_EMAIL, now);
            log.info("[EmailOutboxWorker]|sendOne|deliveryId={}|SKIPPED|kind={}", id, NO_EMAIL);
            return;
        }
        try {
            EmailMessage message = EduEmailComposer.compose(
                    n,
                    delivery.getScope(),
                    recipient.getEmail().trim(),
                    recipient.getFullName(),
                    webBaseUrl,
                    clock.getZone());
            emailGateway.send(message);
            delivery.sent(now);
            log.info("[EmailOutboxWorker]|sendOne|deliveryId={}|SENT", id);
        } catch (EmailDeliveryException ex) {
            int attempt = delivery.getAttempts() + 1;
            Instant next = attempt >= maxAttempts
                    ? null
                    : now.plus(PushDeliveryServiceImpl.backoff(attempt, retryDelay, maxRetryDelay));
            delivery.failed(SEND_FAILED, now, next);
            log.warn("[EmailOutboxWorker]|sendOne|deliveryId={}|attempts={}|FAILED", id, attempt);
        } catch (IllegalArgumentException ex) {
            // Địa chỉ/nội dung không hợp lệ: thử lại cũng không qua
            delivery.failed(INVALID_MESSAGE, now, null);
            log.warn("[EmailOutboxWorker]|sendOne|deliveryId={}|FAILED|kind={}", id, INVALID_MESSAGE);
        }
    }
}
