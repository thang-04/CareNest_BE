package com.carenest.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tạo cron điểm danh từ đúng giờ bắt đầu/chu kỳ cấu hình; không lưu thêm một cron trùng giá trị trong Java. */
@Component
@RequiredArgsConstructor
public class NotificationSchedules {
    private final NotificationTimingProperties timing;

    public String attendanceCron() {
        return timing.notificationReminders().attendanceCron();
    }
}
