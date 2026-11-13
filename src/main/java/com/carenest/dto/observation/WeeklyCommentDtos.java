package com.carenest.dto.observation;

import com.carenest.utils.enums.WeeklyCommentSource;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Nhận xét tuần theo trẻ (OBS-29). */
public final class WeeklyCommentDtos {
    private WeeklyCommentDtos() {}

    /**
     * Bảng nhận xét tuần của lớp. aiEnabled: có dịch vụ AI để viết nháp; canEdit: GV đang dạy lớp, năm đang hoạt động.
     * aiFallback: lần viết nháp này xin AI nhưng AI lỗi, đã viết theo mẫu câu.
     */
    public record WeeklyCommentBoardResponse(
            Long classId,
            String className,
            LocalDate weekStart,
            LocalDate weekEnd,
            boolean aiEnabled,
            boolean canEdit,
            boolean aiFallback,
            List<WeeklyCommentChildRow> children) {}

    /**
     * status: NONE (chưa có), DRAFT, SENT. content null với người chỉ xem khi bản chưa gửi. absentAllWeek: trẻ không có
     * mặt ngày nào trong tuần (không bắt buộc nhận xét).
     */
    public record WeeklyCommentChildRow(
            Long childId,
            String fullName,
            String status,
            boolean absentAllWeek,
            String content,
            WeeklyCommentSource draftSource,
            Instant sentAt,
            String sentByName,
            Instant updatedAt,
            WeekData data) {}

    /** Số liệu tuần từ điểm danh, đánh giá hằng ngày, phiếu bé ngoan (căn cứ của bản nháp). */
    public record WeekData(
            int presentDays,
            int schoolDays,
            int goalsMet,
            int goalsTotal,
            List<String> unmetGoals,
            int flagCount,
            boolean weeklyTicket,
            int lunchFinished,
            int lunchDays) {}

    /** childId null = cả lớp; overwrite = viết lại cả bản nháp cô đã sửa; ai = xin AI viết (AI tắt thì theo mẫu). */
    public record WeeklyCommentDraftRequest(
            @NotNull Long classId, @NotNull LocalDate weekStart, Long childId, boolean overwrite, boolean ai) {}

    public record WeeklyCommentSaveRequest(
            @NotNull Long classId,
            @NotNull LocalDate weekStart,
            @NotNull Long childId,
            String content) {}

    /** childId null = gửi cả lớp các bản nháp đã có nội dung; content (gửi 1 trẻ) lưu kèm trước khi gửi. */
    public record WeeklyCommentSendRequest(
            @NotNull Long classId, @NotNull LocalDate weekStart, Long childId, String content) {}

    /** Nhận xét tuần đã gửi, phụ huynh xem trên App. */
    public record ParentWeeklyCommentResponse(
            Long id,
            Long childId,
            LocalDate weekStart,
            LocalDate weekEnd,
            String className,
            String content,
            Instant sentAt,
            String teacherName) {}
}
