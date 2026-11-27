package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.entity.notification.NotificationPushDelivery;
import com.carenest.utils.enums.PushState;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PushDeliveryStateTest {
    @Test
    void successfulDeviceRemainsSentWhileAnotherDeviceRetries() {
        Instant now = Instant.parse("2026-11-16T02:00:00Z");
        NotificationPushDelivery first = NotificationPushDelivery.create(1L, 2L, now);
        NotificationPushDelivery second = NotificationPushDelivery.create(1L, 3L, now);
        first.sent(now);
        second.retry(now, now.plusSeconds(60));
        assertThat(first.getState()).isEqualTo(PushState.SENT);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getState()).isEqualTo(PushState.PENDING);
        assertThat(second.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
    }
}
