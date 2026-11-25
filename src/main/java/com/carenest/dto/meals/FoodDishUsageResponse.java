package com.carenest.dto.meals;

import com.carenest.utils.enums.DishType;

/** Món có dùng thực phẩm; mỗi món một dòng dù thực phẩm có định lượng ở nhiều nhóm tuổi. */
public record FoodDishUsageResponse(Long dishId, String code, String name, DishType dishType) {}
