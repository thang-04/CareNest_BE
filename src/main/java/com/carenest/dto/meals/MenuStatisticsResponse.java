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
 */
public record MenuStatisticsResponse(
        Long ageGroupId,
        String ageGroupName,
        LocalDate date,
        int children,
        String childrenSource,
        List<Dish> dishes,
        List<Ingredient> ingredients,
        BigDecimal totalCost,
        BigDecimal costPerChild,
        BigDecimal mealPrice,
        Evaluation evaluation) {

    public record Dish(MealSession session, Long dishId, String dishName, DishType dishType) {}

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
