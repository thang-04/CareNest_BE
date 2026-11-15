package com.carenest.service.eduplan.impl;

import com.carenest.dto.organization.SchoolYearDetailResponse;
import java.time.LocalDate;
import java.util.Optional;

/** Học kỳ theo cấu hình năm học (G94-235): chủ đề thuộc học kỳ của ngày bắt đầu (FR-001). */
final class EduSemesters {
    private EduSemesters() {}

    static boolean configured(SchoolYearDetailResponse year) {
        return year != null
                && year.semester1Start() != null
                && year.semester1End() != null
                && year.semester2Start() != null
                && year.semester2End() != null;
    }

    static Optional<Integer> of(SchoolYearDetailResponse year, LocalDate date) {
        if (!configured(year) || date == null) return Optional.empty();
        if (date.isBefore(year.semester1Start()) || date.isAfter(year.semester2End())) return Optional.empty();
        // ponytail: chủ đề bắt đầu trong kỳ nghỉ giữa hai học kỳ tính vào học kỳ sau
        return Optional.of(date.isAfter(year.semester1End()) ? 2 : 1);
    }

    static LocalDate start(SchoolYearDetailResponse year, int no) {
        if (!configured(year)) return null;
        return no == 1 ? year.semester1Start() : year.semester2Start();
    }

    static LocalDate end(SchoolYearDetailResponse year, int no) {
        if (!configured(year)) return null;
        return no == 1 ? year.semester1End() : year.semester2End();
    }
}
