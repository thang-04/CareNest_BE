package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.entity.notification.Notification;
import com.carenest.utils.enums.NotificationType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PushDispatchListenerTest {
    @Test
    void trendApprovedPushHidesOverviewWhilePickupKeepsItsMessage() {
        Notification trend = Notification.of(
                40L, NotificationType.HEALTH_TREND_APPROVED, "Tiêu đề", "Cân nặng giảm (giả)", 5L, Instant.now());
        Notification pickup = Notification.of(
                40L, NotificationType.CHILD_PICKED_UP, "Tiêu đề", "Bé đã được đón (giả)", 5L, Instant.now());
        assertThat(PushBanner.of(trend).title()).isEqualTo("Thông tin sức khỏe");
        assertThat(PushBanner.of(trend).body()).isEqualTo("Có thông tin sức khỏe mới. Mở ứng dụng để xem.");
        assertThat(PushBanner.of(pickup).body()).isEqualTo("Bé đã được đón (giả)");
    }
}
