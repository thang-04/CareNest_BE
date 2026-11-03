package com.carenest.dto.meals;

import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Kế hoạch tuần kèm kết quả kiểm tự động. {@code status}: NONE (chưa lập), DRAFT, PUBLISHED. {@code evaluations}: mỗi
 * ngày phục vụ × nhóm tuổi có lớp; {@code issues}: BLOCKING chặn công bố (NUT-08), WARNING/INFO chỉ báo.
 */
public record MealPlanResponse(
        Long id,
        LocalDate weekStart,
        String status,
        Instant publishedAt,
        Long publishedBy,
        boolean canEdit,
        boolean canPublish,
        boolean publishable,
        List<Block> blocks,
        List<Day> days,
        List<Evaluation> evaluations,
        List<Issue> issues) {

    /** Khối: bữa phục vụ (hợp bữa các nhóm tuổi của khối) và nhóm tuổi thuộc khối. */
    public record Block(MenuBlock block, List<MealSession> sessions, List<Long> ageGroupIds) {}

    public record Day(LocalDate date, boolean off, String note, List<Item> items) {}

    public record Item(MealSession session, MenuBlock block, Long dishId, String dishName, DishType dishType) {}

    /** {@code status}: OK, LOW, HIGH, NA (chưa đặt định mức), INCOMPLETE (thiếu số liệu), NO_DISHES. */
    public record Evaluation(
            LocalDate date,
            Long ageGroupId,
            String ageGroupName,
            MenuBlock block,
            String status,
            NutritionTotals totals,
            BigDecimal cost,
            BigDecimal mealPrice,
            List<NormRow> rows) {}

    public record NutritionTotals(
            BigDecimal kcal,
            BigDecimal protein,
            BigDecimal lipid,
            BigDecimal glucid,
            BigDecimal animalProtein,
            BigDecimal plantProtein,
            BigDecimal animalLipid,
            BigDecimal plantLipid) {}

    /**
     * Một chỉ số so định mức (NUT-24): {@code value} thực tế (kcal, g; dòng tỷ lệ cơ cấu là %), {@code percent} tỷ lệ
     * %, {@code attainment} % đạt = thực tế / giữa khoảng.
     */
    public record NormRow(
            String key,
            BigDecimal value,
            BigDecimal percent,
            BigDecimal min,
            BigDecimal max,
            BigDecimal target,
            Integer attainment,
            String status,
            boolean optional) {}

    public record Issue(
            String severity,
            String code,
            LocalDate date,
            Long ageGroupId,
            MenuBlock block,
            Long dishId,
            String message) {}
}
