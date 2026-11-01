package com.carenest.dto.attendance;

import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tổng hợp điểm danh theo ngày học trong khoảng from..to (không tính ngày tương lai). */
public final class AttendanceSummaryResponse {

    private AttendanceSummaryResponse() {}

    /** missing = trẻ chưa được điểm danh; mealTotal = số trẻ có ăn ít nhất một bữa; meals = số trẻ ăn từng bữa. */
    public record DayRow(
            LocalDate date,
            int present,
            int excused,
            int unexcused,
            int missing,
            int mealTotal,
            Map<MealSession, Integer> meals) {}

    public record ChildTotal(
            ClassAttendanceResponse.ChildBrief child, int present, int excused, int unexcused, int meals) {}

    public record ClassTotal(
            Long classId,
            String className,
            Long campusId,
            int size,
            int present,
            int excused,
            int unexcused,
            int meals) {}

    public record ClassSummary(
            Long classId,
            String className,
            Long campusId,
            List<MealSession> mealSessions,
            LocalDate from,
            LocalDate to,
            int size,
            List<DayRow> days,
            List<ChildTotal> children) {}

    public record MultiSummary(
            Long campusId, LocalDate from, LocalDate to, int size, List<DayRow> days, List<ClassTotal> classes) {}
}
