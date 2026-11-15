package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carenest.entity.notification.DeviceToken;
import com.carenest.entity.notification.Notification;
import com.carenest.integration.push.PushGateway;
import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.enums.DevicePlatform;
import com.carenest.utils.enums.NotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PushDispatchListenerTest {
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final DeviceTokenRepository deviceTokenRepository = mock(DeviceTokenRepository.class);
    private final PushGateway pushGateway = mock(PushGateway.class);
    private final PushDispatchListener listener =
            new PushDispatchListener(notificationRepository, deviceTokenRepository, pushGateway);

    @Test
    @DisplayName(
            "PAR-07, HLT-21: push nhận định xu hướng đã duyệt che nội dung trên màn khóa; loại khác giữ nguyên câu")
    void trendApprovedPushHidesOverview() {
        Notification trend = notification(1L, NotificationType.HEALTH_TREND_APPROVED, "Cân nặng giảm 0,5 kg (giả).");
        Notification pickup = notification(2L, NotificationType.CHILD_PICKED_UP, "Bé đã được đón (giả).");
        given(notificationRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(trend, pickup));
        given(deviceTokenRepository.findByUserIdIn(any()))
                .willReturn(List.of(DeviceToken.of(40L, "token-gia", DevicePlatform.ANDROID, Instant.now())));

        listener.onNotificationsCreated(new NotificationService.NotificationsCreatedEvent(List.of(1L, 2L)));

        ArgumentCaptor<PushGateway.PushMessage> sent = ArgumentCaptor.forClass(PushGateway.PushMessage.class);
        verify(pushGateway, times(2)).send(sent.capture());
        PushGateway.PushMessage trendPush = sent.getAllValues().get(0);
        assertThat(trendPush.title()).isEqualTo("Thông tin sức khỏe");
        assertThat(trendPush.body()).isEqualTo("Có thông tin sức khỏe mới. Mở ứng dụng để xem.");
        assertThat(trendPush.data())
                .containsEntry("type", "HEALTH_TREND_APPROVED")
                .containsEntry("childId", "5");
        assertThat(sent.getAllValues().get(1).body()).isEqualTo("Bé đã được đón (giả).");
    }

    private static Notification notification(Long id, NotificationType type, String body) {
        Notification n = Notification.of(40L, type, "Tiêu đề", body, 5L, Instant.now());
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }
}
