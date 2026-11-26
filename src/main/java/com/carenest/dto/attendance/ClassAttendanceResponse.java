package com.carenest.dto.attendance;

import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.TemperatureLevel;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Sổ điểm danh một lớp/ngày: trẻ đang học lớp ngày đó kèm bản ghi (null = chưa điểm danh). latestAfternoonArrival: giờ
 * nhận trẻ buổi chiều: từ afternoonStart đến latestAfternoonArrival (ATT-03/B2).
 */
public record ClassAttendanceResponse(
        DayInfoResponse day,
        Long classId,
        String className,
        Long campusId,
        List<MealSession> mealSessions,
        boolean canRecord,
        List<ChildRow> children,
        @JsonFormat(pattern = "HH:mm") LocalTime afternoonStart,
        @JsonFormat(pattern = "HH:mm") LocalTime latestAfternoonArrival) {

    /** health: sức khỏe trong ngày theo lần ghi nhận mới nhất; checked = false khi chưa khám. */
    /** leave: đơn nghỉ phụ huynh còn hiệu lực ngày này (nhãn "Phụ huynh báo nghỉ", ATT-04); null nếu không có. */
    public record ChildRow(
            ChildBrief child, RecordView record, HealthSummary health, LeaveRequestDtos.LeaveTag leave) {}

    /** Lần ghi nhận mới nhất trong ngày; temperatureLevel null khi không đo. */
    public record HealthSummary(
            boolean checked,
            int entryCount,
            HealthObservation observation,
            BigDecimal temperature,
            TemperatureLevel temperatureLevel,
            List<HealthSymptom> symptoms,
            String note,
            Instant recordedAt) {}

    /** hasAllergy: hồ sơ trẻ có khai báo dị ứng (GV biết trẻ cần suất thay thế). */
    public record ChildBrief(
            Long id,
            String fullName,
            LocalDate dateOfBirth,
            boolean mealRegistered,
            boolean hasAllergy,
            String historyStatus) {
        public ChildBrief(Long id, String fullName, LocalDate dateOfBirth, boolean mealRegistered, boolean hasAllergy) {
            this(id, fullName, dateOfBirth, mealRegistered, hasAllergy, null);
        }
    }

    /**
     * meals: mỗi bữa của lớp ⇒ trẻ có ăn không. absentPart: buổi nghỉ có phép theo đơn nửa buổi, status là của buổi còn
     * lại — nghỉ sáng: EXCUSED = chưa đến, PRESENT = đã đến buổi chiều, UNEXCUSED = vắng cả ngày (ATT-13).
     */
    public record RecordView(
            Long id,
            AttendanceStatus status,
            Map<MealSession, Boolean> meals,
            Long recordedBy,
            String recordedByName,
            Instant recordedAt,
            DayPart absentPart,
            boolean absentPartUnexcused) {}
}
