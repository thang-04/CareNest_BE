package com.carenest.dto.meals;

import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.time.Instant;
import java.util.List;

/** Bộ thực đơn mẫu kèm lưới món; `sessions` = các bữa khối này đang phục vụ; `ageGroupIds` = nhóm tuổi thuộc khối; `canEdit` theo MENU_MANAGE. */
public record MenuTemplateResponse(
        Long id,
        String name,
        String seasonLabel,
        MenuBlock block,
        CatalogStatus status,
        List<MealSession> sessions,
        List<Long> ageGroupIds,
        int dishCount,
        Instant updatedAt,
        boolean canEdit,
        List<Item> items) {

    public record Item(int weekNo, int weekday, MealSession session, Long dishId, String dishName, DishType dishType) {}
}
