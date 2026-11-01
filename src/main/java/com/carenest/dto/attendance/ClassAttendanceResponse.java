package com.carenest.dto.attendance;

import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.MealSession;
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

    public record ChildRow(ChildBrief child, RecordView record) {}

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
