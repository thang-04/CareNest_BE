package com.carenest.dto.meals;

import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import com.carenest.utils.enums.MenuBlock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nháp gợi ý thực đơn: lưới món, nguồn ({@code AI} hoặc {@code RULE_BASED} kèm {@code fallbackReason}), điểm AI và
 * phương án theo quy tắc, issue của evaluator cho phương án được chọn (hoặc nguyên nhân khi {@code NO_FEASIBLE}). Số
 * liệu dinh dưỡng chỉ có trong issue/evaluator, không lấy từ model.
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
        MenuAiDraftSource source,
        MenuAiFallbackReason fallbackReason,
        String model,
        String promptVersion,
        Instant createdAt,
        Score score,
        List<Day> days,
        List<MealPlanResponse.Issue> issues,
        List<Check> checks) {

    /**
     * Một tiêu chí đo bằng code trên phương án được chọn. {@code status}: PASS (đạt), WARN (cảnh báo, không chặn), INFO
     * (thông tin), FAIL (không đạt — chỉ khi NO_FEASIBLE); {@code rules}: rule ID căn cứ.
     */
    public record Check(String code, String status, String label, String detail, List<String> rules) {}

    /** Điểm 0–100; {@code ai} null khi không có phương án AI đọc được, {@code breakdown} null khi {@code NO_FEASIBLE}. */
    public record Score(Integer ai, Integer baseline, Breakdown breakdown) {}

    public record Breakdown(int norm, int cost, int variety, int warnings) {}

    /** {@code reason}: lý do của model đã làm sạch (không chữ số, không thuật ngữ y khoa); null với phương án quy tắc. */
    public record Day(LocalDate date, boolean off, String reason, List<Item> items) {}

    public record Item(MealSession session, Long dishId, String dishName, DishType dishType) {}
}
