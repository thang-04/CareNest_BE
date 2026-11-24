package com.carenest.dto.attendance;

import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.ParentAttendanceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * App phụ huynh xem điểm danh, sức khỏe đầu ngày của con (AUTH-04, ATT-15). Chỉ số đã ghi: ngày đang được mở lại thì
 * trả số trước khi mở lại; mỗi dòng mang lớp của ngày đó (trẻ chuyển lớp / đã nghỉ học vẫn xem được số cũ).
 */
public final class ParentAttendanceDtos {

    private ParentAttendanceDtos() {}

    /** absentPart: buổi nghỉ theo đơn nửa buổi; arrivedAt: lúc GV ghi có mặt (null nếu không có mặt). */
    public record ParentTodayResponse(
            LocalDate date,
            ParentAttendanceStatus status,
            DayPart absentPart,
            Instant arrivedAt,
            Long classId,
            String className) {}

    /**
     * mealsEaten: bữa trẻ ăn; cancelledMeals: bữa của nhóm tuổi không ăn vì vắng / nghỉ buổi; pickedUpByName: người đón
     * (chưa có nguồn cho phụ huynh, luôn null).
     */
    public record ParentAttendanceDay(
            LocalDate date,
            ParentAttendanceStatus status,
            DayPart absentPart,
            Instant arrivedAt,
            List<MealSession> mealsEaten,
            List<MealSession> cancelledMeals,
            String pickedUpByName,
            Long classId,
            String className) {}

    /** Ngày học trẻ đang học tới hôm nay; present / excused / absent quy đổi nửa buổi 0,5 (ATT-14). */
    public record ParentAttendanceTotals(int schoolDays, double present, double excused, double absent) {}

    public record ParentAttendanceResponse(
            Long childId,
            LocalDate from,
            LocalDate to,
            List<ParentAttendanceDay> days,
            ParentAttendanceTotals totals) {}

    /** Một lần ghi nhận sức khỏe đầu ngày; note là ghi chú GV gửi phụ huynh (bảng không có ghi chú nội bộ). */
    public record ParentDailyHealthResponse(
            LocalDate date,
            BigDecimal temperature,
            List<HealthSymptom> symptoms,
            String note,
            String recordedByName,
            Long classId,
            String className) {}
}
