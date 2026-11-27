package com.carenest.config;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

/**
 * Các giá trị thời gian của thông báo được khai báo một lần trong application.yml.
 * Class này chỉ bind và kiểm tra cấu hình; không chứa bản sao giá trị mặc định.
 * Giờ khóa điểm danh và giờ gửi do người dùng chọn vẫn lấy từ DB, không lấy ở đây.
 * Cấu hình được đọc khi khởi động; đổi YAML/biến môi trường cần khởi động lại BE.
 */
@ConfigurationProperties("carenest")
public record NotificationTimingProperties(
        Push push, Announcement announcement, NotificationReminders notificationReminders) {
    public NotificationTimingProperties {
        Objects.requireNonNull(push, "Thiếu cấu hình carenest.push");
        Objects.requireNonNull(announcement, "Thiếu cấu hình carenest.announcement");
        Objects.requireNonNull(notificationReminders, "Thiếu cấu hình carenest.notification-reminders");
    }

    public record Push(
            Duration workerDelay,
            Duration maxAge,
            Duration deviceMaxIdle,
            Duration retryInitialDelay,
            Duration retryMaxDelay,
            String deviceCleanupCron) {
        public Push {
            positive(workerDelay, "push.worker-delay");
            positive(maxAge, "push.max-age");
            positive(deviceMaxIdle, "push.device-max-idle");
            positive(retryInitialDelay, "push.retry-initial-delay");
            positive(retryMaxDelay, "push.retry-max-delay");
            if (retryMaxDelay.compareTo(retryInitialDelay) < 0)
                throw new IllegalArgumentException("retry-max-delay phải >= retry-initial-delay");
            cron(deviceCleanupCron, "push.device-cleanup-cron");
        }
    }

    public record Announcement(Duration workerDelay, Duration scheduleDelay, int maxScheduleDays) {
        public Announcement {
            positive(workerDelay, "announcement.worker-delay");
            positive(scheduleDelay, "announcement.schedule-delay");
            // PAR-08 cho phép hẹn tối đa 30 ngày; không để cấu hình vượt giới hạn nghiệp vụ.
            if (maxScheduleDays < 1 || maxScheduleDays > 30)
                throw new IllegalArgumentException("max-schedule-days phải từ 1 đến 30");
        }
    }

    public record NotificationReminders(
            LocalTime attendanceStart,
            int attendanceIntervalMinutes,
            String attendanceDays,
            String dailyAssessmentCron,
            String evaluationDeadlineCron,
            String unlockEndingCron,
            Duration unlockEndingLead,
            String leaderOverdueCron,
            String mealBookCron,
            Duration mealBookAfterCutoff) {
        public NotificationReminders {
            Objects.requireNonNull(attendanceStart, "Thiếu attendance-start");
            // Cron chạy theo phút trong giờ; 60 phải chia hết cho chu kỳ để lịch quét/slot chống trùng khớp nhau.
            if (attendanceIntervalMinutes < 1
                    || attendanceIntervalMinutes > 60
                    || 60 % attendanceIntervalMinutes != 0) {
                throw new IllegalArgumentException("attendance-interval-minutes phải là ước số dương của 60");
            }
            cron(attendanceCron(attendanceStart, attendanceIntervalMinutes, attendanceDays), "attendance");
            cron(dailyAssessmentCron, "daily-assessment-cron");
            cron(evaluationDeadlineCron, "evaluation-deadline-cron");
            cron(unlockEndingCron, "unlock-ending-cron");
            cron(leaderOverdueCron, "leader-overdue-cron");
            cron(mealBookCron, "meal-book-cron");
            positive(unlockEndingLead, "unlock-ending-lead");
            positive(mealBookAfterCutoff, "meal-book-after-cutoff");
        }

        public String attendanceCron() {
            return attendanceCron(attendanceStart, attendanceIntervalMinutes, attendanceDays);
        }

        private static String attendanceCron(LocalTime start, int minutes, String days) {
            return "0 */" + minutes + " " + start.getHour() + "-23 * * " + days;
        }
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero() || value.toMillis() < 1) {
            throw new IllegalArgumentException(name + " phải >= 1ms");
        }
    }

    private static void cron(String value, String name) {
        if (value == null || !CronExpression.isValidExpression(value))
            throw new IllegalArgumentException("Cron không hợp lệ: " + name);
    }
}
