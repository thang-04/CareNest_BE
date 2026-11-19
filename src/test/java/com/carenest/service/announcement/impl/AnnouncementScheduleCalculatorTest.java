package com.carenest.service.announcement.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.ScheduleFrequency;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** Lần chạy kế tiếp của lịch lặp (PAR-10, clarify Q3). */
class AnnouncementScheduleCalculatorTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime SEVEN = LocalTime.of(7, 0);

    private static ZonedDateTime at(int y, int m, int d, int h) {
        return ZonedDateTime.of(LocalDateTime.of(y, m, d, h, 0), VN);
    }

    @Test
    void weeklyPicksNextMatchingWeekday() {
        // 16/11/2026 là Thứ Hai
        assertThat(AnnouncementScheduleCalculator.nextRun(
                        ScheduleFrequency.WEEKLY, 1, null, SEVEN, at(2026, 11, 16, 6)))
                .isEqualTo(at(2026, 11, 16, 7));
        assertThat(AnnouncementScheduleCalculator.nextRun(
                        ScheduleFrequency.WEEKLY, 1, null, SEVEN, at(2026, 11, 16, 7)))
                .isEqualTo(at(2026, 11, 23, 7));
        assertThat(AnnouncementScheduleCalculator.nextRun(
                        ScheduleFrequency.WEEKLY, 5, null, SEVEN, at(2026, 11, 16, 9)))
                .isEqualTo(at(2026, 11, 20, 7));
    }

    @Test
    void monthlyDay31FallsBackToLastDayOfShortMonth() {
        assertThat(AnnouncementScheduleCalculator.nextRun(
                        ScheduleFrequency.MONTHLY, null, 31, SEVEN, at(2027, 4, 1, 0)))
                .isEqualTo(at(2027, 4, 30, 7));
        assertThat(AnnouncementScheduleCalculator.nextRun(
                        ScheduleFrequency.MONTHLY, null, 31, SEVEN, at(2027, 2, 1, 0)))
                .isEqualTo(at(2027, 2, 28, 7));
        assertThat(AnnouncementScheduleCalculator.nextRun(
                        ScheduleFrequency.MONTHLY, null, 31, SEVEN, at(2027, 4, 30, 8)))
                .isEqualTo(at(2027, 5, 31, 7));
    }

    @Test
    void monthlyAfterDayMovesToNextMonth() {
        assertThat(AnnouncementScheduleCalculator.nextRun(
                        ScheduleFrequency.MONTHLY, null, 5, SEVEN, at(2026, 12, 10, 0)))
                .isEqualTo(at(2027, 1, 5, 7));
    }
}
