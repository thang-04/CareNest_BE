package com.carenest.service.attendance;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Điểm danh và tham gia ăn theo lớp/ngày (ATT-01, ATT-01b, ATT-02, ATT-06); tổng hợp theo khoảng ngày. */
public interface AttendanceService {

    /** ADR-0004: điểm danh/bữa ăn của lớp trong ngày đổi; module suất ăn nghe để đánh dấu cần xác nhận lại (ATT-07). */
    record AttendanceChangedEvent(LocalDate date, Long campusId, Long classroomId) {}

    /** Số liệu một lớp trong ngày cho sổ báo ăn: sĩ số theo điểm danh và số trẻ có mặt ăn từng bữa (NUT-01). */
    record ClassMealTally(
            Long classroomId,
            String className,
            Long campusId,
            List<MealSession> sessions,
            int present,
            int absent,
            int missing,
            Map<MealSession, Integer> meals) {}

    /** schoolYearId = năm chọn ở header; null = năm đang hoạt động. */
    List<AttendanceClassResponse> getClasses(CurrentUser currentUser, Long schoolYearId);

    DayInfoResponse getDayInfo(CurrentUser currentUser, LocalDate date, Long classId);

    ClassAttendanceResponse getClassAttendance(CurrentUser currentUser, Long classId, LocalDate date);

    SaveAttendanceResponse saveClassAttendance(CurrentUser currentUser, Long classId, SaveAttendanceRequest request);

    AttendanceSummaryResponse.ClassSummary getClassSummary(
            CurrentUser currentUser, Long classId, LocalDate from, LocalDate to);

    AttendanceSummaryResponse.MultiSummary getSummary(
            CurrentUser currentUser, Long campusId, Long schoolYearId, LocalDate from, LocalDate to);

    /** Lớp của năm học tại một điểm trường (chỉ dùng nội bộ: module suất ăn tự kiểm quyền). */
    List<ClassMealTally> tallyMeals(LocalDate date, Long campusId, Long schoolYearId);

    /** Trạng thái điểm danh của một trẻ trong một ngày (chỉ dùng nội bộ, module gọi tự kiểm quyền). */
    record ChildDayStatus(Long childId, LocalDate date, AttendanceStatus status) {}

    /** Điểm danh của các trẻ trong khoảng ngày; ngày chưa điểm danh thì không có dòng (đánh giá trẻ đọc, EDU-19). */
    List<ChildDayStatus> findDayStatuses(Collection<Long> childIds, LocalDate from, LocalDate to);

    /** Một dòng điểm danh của lớp: trạng thái và các bữa trẻ ăn (chỉ dùng nội bộ, module gọi tự kiểm quyền). */
    record ClassChildDay(
            Long classroomId, Long childId, LocalDate date, AttendanceStatus status, List<MealSession> meals) {}

    /** Điểm danh của các lớp trong khoảng ngày (báo cáo chất lượng tháng đọc); ngày chưa điểm danh thì không có dòng. */
    List<ClassChildDay> findClassDays(Collection<Long> classroomIds, LocalDate from, LocalDate to);
}
