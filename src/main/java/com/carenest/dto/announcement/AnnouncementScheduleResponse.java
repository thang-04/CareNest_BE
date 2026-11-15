package com.carenest.dto.announcement;

import com.carenest.utils.enums.ScheduleFrequency;
import java.time.Instant;
import java.time.LocalTime;

/** Lịch gửi lặp; {@code nextRunAt}: lần gửi kế tiếp (ngày nghỉ sẽ bị bỏ khi tới lượt). */
public record AnnouncementScheduleResponse(
        Long id,
        Long templateId,
        String templateName,
        RecipientTargetRequest target,
        ScheduleFrequency frequency,
        Integer dayOfWeek,
        Integer dayOfMonth,
        LocalTime sendTime,
        boolean active,
        Instant nextRunAt) {}
