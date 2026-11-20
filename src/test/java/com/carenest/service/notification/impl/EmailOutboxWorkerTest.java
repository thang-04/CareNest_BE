package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.entity.notification.Notification;
import com.carenest.entity.notification.NotificationEmailDelivery;
import com.carenest.integration.email.EmailDeliveryException;
import com.carenest.integration.email.EmailGateway;
import com.carenest.integration.email.EmailMessage;
import com.carenest.repository.notification.NotificationEmailDeliveryRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PushState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Hàng chờ email KHGD (G94-386): gửi riêng từng người, thiếu email bỏ qua, lỗi SMTP thử lại rồi FAILED. */
class EmailOutboxWorkerTest {
    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    private NotificationEmailDeliveryRepository deliveries;
    private NotificationRepository notifications;
    private EmailGateway gateway;
    private EmailOutboxWorker worker;

    @BeforeEach
    void setUp() {
        deliveries = mock(NotificationEmailDeliveryRepository.class);
        notifications = mock(NotificationRepository.class);
        gateway = mock(EmailGateway.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        given(txManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        worker = new EmailOutboxWorker(
                deliveries,
                notifications,
                gateway,
                txManager,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")),
                "https://web.carenest.test/",
                true,
                50,
                2,
                Duration.ofMinutes(1),
                Duration.ofHours(1));
    }

    private NotificationEmailDelivery queued(long id, long notificationId, long userId) {
        Notification n = Notification.of(
                userId,
                NotificationType.EDU_RETURNED,
                "Bị trả lại: Hồ sơ chủ đề Gia đình · Lớp Mầm 1",
                "Cô B trả lại. Lý do: thiếu tuần 2",
                null,
                NOW,
                "/education/class/3/themes/7");
        ReflectionTestUtils.setField(n, "id", notificationId);
        given(notifications.findById(notificationId)).willReturn(Optional.of(n));
        NotificationEmailDelivery d = NotificationEmailDelivery.create(notificationId, "Năm học 2026-2027", NOW);
        ReflectionTestUtils.setField(d, "id", id);
        given(deliveries.lockForSend(id, PushState.PENDING)).willReturn(Optional.of(d));
        return d;
    }

    private void recipient(long userId, String email, String fullName) {
        given(deliveries.findRecipient(userId))
                .willReturn(Optional.of(new NotificationEmailDeliveryRepository.Recipient() {
                    @Override
                    public String getEmail() {
                        return email;
                    }

                    @Override
                    public String getFullName() {
                        return fullName;
                    }
                }));
    }

    @Test
    void sendsOneEmailToTheRecipientOnlyWithLinkToDocument() {
        NotificationEmailDelivery d = queued(1L, 11L, 5L);
        recipient(5L, "gv@example.test", "Cô A");

        worker.sendOne(1L);

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(gateway).send(sent.capture());
        assertThat(sent.getValue().to()).containsExactly("gv@example.test");
        assertThat(sent.getValue().subject()).isEqualTo("[CareNest] Bị trả lại: Hồ sơ chủ đề Gia đình · Lớp Mầm 1");
        assertThat(sent.getValue().html())
                .contains("https://web.carenest.test/education/class/3/themes/7")
                .contains("Mở hồ sơ")
                .contains("https://web.carenest.test/email/logo-mark.png")
                .contains("thiếu tuần 2")
                .contains("Năm học 2026-2027");
        assertThat(d.getState()).isEqualTo(PushState.SENT);
    }

    @Test
    void recipientWithoutEmailIsSkippedWithoutSending() {
        NotificationEmailDelivery d = queued(1L, 11L, 5L);
        given(deliveries.findRecipient(5L)).willReturn(Optional.empty());

        worker.sendOne(1L);

        verify(gateway, never()).send(any());
        assertThat(d.getState()).isEqualTo(PushState.SKIPPED);
        assertThat(d.getLastErrorKind()).isEqualTo(EmailOutboxWorker.NO_EMAIL);
    }

    @Test
    void smtpFailureRetriesThenFailsAfterMaxAttempts() {
        NotificationEmailDelivery d = queued(1L, 11L, 5L);
        recipient(5L, "gv@example.test", null);
        willThrow(new EmailDeliveryException("down")).given(gateway).send(any());

        worker.sendOne(1L);
        assertThat(d.getState()).isEqualTo(PushState.PENDING);
        assertThat(d.getNextAttemptAt()).isAfter(NOW);

        worker.sendOne(1L);
        assertThat(d.getState()).isEqualTo(PushState.FAILED);
        assertThat(d.getLastErrorKind()).isEqualTo(EmailOutboxWorker.SEND_FAILED);
        assertThat(d.getAttempts()).isEqualTo(2);
    }

    @Test
    void oneRecipientFailingDoesNotBlockOthers() {
        given(deliveries.findDueIds(NOW, 50)).willReturn(List.of(1L, 2L));
        queued(1L, 11L, 5L);
        NotificationEmailDelivery second = queued(2L, 12L, 6L);
        given(deliveries.findRecipient(5L)).willThrow(new IllegalStateException("db"));
        recipient(6L, "ht@example.test", null);

        worker.sendDue();

        assertThat(second.getState()).isEqualTo(PushState.SENT);
    }
}
