package com.carenest.dto.meals;

import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodUnit;
import com.carenest.utils.enums.MealPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Chi tiết món: lưới định lượng, dinh dưỡng và giá một suất tính riêng từng nhóm tuổi, chất dị ứng suy từ thực phẩm. */
public record DishDetailResponse(
        Long id,
        String code,
        String name,
        DishType dishType,
        String description,
        boolean reference,
        CatalogStatus status,
        List<Ingredient> ingredients,
        List<AgeGroupNutrition> nutritionByAgeGroup,
        List<AllergenResponse> allergens,
        List<TemplateUsage> usedInTemplates,
        List<WeekUsage> usedInWeeks) {

    /** Thực đơn mẫu đang dùng có chứa món (chỉ có ở chi tiết một món, danh sách trả rỗng). */
    public record TemplateUsage(Long id, String name) {}

    /** Tuần thực đơn chưa qua có chứa món (chỉ có ở chi tiết một món, danh sách trả rỗng). */
    public record WeekUsage(LocalDate weekStart, MealPlanStatus status) {}

    public record Ingredient(
            Long foodId, String foodCode, String foodName, FoodUnit unit, Long ageGroupId, BigDecimal gramsPerChild) {}

    /**
     * NUT-24: 6 chỉ số (g, kcal) một suất; protein, lipid là tổng động vật + thực vật. NUT-26: 8 vi chất một suất, null
     * khi có nguyên liệu (lượng &gt; 0) chưa có số liệu vi chất đó.
     */
    public record AgeGroupNutrition(
            Long ageGroupId,
            BigDecimal kcal,
            BigDecimal animalProtein,
            BigDecimal plantProtein,
            BigDecimal protein,
            BigDecimal animalLipid,
            BigDecimal plantLipid,
            BigDecimal lipid,
            BigDecimal glucid,
            BigDecimal cost,
            BigDecimal calcium,
            BigDecimal phosphorus,
            BigDecimal iron,
            BigDecimal vitaminA,
            BigDecimal vitaminB1,
            BigDecimal vitaminB2,
            BigDecimal vitaminPp,
            BigDecimal vitaminC) {}
}
