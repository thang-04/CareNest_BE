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
 * Dị ứng (NUT-23): đếm trẻ báo ăn đang học trong lớp của năm đang hoạt động theo khai báo hiện tại, không theo điểm
 * danh; rỗng không có nghĩa là món an toàn. {@code unmatchedAllergies}: chữ đã khai không khớp chất nào trong danh mục.
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
        List<FoodNeed> foods,
        List<UnmatchedAllergy> unmatchedAllergies) {

    public record SessionMenu(MealSession session, MenuBlock menuBlock, List<MenuDish> dishes) {}

    /** {@code allergyWarnings}: chỉ chất của món có trẻ dị ứng ở lớp đúng khối, ăn bữa đó. */
    public record MenuDish(
            Long id,
            String code,
            String name,
            List<AllergenResponse> allergens,
            List<DishAllergyWarning> allergyWarnings) {}

    public record DishAllergyWarning(AllergenResponse allergen, List<AllergyClassCount> byClass) {}

    public record AllergyClassCount(Long classroomId, String classroomName, int count) {}

    public record UnmatchedAllergy(String name, List<AllergyClassCount> byClass) {}

    public record ClassServings(
            MealSession session, Long classroomId, String classroomName, int normal, int substitute) {}

    public record FoodNeed(StockFoodRef food, String storage, BigDecimal quantity) {}
}
