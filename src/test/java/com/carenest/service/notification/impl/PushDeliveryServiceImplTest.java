package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carenest.entity.notification.DeviceToken;
import com.carenest.entity.notification.Notification;
import com.carenest.entity.notification.NotificationPushDelivery;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PushDeliveryServiceImplTest {
    @Test
    void afterRestartOnlyPendingDeviceIsSent() {
        Instant now = Instant.parse("2026-11-16T03:00:00Z");
        NotificationRepository notifications = mock(NotificationRepository.class);
        DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
        NotificationPushDeliveryRepository deliveries = mock(NotificationPushDeliveryRepository.class);
        PushGateway gateway = mock(PushGateway.class);
        Notification parent = Notification.of(5L, NotificationType.CHILD_PICKED_UP, "Tin", "Nội dung", null, now);
        ReflectionTestUtils.setField(parent, "id", 1L);
        parent.initializePush(now);
        NotificationPushDelivery delivered = NotificationPushDelivery.create(1L, 10L, now);
        delivered.sent(now);
        NotificationPushDelivery pending = NotificationPushDelivery.create(1L, 11L, now);
        ReflectionTestUtils.setField(pending, "id", 2L);
        DeviceToken device = DeviceToken.of(5L, "fake-b", DevicePlatform.ANDROID, now);
        given(notifications.lockForPush(1L, PushState.PENDING)).willReturn(Optional.of(parent));
        given(deliveries.findByNotificationIdOrderByIdAsc(1L)).willReturn(List.of(delivered, pending));
        given(deliveries.lockById(2L)).willReturn(Optional.of(pending));
        given(devices.lockById(11L)).willReturn(Optional.of(device));
        var service = new PushDeliveryServiceImpl(
                notifications, devices, deliveries, gateway, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(60));
        assertThat(service.pushOne(1L)).isEqualTo(PushState.SENT);
        ArgumentCaptor<PushGateway.PushMessage> message = ArgumentCaptor.forClass(PushGateway.PushMessage.class);
        verify(gateway, times(1)).send(message.capture());
        assertThat(message.getValue().token()).isEqualTo("fake-b");
        assertThat(delivered.getAttempts()).isEqualTo(1);
        assertThat(pending.getAttempts()).isEqualTo(1);
        verify(devices, org.mockito.Mockito.never()).findByUserId(any());
    }
}
