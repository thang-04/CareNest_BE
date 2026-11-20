package com.carenest.dto.meals;

import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import java.util.List;

/**
 * Món ứng viên theo từng ô của nháp gợi ý tuần, theo thứ tự ưu tiên của hệ thống (NUT-05, NUT-09: chỉ món đang dùng,
 * đủ định lượng và dữ liệu). Cờ là tín hiệu chọn món, không phải rule chặn.
 */
public record MenuAiDraftCandidatesResponse(List<Slot> slots) {

    public record Slot(String key, MealSession session, int index, List<DishType> dishTypes, List<Dish> dishes) {}

    /** {@code outOfStock}: món dùng thực phẩm kho khô đang hết ở mọi điểm trường (NUT-11). */
    public record Dish(
            Long dishId, String dishName, DishType dishType, boolean seasonal, boolean familiar, boolean outOfStock) {}
}
