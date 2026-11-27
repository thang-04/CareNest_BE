package com.carenest.service.attendance;

import com.carenest.dto.attendance.AttendanceClassResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.CorrectMealParticipationRequest;
import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.DayPart;
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

    /** Ngày học / giờ chốt của năm đang hoạt động cho tác vụ hệ thống (không có người dùng). */
    DayInfoResponse getActiveYearDayInfo(LocalDate date);

    /** Dùng nội bộ sau khi module gọi kiểm quyền xem sổ suất ăn. */
    DayInfoResponse getYearDayInfo(LocalDate date, Long schoolYearId);

    ClassAttendanceResponse getClassAttendance(CurrentUser currentUser, Long classId, LocalDate date);

    SaveAttendanceResponse saveClassAttendance(CurrentUser currentUser, Long classId, SaveAttendanceRequest request);

    /** ATT-13: trẻ nghỉ sáng đã đến trong khung nhận trẻ chiều; không đổi sổ báo ăn. */
    SaveAttendanceResponse markArrived(CurrentUser currentUser, Long classId, Long childId, LocalDate date);

    /**
     * ATT-03/B2: ngày đã mở lại, GV sửa trẻ bị ghi nhầm "vắng sáng không phép" (đã bấm Đã đến chiều) thành có mặt cả
     * ngày; không đổi sổ báo ăn.
     */
    SaveAttendanceResponse correctFullDayPresent(CurrentUser currentUser, Long classId, Long childId, LocalDate date);

    SaveAttendanceResponse correctMealParticipation(
            CurrentUser currentUser, Long classId, Long childId, CorrectMealParticipationRequest request);

    /** ATT-13: qua giờ đến muộn nhất mà trẻ nghỉ sáng chưa đến ⇒ Vắng cả ngày, báo GV và phụ huynh; trả số trẻ. */
    int closeMorningLeaves();

    default AttendanceSummaryResponse.ClassSummary getClassSummary(
            CurrentUser currentUser, Long classId, LocalDate from, LocalDate to) {
        return getClassSummary(currentUser, classId, from, to, null);
    }

    /**
     * semester: 1 / 2 ⇒ khoảng ngày theo học kỳ của năm học lớp (bỏ qua from/to), cắt ở hôm nay; null ⇒ from..to. Khoảng
     * luôn cắt trong năm học của lớp.
     */
    AttendanceSummaryResponse.ClassSummary getClassSummary(
            CurrentUser currentUser, Long classId, LocalDate from, LocalDate to, Integer semester);

    default AttendanceSummaryResponse.MultiSummary getSummary(
            CurrentUser currentUser, Long campusId, Long schoolYearId, LocalDate from, LocalDate to) {
        return getSummary(currentUser, campusId, schoolYearId, null, from, to);
    }

    /** ageGroupId: chỉ các lớp của nhóm tuổi (ATT-14, lọc nhóm tuổi); null = mọi nhóm. */
    default AttendanceSummaryResponse.MultiSummary getSummary(
            CurrentUser currentUser, Long campusId, Long schoolYearId, Long ageGroupId, LocalDate from, LocalDate to) {
        return getSummary(currentUser, campusId, schoolYearId, ageGroupId, from, to, null);
    }

    /** semester: như getClassSummary, theo năm học schoolYearId (null = năm đang hoạt động). */
    AttendanceSummaryResponse.MultiSummary getSummary(
            CurrentUser currentUser,
            Long campusId,
            Long schoolYearId,
            Long ageGroupId,
            LocalDate from,
            LocalDate to,
            Integer semester);

    /** Lớp của năm học tại một điểm trường (chỉ dùng nội bộ: module suất ăn tự kiểm quyền). */
    List<ClassMealTally> tallyMeals(LocalDate date, Long campusId, Long schoolYearId);

    /** Trạng thái điểm danh của một trẻ trong một ngày (chỉ dùng nội bộ, module gọi tự kiểm quyền). */
    /** absentPart: buổi nghỉ theo đơn nửa buổi, status là của buổi còn lại (ATT-13). */
    record ChildDayStatus(Long childId, LocalDate date, AttendanceStatus status, DayPart absentPart) {

        public ChildDayStatus(Long childId, LocalDate date, AttendanceStatus status) {
            this(childId, date, status, null);
        }

        /** ATT-14: ngày có mặt quy đổi, nửa buổi 0,5. */
        public double presentDays() {
            return status != AttendanceStatus.PRESENT ? 0 : absentPart == null ? 1 : 0.5;
        }
    }

    /** Điểm danh của các trẻ trong khoảng ngày; ngày chưa điểm danh thì không có dòng (đánh giá trẻ đọc, EDU-19). */
    List<ChildDayStatus> findDayStatuses(Collection<Long> childIds, LocalDate from, LocalDate to);

    /** Một dòng điểm danh của lớp: trạng thái và các bữa trẻ ăn (chỉ dùng nội bộ, module gọi tự kiểm quyền). */
    record ClassChildDay(
            Long classroomId,
            Long childId,
            LocalDate date,
            AttendanceStatus status,
            List<MealSession> meals,
            DayPart absentPart) {

        public ClassChildDay(
                Long classroomId, Long childId, LocalDate date, AttendanceStatus status, List<MealSession> meals) {
            this(classroomId, childId, date, status, meals, null);
        }

        /** ATT-14: ngày có mặt quy đổi, nửa buổi 0,5. */
        public double presentDays() {
            return status != AttendanceStatus.PRESENT ? 0 : absentPart == null ? 1 : 0.5;
        }
    }

    /** Điểm danh của các lớp trong khoảng ngày (báo cáo chất lượng tháng đọc); ngày chưa điểm danh thì không có dòng. */
    List<ClassChildDay> findClassDays(Collection<Long> classroomIds, LocalDate from, LocalDate to);

    /** Một dòng điểm danh của trẻ kèm lớp và người điểm danh (chỉ dùng nội bộ, module gọi tự kiểm quyền). */
    record ChildAttendanceDay(
            Long childId,
            Long classroomId,
            LocalDate date,
            AttendanceStatus status,
            DayPart absentPart,
            Long recordedBy) {}

    /** Lịch sử lớp cũ của trẻ chuyển đến (OBS-35); ngày chưa điểm danh thì không có dòng. */
    List<ChildAttendanceDay> findChildAttendance(Collection<Long> childIds, LocalDate from, LocalDate to);
}
