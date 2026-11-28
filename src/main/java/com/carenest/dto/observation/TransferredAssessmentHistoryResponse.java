package com.carenest.dto.observation;

import com.carenest.dto.observation.EvaluationDtos.EvaluationDay;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.HealthSymptom;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lịch sử ở lớp cũ của trẻ đang thuộc lớp (OBS-35): chỉ xem, chỉ trẻ chuyển đến, chỉ trong thời gian trẻ ở lớp cũ cùng năm
 * học. days: đánh giá hằng ngày đã lưu; weeklyComments: nhận xét tuần đã gửi (không có bản nháp); attendance, health: điểm
 * danh và sức khỏe trong ngày ở lớp cũ; themeResults: bảng cuối chủ đề đã xác nhận (không có bản nháp).
 */
public record TransferredAssessmentHistoryResponse(
        Long classId, LocalDate from, LocalDate to, List<ChildHistory> children) {

    public record ChildHistory(
            Long childId,
            String childName,
            List<ClassStay> stays,
            List<HistoryDay> days,
            List<HistoryWeeklyComment> weeklyComments,
            List<HistoryAttendance> attendance,
            List<HistoryHealth> health,
            List<HistoryThemeResult> themeResults) {}

    /** Khoảng trẻ học lớp cũ trong khoảng xem. */
    public record ClassStay(Long classId, String className, LocalDate from, LocalDate to) {}

    /** day.classId / day.className là lớp đã ghi; recordedByName là người lưu gần nhất. */
    public record HistoryDay(EvaluationDay day, Long recordedById, String recordedByName, Instant recordedAt) {}

    public record HistoryWeeklyComment(
            LocalDate weekStart,
            Long classId,
            String className,
            String content,
            Instant sentAt,
            Long sentById,
            String sentByName) {}

    /** absentPart: buổi nghỉ theo đơn nửa buổi, status là của buổi còn lại (ATT-13). */
    public record HistoryAttendance(
            LocalDate date,
            AttendanceStatus status,
            DayPart absentPart,
            Long classId,
            String className,
            Long recordedById,
            String recordedByName) {}

    /** Một lần ghi nhận sức khỏe trong ngày ở lớp cũ (quan sát, không chẩn đoán). */
    public record HistoryHealth(
            LocalDate date,
            BigDecimal temperature,
            List<HealthSymptom> symptoms,
            String note,
            Long classId,
            String className,
            Long recordedById,
            String recordedByName,
            Instant recordedAt) {}

    /** Kết quả của trẻ ở bảng cuối chủ đề đã xác nhận của lớp cũ (snapshot lúc xác nhận). */
    public record HistoryThemeResult(
            Long themeId,
            String themeName,
            LocalDate startDate,
            LocalDate endDate,
            Long classId,
            String className,
            List<HistoryGoalResult> goals,
            int met,
            int notMet,
            Integer rate,
            Boolean passed,
            Long confirmedById,
            String confirmedByName,
            Instant confirmedAt) {}

    public record HistoryGoalResult(Long goalId, Short goalNo, String text, Boolean met) {}
}
