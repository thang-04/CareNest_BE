package com.carenest.dto.observation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Đánh giá cuối chủ đề (OBS-27): bảng trẻ × MT, ô Đạt (+) / Chưa đạt (−) / chưa chấm (null). */
public final class ThemeEvaluationDtos {
    private ThemeEvaluationDtos() {}

    /** Lớp (mọi nhóm tuổi) người dùng xem được; teaches = GV đang dạy lớp (được sửa, xác nhận). */
    public record ClassItem(Long classId, String name, Long campusId, Long ageGroupId, boolean teaches) {}

    /**
     * period: UPCOMING | ONGOING | ENDED theo ngày chủ đề; status: NONE (chưa mở bảng) | DRAFT | CONFIRMED.
     */
    public record ThemeItem(
            Long themeId,
            String name,
            String abbr,
            LocalDate startDate,
            LocalDate endDate,
            String period,
            String status) {}

    /** goalId là mã dòng gốc của MT (giữ qua các bản kế hoạch). */
    public record GoalColumn(
            Long goalId, Short goalNo, String text, int metChildren, int assessedChildren, Integer rate) {}

    /** auto: kết quả hệ thống tính từ KTKN (để màn hình biết ô sửa tay có khác kết quả tự tính không). */
    public record Cell(Long goalId, Boolean met, boolean manual, Boolean auto) {}

    /** passed: trẻ đạt từ passPercent% số MT đã chấm; null khi chưa chấm MT nào. */
    public record ChildRow(
            Long childId,
            String fullName,
            LocalDate dateOfBirth,
            List<Cell> cells,
            int met,
            int notMet,
            Integer rate,
            Boolean passed) {}

    public record ThemeEvaluationResponse(
            Long classId,
            String className,
            Long themeId,
            String themeName,
            LocalDate startDate,
            LocalDate endDate,
            String period,
            String status,
            String note,
            List<GoalColumn> goals,
            List<ChildRow> rows,
            int assessedChildren,
            int passedChildren,
            int notPassedChildren,
            int passPercent,
            boolean canEdit,
            boolean canConfirm,
            String confirmedByName,
            Instant confirmedAt,
            ThemeSigners signers) {}

    /**
     * Họ tên in dưới chỗ ký của bản in (không phải bước duyệt): GV chủ nhiệm (chưa có thì các GV lớp), tổ trưởng nhóm tuổi, PHT ban hành kế hoạch năm
     * của nhóm tuổi; không xác định được thì rỗng / null để ký tay.
     */
    public record ThemeSigners(List<String> teachers, List<String> teamLeaders, String vicePrincipal) {}

    /** met null = bỏ sửa tay, dùng lại kết quả hệ thống tính. */
    public record CellInput(@NotNull Long childId, @NotNull Long goalId, Boolean met) {}

    public record ThemeEvaluationRequest(
            @NotNull Long classId,
            @NotNull Long themeId,
            @Size(max = 1000) String note,
            @Size(max = 2000) List<@Valid CellInput> cells) {}
}
