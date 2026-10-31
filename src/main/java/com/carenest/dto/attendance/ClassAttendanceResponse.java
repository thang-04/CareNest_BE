package com.carenest.dto.attendance;

import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.TemperatureLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sổ điểm danh một lớp/ngày: trẻ đang học lớp ngày đó kèm bản ghi (null = chưa điểm danh). */
public record ClassAttendanceResponse(
        DayInfoResponse day,
        Long classId,
        String className,
        Long campusId,
        List<MealSession> mealSessions,
        boolean canRecord,
        List<ChildRow> children) {

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

    public record ChildBrief(Long id, String fullName, LocalDate dateOfBirth) {}

    /** meals: mỗi bữa của lớp ⇒ trẻ có ăn không. */
    public record RecordView(
            Long id,
            AttendanceStatus status,
            Map<MealSession, Boolean> meals,
            Long recordedBy,
            String recordedByName,
            Instant recordedAt) {}
}
