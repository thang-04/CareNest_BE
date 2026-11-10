package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bếp hôm nay của một điểm trường (NUT-03, AUTH-05): chỉ số suất đã xác nhận theo lớp, không tên/ID trẻ; số đang chờ
 * xác nhận lại không hiện. {@code menuStatus}: NONE, DRAFT, PUBLISHED — món chỉ hiện khi PUBLISHED.
 * {@code countStatus}: NONE (chưa bữa nào có suất đã xác nhận), PARTIAL (một số bữa), CONFIRMED (mọi bữa có sổ).
 * {@code foods}: lượng thực phẩm cần của ngày
 * (đơn vị gốc, đã cộng thải bỏ), rỗng khi ngày nghỉ, thực đơn chưa công bố hoặc chưa có suất đã xác nhận.
 */
public record KitchenDayResponse(
        Long campusId,
        LocalDate date,
        boolean dayOff,
        String menuStatus,
        String countStatus,
        Instant confirmedAt,
        List<SessionMenu> menu,
        List<ClassServings> servings,
        List<FoodNeed> foods) {

    public record SessionMenu(MealSession session, MenuBlock menuBlock, List<MenuDish> dishes) {}

    public record MenuDish(Long id, String code, String name) {}

    public record ClassServings(
            MealSession session, Long classroomId, String classroomName, int normal, int substitute) {}

    public record FoodNeed(StockFoodRef food, String storage, BigDecimal quantity) {}
}
