package com.carenest.service.attendance;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Điểm danh và tham gia ăn theo lớp/ngày (ATT-01, ATT-01b, ATT-02, ATT-06); tổng hợp theo khoảng ngày. */
public interface AttendanceService {

    /** ADR-0004: điểm danh/bữa ăn của lớp trong ngày đổi; module suất ăn nghe để đánh dấu cần xác nhận lại (ATT-07). */
    record AttendanceChangedEvent(LocalDate date, Long campusId, Long classroomId) {}

    /** schoolYearId = năm chọn ở header; null = năm đang hoạt động. */
    List<AttendanceClassResponse> getClasses(CurrentUser currentUser, Long schoolYearId);

    DayInfoResponse getDayInfo(CurrentUser currentUser, LocalDate date, Long classId);

    ClassAttendanceResponse getClassAttendance(CurrentUser currentUser, Long classId, LocalDate date);

    SaveAttendanceResponse saveClassAttendance(CurrentUser currentUser, Long classId, SaveAttendanceRequest request);

    AttendanceSummaryResponse.ClassSummary getClassSummary(
            CurrentUser currentUser, Long classId, LocalDate from, LocalDate to);

    AttendanceSummaryResponse.MultiSummary getSummary(
            CurrentUser currentUser, Long campusId, Long schoolYearId, LocalDate from, LocalDate to);
}
