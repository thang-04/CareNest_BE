package com.carenest.dto.attendance;

import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tổng hợp điểm danh theo ngày học trong khoảng from..to (không tính ngày tương lai). */
public final class AttendanceSummaryResponse {

    private AttendanceSummaryResponse() {}

    /**
     * missing = trẻ chưa được điểm danh; mealTotal = số trẻ có ăn ít nhất một bữa (lượt trẻ ăn); substitute = trong đó
     * trẻ ăn suất thay thế (có khai dị ứng trong hồ sơ); meals = số trẻ ăn từng bữa. morningOff / afternoonOff = trẻ
     * nghỉ buổi sáng / buổi chiều theo đơn (đã gồm trong present/excused/unexcused theo buổi còn lại, ATT-14).
     */
    public record DayRow(
            LocalDate date,
            int present,
            int excused,
            int unexcused,
            int missing,
            int mealTotal,
            int substitute,
            Map<MealSession, Integer> meals,
            int morningOff,
            int afternoonOff) {}

    /**
     * attendedDays: ngày có mặt quy đổi, nghỉ nửa buổi tính 0,5 (ATT-14); mealCounts: số ngày trẻ ăn từng bữa;
     * morningOff / afternoonOff: số ngày nghỉ buổi sáng / buổi chiều.
     */
    public record ChildTotal(
            ClassAttendanceResponse.ChildBrief child,
            int present,
            int excused,
            int unexcused,
            int meals,
            double attendedDays,
            Map<MealSession, Integer> mealCounts,
            int morningOff,
            int afternoonOff) {}

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

    /** mealSessions: các bữa có ở ít nhất một lớp trong phạm vi. */
    public record MultiSummary(
            Long campusId,
            LocalDate from,
            LocalDate to,
            int size,
            List<DayRow> days,
            List<ClassTotal> classes,
            List<MealSession> mealSessions) {}
}
