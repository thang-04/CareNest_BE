package com.carenest.service.announcement.impl;

import com.carenest.utils.enums.ScheduleFrequency;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/** Lần chạy kế tiếp của lịch lặp (PAR-10); tháng không có ngày đã chọn (29–31) ⇒ ngày cuối tháng (clarify Q3). */
final class AnnouncementScheduleCalculator {

    private AnnouncementScheduleCalculator() {}

    /** Lần chạy đầu tiên sau {@code after} (không tính đúng thời điểm {@code after}). */
    static ZonedDateTime nextRun(
            ScheduleFrequency frequency,
            Integer dayOfWeek,
            Integer dayOfMonth,
            LocalTime sendTime,
            ZonedDateTime after) {
        ZoneId zone = after.getZone();
        LocalDate day = after.toLocalDate();
        if (frequency == ScheduleFrequency.WEEKLY) {
            LocalDate candidate = day.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
            ZonedDateTime at = ZonedDateTime.of(LocalDateTime.of(candidate, sendTime), zone);
            return at.isAfter(after) ? at : at.plusWeeks(1);
        }
        YearMonth month = YearMonth.from(day);
        for (int i = 0; i < 2; i++) {
            YearMonth m = month.plusMonths(i);
            LocalDate candidate = m.atDay(Math.min(dayOfMonth, m.lengthOfMonth()));
            ZonedDateTime at = ZonedDateTime.of(LocalDateTime.of(candidate, sendTime), zone);
            if (at.isAfter(after)) {
                return at;
            }
        }
        YearMonth m = month.plusMonths(2);
        return ZonedDateTime.of(LocalDateTime.of(m.atDay(Math.min(dayOfMonth, m.lengthOfMonth())), sendTime), zone);
    }
}
