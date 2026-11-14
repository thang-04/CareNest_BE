package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.entity.notification.Notification;
import com.carenest.entity.notification.NotificationRule;
import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.repository.notification.NotificationRuleRepository;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PushState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Thông báo theo quy tắc (PAR-09): loại đã tắt không tạo, câu chữ HT sửa được dùng; mọi dòng mới chờ đẩy. */
class NotificationServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");

    private NotificationRepository notifications;
    private NotificationRuleRepository rules;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        rules = mock(NotificationRuleRepository.class);
        service = new NotificationServiceImpl(
                notifications, mock(DeviceTokenRepository.class), rules, Clock.fixed(NOW, ZoneOffset.UTC));
        given(notifications.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private NotificationRule rule(NotificationType type, boolean enabled, String title) {
        NotificationRule r = NotificationRule.of(type);
        r.update(enabled, title, null, 1L, NOW);
        given(rules.findById(type)).willReturn(Optional.of(r));
        return r;
    }

    @Test
    void disabledOptionalTypeCreatesNothing() {
        rule(NotificationType.MENU_PUBLISHED, false, null);

        service.notifyEvent(List.of(5L), NotificationType.MENU_PUBLISHED, Map.of("tuan", "16/11 – 20/11"), null);

        verify(notifications, never()).save(any());
    }

    @Test
    void mandatoryTypeIgnoresDisabledFlagAndUsesCustomWording() {
        rule(NotificationType.CHILD_PICKED_UP, false, "Đã đón {tenTre}");

        service.notifyEvent(List.of(5L, 5L), NotificationType.CHILD_PICKED_UP, Map.of("tenTre", "Bé An"), 9L);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Đã đón Bé An");
        assertThat(saved.getValue().getPushState()).isEqualTo(PushState.PENDING);
    }

    @Test
    void defaultWordingWhenNotCustomized() {
        given(rules.findById(NotificationType.MENU_PUBLISHED)).willReturn(Optional.empty());

        service.notifyEvent(List.of(5L), NotificationType.MENU_PUBLISHED, Map.of("tuan", "16/11 – 20/11"), null);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getBody()).contains("16/11 – 20/11");
    }
}
