package com.carenest.dto.meals;

import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bản cân đối khẩu phần một ngày. Lượng "đơn vị gốc" là g/ml, {@code purchaseQuantity} theo đơn vị mua. {@code saved}
 * false = đang dựng từ công thức món, chưa lưu; {@code stale} = bản lưu không còn khớp món của thực đơn; {@code passed} =
 * mọi nhóm tuổi đạt lượng, chất, tiền và đủ món.
 */
public record DayBalanceResponse(
        LocalDate date,
        int round,
        boolean saved,
        Long version,
        Instant savedAt,
        boolean stale,
        boolean canEdit,
        boolean dayOff,
        BigDecimal roundingStep,
        boolean passed,
        List<Campus> campuses,
        List<AgeGroup> ageGroups,
        List<Count> counts,
        List<Line> lines,
        List<Food> foods,
        List<GroupResult> groups,
        List<SummaryRow> summary) {

    public record Campus(Long campusId, String name) {}

    public record AgeGroup(Long ageGroupId, String name, MenuBlock block, List<MealSession> sessions) {}

    public record Count(Long campusId, Long ageGroupId, int children) {}

    public record Line(
            Long ageGroupId,
            MealSession session,
            Long dishId,
            String dishName,
            Long foodId,
            BigDecimal baseGrams,
            BigDecimal grams) {}

    public record Food(
            Long foodId,
            String code,
            String name,
            FoodStorage storage,
            BigDecimal wastePct,
            String purchaseUnit,
            BigDecimal purchaseUnitSize,
            BigDecimal unitPrice) {}

    /** {@code evaluation} cùng dạng bảng kiểm kế hoạch tuần (dinh dưỡng, tiền mỗi trẻ, ba kết luận). */
    public record GroupResult(
            Long ageGroupId,
            int children,
            MealPlanResponse.Evaluation evaluation,
            BigDecimal totalCost,
            List<FoodRow> rows) {}

    public record FoodRow(
            Long foodId,
            BigDecimal gramsPerChild,
            BigDecimal totalGrams,
            BigDecimal purchaseTotal,
            BigDecimal purchaseQuantity,
            BigDecimal cost) {}

    /** Tổng toàn trường của một thực phẩm và phần của từng điểm trường (cộng lại đúng tổng). */
    public record SummaryRow(
            Long foodId,
            BigDecimal purchaseTotal,
            BigDecimal purchaseQuantity,
            BigDecimal cost,
            List<CampusShare> campuses) {}

    public record CampusShare(Long campusId, BigDecimal purchaseTotal, BigDecimal purchaseQuantity) {}
}
