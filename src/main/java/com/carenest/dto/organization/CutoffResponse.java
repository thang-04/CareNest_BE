package com.carenest.dto.organization;

import com.carenest.utils.enums.SchoolYearStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Theo dạng FE: setting = lần đặt mới nhất kèm history (null nếu chưa đặt); todayTime = giờ chốt đang áp dụng hôm nay.
 * setting.schoolYear = id năm học.
 */
public record CutoffResponse(
        Year year,
        Setting setting,
        @JsonFormat(pattern = "HH:mm") LocalTime todayTime) {

    public record Year(Long id, String name, LocalDate startDate, LocalDate endDate, SchoolYearStatus status) {}

    public record Setting(
            Long schoolYear,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            LocalDate effectiveFrom,
            String note,
            Long updatedBy,
            String updatedByName,
            Instant updatedAt,
            List<Entry> history) {}

    public record Entry(
            Long id,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            LocalDate effectiveFrom,
            @JsonFormat(pattern = "HH:mm") LocalTime previousTime,
            String note,
            Long userId,
            String userName,
            Instant at) {}
}
