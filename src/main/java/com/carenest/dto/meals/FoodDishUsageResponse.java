package com.carenest.dto.meals;

import com.carenest.utils.enums.DishType;
import java.math.BigDecimal;
import java.util.List;

/** Món có dùng thực phẩm, mỗi món một dòng kèm định lượng (g hoặc ml mỗi trẻ) theo nhóm tuổi. */
public record FoodDishUsageResponse(Long dishId, String code, String name, DishType dishType, List<Portion> portions) {

    public record Portion(Long ageGroupId, BigDecimal gramsPerChild) {}
}
