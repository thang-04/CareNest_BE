package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carenest.entity.notification.DeviceToken;
import com.carenest.entity.notification.Notification;
import com.carenest.entity.notification.NotificationPushDelivery;
import com.carenest.integration.push.PushException;
import com.carenest.integration.push.PushGateway;
import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationPushDeliveryRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.utils.enums.DevicePlatform;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PushState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class PushOutboxWorkerTest {
    private static final Instant NOW = Instant.parse("2026-11-15T03:00:00Z");

    private NotificationRepository notifications;
    private DeviceTokenRepository devices;
    private PushGateway gateway;
    private PushOutboxWorker worker;
    private NotificationPushDeliveryRepository deliveries;
    private List<NotificationPushDelivery> rows;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        devices = mock(DeviceTokenRepository.class);
        gateway = mock(PushGateway.class);
        deliveries = mock(NotificationPushDeliveryRepository.class);
        rows = new ArrayList<>();
        given(deliveries.findByNotificationIdOrderByIdAsc(any())).willAnswer(invocation -> new ArrayList<>(rows));
        given(deliveries.save(any())).willAnswer(invocation -> {
            NotificationPushDelivery row = invocation.getArgument(0);
            rows.add(row);
            return row;
        });
        worker = new PushOutboxWorker(
                notifications,
                devices,
                new PushDeliveryServiceImpl(
                        notifications,
                        devices,
                        deliveries,
                        gateway,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofMinutes(60)),
                mock(PlatformTransactionManager.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                100,
                Duration.ofMinutes(60),
                Duration.ofDays(270),
                true,
                1);
    }

    private Notification pending(NotificationType type, Instant createdAt) {
        Notification n = Notification.of(5L, type, "Bé An đã được đón", "Bé An đã được đón lúc 16:30", 9L, createdAt);
        ReflectionTestUtils.setField(n, "id", 1L);
        given(notifications.lockForPush(1L, PushState.PENDING)).willReturn(Optional.of(n));
        return n;
    }

    private List<DeviceToken> devicesOfUser(int count) {
        List<DeviceToken> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DeviceToken d = DeviceToken.of(5L, "token-" + i, DevicePlatform.ANDROID, NOW);
            ReflectionTestUtils.setField(d, "id", 100L + i);
            given(devices.lockById(100L + i)).willReturn(Optional.of(d));
            list.add(d);
        }
        given(devices.findByUserId(5L)).willReturn(list);
        return list;
    }

    @Test
    void pushesEveryDeviceThenMarksSent() {
        Notification n = pending(NotificationType.CHILD_PICKED_UP, NOW);
        devicesOfUser(2);

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.PENDING);
        assertThat(worker.pushOne(1L)).isEqualTo(PushState.SENT);

        ArgumentCaptor<PushGateway.PushMessage> sent = ArgumentCaptor.forClass(PushGateway.PushMessage.class);
        verify(gateway, times(2)).send(sent.capture());
        assertThat(sent.getValue().data())
                .containsEntry("notificationId", "1")
                .containsEntry("type", "CHILD_PICKED_UP")
                .containsEntry("childId", "9");
        assertThat(sent.getValue().title()).isEqualTo("Bé An đã được đón");
        assertThat(n.getPushState()).isEqualTo(PushState.SENT);
        assertThat(n.getPushAttempts()).isEqualTo((short) 2);
    }

    @Test
    void userWithoutDeviceIsSkippedWithoutError() {
        Notification n = pending(NotificationType.CHILD_PICKED_UP, NOW);
        devicesOfUser(0);

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.SKIPPED);
        verify(gateway, never()).send(any());
        assertThat(n.getPushState()).isEqualTo(PushState.SKIPPED);
    }

    @Test
    void lockedDeviceRemainsPendingWithoutAttempt() {
        Notification n = pending(NotificationType.CHILD_PICKED_UP, NOW);
        devicesOfUser(1);
        given(devices.lockById(100L)).willReturn(Optional.empty());
        given(devices.existsById(100L)).willReturn(true);

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.PENDING);
        assertThat(n.getPushAttempts()).isZero();
        verify(gateway, never()).send(any());
    }

    @Test
    void deviceChangedOwnerAfterSnapshotIsNotSent() {
        pending(NotificationType.CHILD_PICKED_UP, NOW);
        DeviceToken device = devicesOfUser(1).getFirst();
        device.assignTo(6L, DevicePlatform.ANDROID, NOW);
        assertThat(worker.pushOne(1L)).isEqualTo(PushState.SKIPPED);
        assertThat(rows.getFirst().getLastErrorKind()).isEqualTo("OWNER_CHANGED");
        verify(gateway, never()).send(any());
    }

    @Test
    void temporaryFailureKeepsPendingWithBackoff() {
        Notification n = pending(NotificationType.CHILD_PICKED_UP, NOW);
        devicesOfUser(1);
        willThrow(new PushException(PushException.Kind.RETRYABLE, "FCM 503"))
                .given(gateway)
                .send(any());

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.PENDING);

        assertThat(n.getPushState()).isEqualTo(PushState.PENDING);
        assertThat(n.getPushAttempts()).isEqualTo((short) 1);
        assertThat(n.getPushNextAt()).isBetween(NOW.plusSeconds(48), NOW.plusSeconds(72));
    }

    @Test
    void retryAfterFromServiceIsRespected() {
        Notification n = pending(NotificationType.CHILD_PICKED_UP, NOW);
        devicesOfUser(1);
        willThrow(new PushException(PushException.Kind.RETRYABLE, "FCM 429", Duration.ofMinutes(10)))
                .given(gateway)
                .send(any());

        worker.pushOne(1L);

        assertThat(n.getPushNextAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void stopsAfterSixtyMinutesButKeepsNotification() {
        Notification n = pending(
                NotificationType.CHILD_PICKED_UP,
                NOW.minus(Duration.ofMinutes(60)).plusSeconds(10));
        devicesOfUser(1);
        willThrow(new PushException(PushException.Kind.RETRYABLE, "FCM 503"))
                .given(gateway)
                .send(any());

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.FAILED);
        assertThat(n.getPushState()).isEqualTo(PushState.FAILED);
        verify(notifications, never()).delete(any());
    }

    @Test
    void invalidTokenRemovesDeviceWithoutRetry() {
        Notification n = pending(NotificationType.CHILD_PICKED_UP, NOW);
        List<DeviceToken> list = devicesOfUser(1);
        willThrow(new PushException(PushException.Kind.INVALID_TOKEN, "FCM 404 UNREGISTERED"))
                .given(gateway)
                .send(any());

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.FAILED);
        verify(devices).delete(list.getFirst());
        assertThat(n.getPushNextAt()).isNull();
    }

    @Test
    void missingApnsKeyKeepsDevice() {
        pending(NotificationType.CHILD_PICKED_UP, NOW);
        devicesOfUser(1);
        willThrow(new PushException(PushException.Kind.PERMANENT, "FCM 401 THIRD_PARTY_AUTH_ERROR"))
                .given(gateway)
                .send(any());

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.FAILED);
        verify(devices, never()).delete(any());
    }

    @Test
    void oneBrokenDeviceDoesNotBlockOthers() {
        Notification n = pending(NotificationType.CHILD_PICKED_UP, NOW);
        devicesOfUser(2);
        willThrow(new PushException(PushException.Kind.RETRYABLE, "FCM 503"))
                .willDoNothing()
                .given(gateway)
                .send(any());

        assertThat(worker.pushOne(1L)).isEqualTo(PushState.PENDING);
        assertThat(worker.pushOne(1L)).isEqualTo(PushState.PENDING);
        verify(gateway, times(2)).send(any());
        assertThat(n.getPushState()).isEqualTo(PushState.PENDING);
    }

    @Test
    void healthBannerShowsOnlyGenericText() {
        pending(NotificationType.HEALTH_RESULT_PUBLISHED, NOW);
        devicesOfUser(1);

        worker.pushOne(1L);

        ArgumentCaptor<PushGateway.PushMessage> sent = ArgumentCaptor.forClass(PushGateway.PushMessage.class);
        verify(gateway).send(sent.capture());
        assertThat(sent.getValue().title()).doesNotContain("An");
        assertThat(sent.getValue().body()).doesNotContain("An").contains("Mở ứng dụng");
    }

    @Test
    void rowTakenByAnotherServerIsLeftAlone() {
        given(notifications.lockForPush(1L, PushState.PENDING)).willReturn(Optional.empty());

        assertThat(worker.pushOne(1L)).isNull();
        verify(gateway, never()).send(any());
    }

    @Test
    void backoffDoublesAndCapsAtThirtyMinutes() {
        assertThat(PushOutboxWorker.backoff(1)).isBetween(Duration.ofSeconds(48), Duration.ofSeconds(72));
        assertThat(PushOutboxWorker.backoff(3)).isBetween(Duration.ofSeconds(192), Duration.ofSeconds(288));
        assertThat(PushOutboxWorker.backoff(20)).isLessThanOrEqualTo(Duration.ofMinutes(36));
    }
}
