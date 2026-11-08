package com.carenest.dto.meals;

import com.carenest.dto.meals.MealPlanResponse.Evaluation;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodUnit;
import com.carenest.utils.enums.MealSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Thống kê một ngày thực đơn cho N trẻ của một nhóm tuổi. {@code childrenSource}: CONFIRMED (suất đã xác nhận),
 * ENROLLED (sĩ số đang học), INPUT (người xem nhập). Lượng tính theo đơn vị gốc của thực phẩm (g/ml); lượng mua =
 * lượng ăn ÷ (1 − thải bỏ%) (NUT-07, NUT-18). {@code evaluation} cùng dạng bảng kiểm của kế hoạch tuần (NUT-24, NUT-25).
 * {@code dishIngredients}: dòng công thức của từng món (theo bữa) cho MỘT trẻ, cùng công thức với {@code ingredients}
 * nhưng không nhân N và không gộp theo thực phẩm; dùng cho màn thực đơn mẫu (không cần ngày/điểm trường).
 */
public record MenuStatisticsResponse(
        Long ageGroupId,
        String ageGroupName,
        LocalDate date,
        int children,
        String childrenSource,
        List<Dish> dishes,
        List<Ingredient> ingredients,
        List<DishIngredientLine> dishIngredients,
        BigDecimal totalCost,
        BigDecimal costPerChild,
        BigDecimal mealPrice,
        Evaluation evaluation) {

    public record Dish(MealSession session, Long dishId, String dishName, DishType dishType) {}

    /** Một dòng công thức của món cho một trẻ; {@code purchasePerChild} theo đơn vị gốc (g/ml), {@code costPerChild} đồng. */
    public record DishIngredientLine(
            MealSession session,
            Long dishId,
            String dishName,
            Long foodId,
            String foodName,
            BigDecimal gramsPerChild,
            BigDecimal wastePct,
            BigDecimal purchasePerChild,
            String purchaseUnit,
            BigDecimal unitPrice,
            BigDecimal costPerChild) {}

    public record Ingredient(
            Long foodId,
            String foodName,
            FoodUnit unit,
            BigDecimal gramsPerChild,
            BigDecimal eatenTotal,
            BigDecimal wastePct,
            BigDecimal purchaseTotal,
            String purchaseUnit,
            BigDecimal purchaseUnitSize,
            BigDecimal purchaseQty,
            BigDecimal unitPrice,
            BigDecimal cost) {}
}
