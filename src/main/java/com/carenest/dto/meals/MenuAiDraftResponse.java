package com.carenest.dto.meals;

import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import com.carenest.utils.enums.MenuAiStrategy;
import com.carenest.utils.enums.MenuBlock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nháp gợi ý thực đơn: lưới món, nguồn ({@code AI} hoặc {@code RULE_BASED} kèm {@code fallbackReason}), điểm AI và
 * phương án theo quy tắc, issue của evaluator cho phương án được chọn (hoặc nguyên nhân khi {@code NO_FEASIBLE}).
 * {@code feasible=false} với nháp có lưới món = phương án theo quy tắc còn lỗi chặn (NUT-09): dùng được làm điểm bắt
 * đầu để sửa tay, tuần chưa công bố được. {@code editedCells}: số ô người duyệt đã sửa so với gợi ý lúc dùng.
 * {@code historyFrom}–{@code historyTo}: khoảng ngày của các tuần liền trước đã đối chiếu để tránh lặp món (NUT-20);
 * null ở nháp tạo trước khi có trường này. Số liệu dinh dưỡng chỉ có trong issue/evaluator, không lấy từ model.
 */
public record MenuAiDraftResponse(
        Long id,
        MenuAiDraftKind kind,
        MenuBlock block,
        LocalDate weekStart,
        LocalDate targetDate,
        MealSession targetSession,
        Long fromDishId,
        MenuAiDraftStatus status,
        boolean feasible,
        MenuAiDraftSource source,
        MenuAiFallbackReason fallbackReason,
        String model,
        String promptVersion,
        Instant createdAt,
        Score score,
        List<Slot> slots,
        List<Day> days,
        List<MealPlanResponse.Issue> issues,
        List<Check> checks,
        int editedCells,
        LocalDate historyFrom,
        LocalDate historyTo,
        MenuAiStrategy strategy,
        List<ChangedCell> changedCells) {

    /**
     * Một ô của gợi ý khác thực đơn mẫu khi {@code strategy=TEMPLATE_ADJUST} (rỗng với {@code FREE}): {@code slot} khớp
     * {@link Slot#key()}; {@code fromDishId} null khi thực đơn mẫu không có món ở ô đó.
     */
    public record ChangedCell(
            LocalDate date, String slot, Long fromDishId, String fromDishName, Long toDishId, String toDishName) {}

    /** Một hàng của bảng gợi ý: ô {@code key} (vd. {@code LUNCH.2}) của bữa, nhận món thuộc {@code dishTypes}. */
    public record Slot(String key, MealSession session, int index, List<DishType> dishTypes) {}

    /**
     * Một tiêu chí đo bằng code trên phương án được chọn. {@code status}: PASS (đạt), WARN (cảnh báo, không chặn), INFO
     * (thông tin), FAIL (không đạt — nháp {@code feasible=false}); {@code rules}: rule ID căn cứ.
     */
    public record Check(String code, String status, String label, String detail, List<String> rules) {}

    /** Điểm 0–100; {@code ai} null khi không có phương án AI đọc được, {@code breakdown} null khi {@code NO_FEASIBLE}. */
    public record Score(Integer ai, Integer baseline, Breakdown breakdown) {}

    public record Breakdown(int norm, int cost, int variety, int warnings) {}

    /** {@code reason}: lý do của model đã làm sạch (không chữ số, không thuật ngữ y khoa); null với phương án quy tắc. */
    public record Day(LocalDate date, boolean off, String reason, List<Item> items) {}

    /** {@code slot}: khóa ô của món trong bữa, khớp {@link Slot#key()}. */
    public record Item(String slot, MealSession session, Long dishId, String dishName, DishType dishType) {}
}
