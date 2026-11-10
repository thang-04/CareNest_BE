package com.carenest.dto.eduplan;

import java.time.LocalDate;
import java.util.List;

/**
 * Kế hoạch ngày của giáo án tuần đã duyệt (EDU-16, EDU-19): các dòng hoạt động có MT trong ngày, để đánh giá trẻ chấm theo
 * MT. Chỉ đọc; module khác tham chiếu giáo án bằng classDocId và MT bằng id dòng mục tiêu.
 */
public record ApprovedDayLessonResponse(
        Long classDocId, LocalDate weekStart, LocalDate date, String weekLabel, List<Activity> activities) {

    /** key: khóa dòng hoạt động của bảng tuần (edu_settings.week_rows). */
    public record Activity(String key, String label, String topic, List<Goal> goals) {}

    public record Goal(Long id, Short goalNo, String text) {}
}
