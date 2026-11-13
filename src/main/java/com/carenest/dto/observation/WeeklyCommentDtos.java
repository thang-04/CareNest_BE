package com.carenest.dto.observation;

import com.carenest.utils.enums.WeeklyCommentSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Nhận xét tuần theo trẻ (OBS-29). */
public final class WeeklyCommentDtos {
    private WeeklyCommentDtos() {}

    /**
     * Bảng nhận xét tuần của lớp. aiEnabled: có dịch vụ AI để viết nháp; canEdit: GV đang dạy lớp, năm đang hoạt động,
     * tuần đã mở và chưa khóa (hoặc đang được mở lại). aiFallback: lần viết nháp này xin AI nhưng AI lỗi, đã viết theo
     * mẫu câu. week: tuần đang xem; weeks: các tuần của năm học tới tuần hiện tại, mới nhất trước.
     */
    public record WeeklyCommentBoardResponse(
            Long classId,
            String className,
            LocalDate weekStart,
            LocalDate weekEnd,
            WeekOption week,
            List<WeekOption> weeks,
            boolean aiEnabled,
            boolean canEdit,
            boolean aiFallback,
            List<WeeklyCommentChildRow> children) {}

    /**
     * status: NONE (chưa có), DRAFT, SENT. content null với người chỉ xem khi bản chưa gửi. absentAllWeek: trẻ không có
     * mặt ngày nào trong tuần (không bắt buộc nhận xét). reopened: đã gửi rồi được GV sửa lại, chưa gửi lại.
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
            boolean reopened,
            WeekData data) {}

    /**
     * Tuần nhận xét (OBS-29): weekStart = thứ Hai (khóa lưu); from–to = ngày học có giáo án tuần đã duyệt (source
     * LESSON_PLAN, label = tên tuần của giáo án) hoặc thứ Hai–Sáu khi chưa có giáo án (CALENDAR). Viết / gửi từ openFrom
     * (ngày học cuối của tuần); open = hôm nay đã tới openFrom. deadline: ngày học đầu tiên sau tuần, khóa sau 23:59:59
     * ngày đó (locked); unlockedUntil: lớp – tuần đang được mở lại tới lúc này (null = không).
     */
    public record WeekOption(
            LocalDate weekStart,
            LocalDate from,
            LocalDate to,
            String label,
            String source,
            LocalDate openFrom,
            boolean open,
            LocalDate deadline,
            boolean locked,
            Instant unlockedUntil) {}

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

    /**
     * Nhận xét tuần đã gửi, phụ huynh xem trên App. weekFrom / weekEnd: ngày học đầu, cuối của giáo án tuần đã duyệt
     * (tuần chưa có giáo án: thứ Hai–Sáu); weekLabel: tên tuần trong giáo án.
     */
    public record ParentWeeklyCommentResponse(
            Long id,
            Long childId,
            LocalDate weekStart,
            LocalDate weekFrom,
            LocalDate weekEnd,
            String weekLabel,
            String className,
            String content,
            Instant sentAt,
            String teacherName) {}

    /** Mở lại nhận xét tuần đã khóa của lớp – tuần tới validUntil (sau hiện tại, không quá ngày kết thúc năm học). */
    public record WeeklyCommentUnlockRequest(
            @NotNull Long classId,
            @NotNull LocalDate weekStart,
            @NotNull Instant validUntil,
            @NotBlank @Size(max = 300) String reason) {}

    /** Một lần mở lại nhận xét tuần; active = còn hiệu lực lúc trả về. */
    public record WeeklyCommentUnlockResponse(
            Long id,
            Long classId,
            String className,
            Long campusId,
            LocalDate weekStart,
            LocalDate weekFrom,
            LocalDate weekEnd,
            Instant validUntil,
            String reason,
            Long unlockedBy,
            String unlockedByName,
            Instant unlockedAt,
            Long revokedBy,
            String revokedByName,
            Instant revokedAt,
            boolean active) {}

    /**
     * Tình hình nhận xét tuần của một lớp cho BGH: total = trẻ của lớp trong tuần, sent = đã gửi phụ huynh; overdue = đã
     * khóa (quá hạn, không đang mở lại) mà chưa gửi đủ.
     */
    public record WeeklyCommentClassSummary(
            Long classId, String className, Long campusId, WeekOption week, int total, int sent, boolean overdue) {}
}
