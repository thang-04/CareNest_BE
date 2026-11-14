package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
