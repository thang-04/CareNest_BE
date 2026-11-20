package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carenest.integration.email.EmailGateway;
import com.carenest.integration.email.EmailMessage;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.enums.NotificationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** Hàng chờ email KHGD trên PostgreSQL thật (V158): rollback không phát sinh email, commit gửi đúng một lần. Cần Docker. */
@SpringBootTest(properties = {"carenest.email.outbox.delay=1h", "carenest.email.web-base-url=https://web.test"})
@Testcontainers(disabledWithoutDocker = true)
class EmailOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @MockitoBean
    private EmailGateway emailGateway;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailOutboxWorker worker;

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
        String key = "email-" + System.nanoTime();
        userId = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, email, full_name)"
                        + " VALUES (?, ?, 'gv@example.test', 'Cô Demo') RETURNING id",
                Long.class,
                key,
                key);
    }

    private void notifySubmitted() {
        notificationService.notifyUsers(
                List.of(userId),
                NotificationType.EDU_SUBMITTED,
                "Chờ bạn duyệt: Kế hoạch tổ Demo",
                "Cô A gửi duyệt",
                "/education/goals/1",
                false,
                "Năm học Demo");
    }

    private int queued() {
        return jdbc.queryForObject("SELECT count(*) FROM notification_email_deliveries", Integer.class);
    }

    @Test
    void rolledBackActionQueuesNoEmail() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                    notifySubmitted();
                    throw new IllegalStateException("nghiệp vụ lỗi");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(queued()).isZero();
    }

    @Test
    void committedActionSendsOneEmailOnce() {
        tx.executeWithoutResult(s -> notifySubmitted());
        assertThat(queued()).isEqualTo(1);

        worker.sendDue();
        worker.sendDue();

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, times(1)).send(sent.capture());
        assertThat(sent.getValue().to()).containsExactly("gv@example.test");
        assertThat(sent.getValue().html()).contains("https://web.test/education/goals/1");
        assertThat(jdbc.queryForObject("SELECT state FROM notification_email_deliveries", String.class))
                .isEqualTo("SENT");
    }

    @Test
    void resubmitInNewRoundSendsNewEmail() {
        tx.executeWithoutResult(s -> notifySubmitted());
        worker.sendDue();
        tx.executeWithoutResult(s -> notifySubmitted());
        worker.sendDue();

        verify(emailGateway, times(2)).send(any());
    }
}
