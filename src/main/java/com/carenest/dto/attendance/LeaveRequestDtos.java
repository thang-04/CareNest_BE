package com.carenest.dto.attendance;

import com.carenest.utils.enums.LeavePart;
import com.carenest.utils.enums.MealSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Đơn nghỉ của phụ huynh (ATT-04, ATT-05, ATT-05b). */
public final class LeaveRequestDtos {

    private LeaveRequestDtos() {}

    /**
     * part: cả ngày (mặc định) / buổi sáng / buổi chiều — nửa buổi chỉ cho đơn 1 ngày; eats: bé ăn các bữa của buổi còn
     * học (mặc định có; buổi còn học không có bữa thì bỏ qua) — ATT-12.
     */
    public record CreateLeaveRequest(
            @NotNull Long childId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotBlank @Size(max = 500) String reason,
            LeavePart part,
            Boolean eats) {

        public CreateLeaveRequest(Long childId, LocalDate fromDate, LocalDate toDate, String reason) {
            this(childId, fromDate, toDate, reason, null, null);
        }
    }

    /** Xem trước đơn nghỉ (không lưu): như CreateLeaveRequest, lý do không bắt buộc. */
    public record PreviewLeaveRequest(
            @NotNull Long childId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @Size(max = 500) String reason,
            LeavePart part,
            Boolean eats) {}

    /**
     * ATT-12, ATT-05b: offMeals = bữa của buổi nghỉ (cả ngày: mọi bữa), keptMeals = bữa của buổi còn học, theo nhóm tuổi
     * của lớp ngày đầu được áp dụng; eatsApplies = false ⇒ không hỏi "Bé tham gia ăn" (gửi eats = null); skippedDates =
     * ngày đã qua giờ chốt.
     */
    public record LeavePreviewResponse(
            LeavePart part,
            List<MealSession> offMeals,
            List<MealSession> keptMeals,
            boolean eatsApplies,
            List<LocalDate> appliedDates,
            List<LocalDate> skippedDates,
            boolean noticeOnly) {}

    /**
     * status: ACTIVE / CANCELLED (hủy hết). effectiveTo: ngày cuối còn hiệu lực. appliedDates: ngày đã ghi Vắng có phép;
     * skippedDates: ngày đã qua giờ chốt khi gửi nên không đổi điểm danh (ATT-05b). offMeals: bữa của buổi nghỉ;
     * teacherSeenAt / teacherSeenByName: lần đầu GV lớp mở đơn (null = chưa xem).
     */
    public record LeaveRequestResponse(
            Long id,
            Long childId,
            String childName,
            String historyStatus,
            Long classId,
            String className,
            Long campusId,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate effectiveTo,
            String reason,
            String status,
            Long createdBy,
            String createdByName,
            Instant createdAt,
            Instant cancelledAt,
            List<LocalDate> appliedDates,
            List<LocalDate> skippedDates,
            LeavePart part,
            Boolean eats,
            Long ageGroupId,
            List<MealSession> eatsMeals,
            List<LeaveDay> days,
            List<MealSession> offMeals,
            Instant teacherSeenAt,
            String teacherSeenByName,
            boolean noticeOnly) {}

    /** Một ngày học của đơn gắn với lớp trẻ học ngày đó (trẻ chuyển lớp sau khi gửi đơn, Cần chốt 24/11/2026). */
    public record LeaveDay(LocalDate date, Long classId, String className) {}

    /** Nhãn "Phụ huynh báo nghỉ" ở dòng trẻ của màn điểm danh. */
    /** eats null = không áp dụng (cả ngày, hoặc buổi còn học không có bữa); eatsMeals: các bữa của buổi còn học. */
    public record LeaveTag(
            Long requestId,
            LocalDate fromDate,
            LocalDate toDate,
            String reason,
            LeavePart part,
            Boolean eats,
            List<MealSession> eatsMeals,
            boolean noticeOnly) {

        public LeaveTag(Long requestId, LocalDate fromDate, LocalDate toDate, String reason) {
            this(requestId, fromDate, toDate, reason, LeavePart.FULL_DAY, null, List.of(), false);
        }
    }
}
