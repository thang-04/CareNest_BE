package com.carenest.service.attendance;

import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceResponse;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentAttendanceSchedule;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentDailyHealthResponse;
import com.carenest.dto.attendance.ParentAttendanceDtos.ParentTodayResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Phụ huynh xem điểm danh, sức khỏe đầu ngày của con đã liên kết (AUTH-04, ATT-15); chỉ đọc. */
public interface ParentAttendanceService {

    /** Lịch đưa, đón và mốc điểm danh đang hiệu lực cho ngày phụ huynh xem. */
    ParentAttendanceSchedule getSchedule(CurrentUser currentUser, LocalDate date);

    ParentTodayResponse getToday(CurrentUser currentUser, Long childId);

    /** Khoảng tối đa 62 ngày trong một năm học. */
    ParentAttendanceResponse getAttendance(CurrentUser currentUser, Long childId, LocalDate from, LocalDate to);

    List<ParentDailyHealthResponse> getDailyHealth(CurrentUser currentUser, Long childId, LocalDate from, LocalDate to);
}
