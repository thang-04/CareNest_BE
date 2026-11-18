package com.carenest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.support.NotificationTimingTestConfig;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTimingPropertiesTest {
    @Test
    void defaultsComeFromYamlAndLegacyPushKeysStillBind() {
        var p = NotificationTimingTestConfig.load();
        assertThat(p.push().workerDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(p.announcement().workerDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.announcement().scheduleDelay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(p.notificationReminders().attendanceStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(p.notificationReminders().attendanceCron()).isEqualTo("0 */5 8-23 * * MON-FRI");
        assertThat(NotificationTimingTestConfig.load(Map.of("carenest.push.max-age", "20m"))
                        .push()
                        .maxAge())
                .isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void customAttendanceCadenceAlsoGeneratesCron() {
        var p = NotificationTimingTestConfig.load(Map.of(
                "carenest.notification-reminders.attendance-start",
                "07:30",
                "carenest.notification-reminders.attendance-interval-minutes",
                10));
        assertThat(p.notificationReminders().attendanceCron()).isEqualTo("0 */10 7-23 * * MON-FRI");
    }

    @Test
    void invalidIntervalsDurationsCronAndBusinessLimitFailFast() {
        for (Map<String, Object> bad : java.util.List.<Map<String, Object>>of(
                Map.of("carenest.notification-reminders.attendance-interval-minutes", 0),
                Map.of("carenest.notification-reminders.attendance-interval-minutes", 7),
                Map.of("carenest.push.worker-delay", "0s"),
                Map.of("carenest.notification-reminders.daily-assessment-cron", "invalid"),
                Map.of("carenest.announcement.max-schedule-days", 31))) {
            assertThatThrownBy(() -> NotificationTimingTestConfig.load(bad)).isInstanceOf(RuntimeException.class);
        }
    }
}
