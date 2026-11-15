package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carenest.integration.push.PushException;
import com.carenest.integration.push.PushGateway;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PushState;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Hàng chờ đẩy trên PostgreSQL thật: V130, worker đẩy rồi ghi SENT, SKIP LOCKED chặn hai máy đẩy trùng. Cần Docker. */
@SpringBootTest(properties = "carenest.push.worker-delay=1h")
@Testcontainers(disabledWithoutDocker = true)
class PushOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @MockitoBean
    private PushGateway pushGateway;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushOutboxWorker worker;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long userId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM device_tokens");
        String key = "push-" + System.nanoTime();
        userId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, 'Demo') RETURNING id",
                Long.class,
                key,
                key);
    }

    private Long createNotification() {
        tx.executeWithoutResult(s -> notificationService.notifyUsers(
                List.of(userId), NotificationType.CHILD_WITHDRAWN, "Tiêu đề", "Nội dung", (Long) null));
        return jdbc.queryForObject("SELECT max(id) FROM notifications WHERE user_id = ?", Long.class, userId);
    }

    @Test
    void newNotificationIsPushedOnceThenMarkedSent() {
        jdbc.update(
                "INSERT INTO device_tokens (user_id, token, platform, created_at, updated_at)"
                        + " VALUES (?, 'tok-1', 'ANDROID', now(), now())",
                userId);
        Long id = createNotification();
        assertThat(notificationRepository.findById(id).orElseThrow().getPushState())
                .isEqualTo(PushState.PENDING);

        worker.pushDue();
        worker.pushDue();

        verify(pushGateway, times(1)).send(any());
        assertThat(notificationRepository.findById(id).orElseThrow().getPushState())
                .isEqualTo(PushState.SENT);
    }

    @Test
    void userWithoutDeviceIsSkipped() {
        Long id = createNotification();

        worker.pushDue();

        assertThat(notificationRepository.findById(id).orElseThrow().getPushState())
                .isEqualTo(PushState.SKIPPED);
    }

    @Test
    void commitsSuccessfulDeviceBeforeRetryingOnlyFailedDevice() {
        jdbc.update(
                "INSERT INTO device_tokens (user_id, token, platform, created_at, updated_at) VALUES (?, 'tok-a', 'ANDROID', now(), now()), (?, 'tok-b', 'ANDROID', now(), now())",
                userId,
                userId);
        Long id = createNotification();
        var attempts = new java.util.concurrent.ConcurrentHashMap<String, Integer>();
        doAnswer(call -> {
                    PushGateway.PushMessage message = call.getArgument(0);
                    int count = attempts.merge(message.token(), 1, Integer::sum);
                    if (message.token().equals("tok-b") && count == 1) {
                        throw new PushException(PushException.Kind.RETRYABLE, "temporary");
                    }
                    return null;
                })
                .when(pushGateway)
                .send(any());

        tx.execute(s -> worker.pushOne(id));
        tx.execute(s -> worker.pushOne(id));
        assertThat(attempts).containsEntry("tok-a", 1).containsEntry("tok-b", 1);
        assertThat(jdbc.queryForObject("SELECT push_state FROM notifications WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
        jdbc.update(
                "UPDATE notification_push_deliveries SET next_attempt_at = now() - interval '1 second' WHERE notification_id = ? AND state = 'PENDING'",
                id);
        tx.execute(s -> worker.pushOne(id));

        assertThat(attempts).containsEntry("tok-a", 1).containsEntry("tok-b", 2);
        assertThat(jdbc.queryForObject("SELECT push_state FROM notifications WHERE id = ?", String.class, id))
                .isEqualTo("SENT");
    }

    @Test
    void lockedDeviceIsNotSentOrCountedAsMissing() throws Exception {
        Long deviceId = jdbc.queryForObject(
                "INSERT INTO device_tokens (user_id, token, platform, created_at, updated_at) VALUES (?, 'tok-locked', 'ANDROID', now(), now()) RETURNING id",
                Long.class,
                userId);
        Long id = createNotification();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holding = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT id FROM device_tokens WHERE id = ? FOR UPDATE", Long.class, deviceId);
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            CompletableFuture<PushState> result =
                    CompletableFuture.supplyAsync(() -> tx.execute(s -> worker.pushOne(id)));
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(PushState.PENDING);
            assertThat(jdbc.queryForObject(
                            "SELECT attempts FROM notification_push_deliveries WHERE notification_id = ?",
                            Integer.class,
                            id))
                    .isZero();
            verify(pushGateway, times(0)).send(any());
        } finally {
            release.countDown();
            holding.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void ledgerIsUniqueKeepsHistoryOnDeviceDeletionAndCascadesWithNotification() {
        Long deviceId = jdbc.queryForObject(
                "INSERT INTO device_tokens (user_id, token, platform, created_at, updated_at) VALUES (?, 'tok-history', 'ANDROID', now(), now()) RETURNING id",
                Long.class,
                userId);
        Long id = createNotification();
        jdbc.update(
                "INSERT INTO notification_push_deliveries (notification_id, device_id, created_at) VALUES (?, ?, now())",
                id,
                deviceId);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO notification_push_deliveries (notification_id, device_id, created_at) VALUES (?, ?, now())",
                        id,
                        deviceId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM device_tokens WHERE id = ?", deviceId);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification_push_deliveries WHERE notification_id = ?",
                        Integer.class,
                        id))
                .isEqualTo(1);
        jdbc.update("DELETE FROM notifications WHERE id = ?", id);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification_push_deliveries WHERE notification_id = ?",
                        Integer.class,
                        id))
                .isZero();
    }

    @Test
    void cursorReachesBeyondBatchAndDeliversTwoDevicesWithinThirtySeconds() {
        String prefix = "bench-" + userId + "-";
        jdbc.update(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) SELECT ? || i, ? || i, 'Demo' FROM generate_series(1, 301) i",
                prefix,
                prefix);
        jdbc.update(
                "INSERT INTO device_tokens (user_id, token, platform, created_at, updated_at) SELECT id, username || suffix, 'ANDROID', now(), now() FROM user_accounts CROSS JOIN (VALUES ('-a'), ('-b')) AS devices(suffix) WHERE username LIKE ?",
                prefix + "%");
        jdbc.update(
                "INSERT INTO notifications (user_id, type, title, body, created_at) SELECT id, 'CHILD_WITHDRAWN', 'Tin', 'Nội dung', now() FROM user_accounts WHERE username LIKE ?",
                prefix + "%");
        doAnswer(call -> {
                    Thread.sleep(50);
                    return null;
                })
                .when(pushGateway)
                .send(any());
        org.springframework.test.util.ReflectionTestUtils.setField(worker, "cursor", 0L);
        org.springframework.test.util.ReflectionTestUtils.setField(worker, "ceiling", 0L);
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) worker.pushDue();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE push_state = 'SENT'", Integer.class))
                .isEqualTo(301);
        verify(pushGateway, times(602)).send(any());
        assertThat(elapsedMs).isLessThan(30_000);
    }

    @Test
    void expiredParentClosesEveryPendingDeliveryEvenWhenNextRetryIsInFuture() {
        Long id = createNotification();
        jdbc.update(
                "UPDATE notifications SET created_at = now() - interval '61 minutes', push_initialized_at = now() - interval '1 hour', push_next_at = now() + interval '1 hour' WHERE id = ?",
                id);
        jdbc.update(
                "INSERT INTO notification_push_deliveries (notification_id, device_id, created_at, next_attempt_at) VALUES (?, 1001, now(), now() + interval '1 hour'), (?, 1002, now(), now() + interval '1 hour')",
                id,
                id);
        org.springframework.test.util.ReflectionTestUtils.setField(worker, "cursor", 0L);
        org.springframework.test.util.ReflectionTestUtils.setField(worker, "ceiling", 0L);
        worker.pushDue();
        assertThat(jdbc.queryForObject("SELECT push_state FROM notifications WHERE id = ?", String.class, id))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification_push_deliveries WHERE notification_id = ? AND state = 'FAILED' AND last_error_kind = 'EXPIRED'",
                        Integer.class,
                        id))
                .isEqualTo(2);
        verify(pushGateway, times(0)).send(any());
    }

    @Test
    void rowLockedByOneServerIsSkippedByAnother() throws Exception {
        Long id = createNotification();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            assertThat(notificationRepository.lockForPush(id, PushState.PENDING))
                    .isPresent();
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Optional<?> second = tx.execute(s -> notificationRepository.lockForPush(id, PushState.PENDING));

        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertThat(second).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
