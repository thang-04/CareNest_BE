package com.carenest.dto.announcement;

import com.carenest.utils.enums.ScheduleFrequency;
import java.time.LocalTime;

/**
 * Lịch gửi lặp (PAR-10): WEEKLY cần {@code dayOfWeek} 1–7 (Thứ Hai = 1); MONTHLY cần {@code dayOfMonth} 1–31 (tháng
 * thiếu ngày ⇒ ngày cuối tháng). {@code active} null = bật.
 */
public record AnnouncementScheduleRequest(
        Long templateId,
        RecipientTargetRequest target,
        ScheduleFrequency frequency,
        Integer dayOfWeek,
        Integer dayOfMonth,
        LocalTime sendTime,
        Boolean active) {}
