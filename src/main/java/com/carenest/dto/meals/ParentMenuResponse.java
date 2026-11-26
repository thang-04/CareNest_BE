package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Thực đơn tuần của một con cho phụ huynh (PAR-14): chỉ khối và bữa của lớp con, không số suất hay thông tin nhân sự.
 * {@code mealsHidden}: con không báo ăn nên tuần từ hiện tại trở đi để trống. {@code unmatchedAllergies}: chữ con đã
 * khai không khớp danh mục; rỗng không có nghĩa là món an toàn.
 */
public record ParentMenuResponse(
        Long childId,
        LocalDate weekStart,
        boolean published,
        boolean mealsHidden,
        List<String> unmatchedAllergies,
        List<ParentMenuDay> days) {

    /** {@code enrolled} false: ngày con chưa học hoặc ngoài năm học. */
    public record ParentMenuDay(LocalDate date, boolean enrolled, boolean dayOff, List<ParentMenuMeal> meals) {}

    public record ParentMenuMeal(MealSession session, List<ParentMenuDish> dishes, ParentMenuPhoto photo) {}

    /** {@code childAllergens}: chất của món trùng khai báo dị ứng của con (NUT-23, chỉ cảnh báo). */
    public record ParentMenuDish(
            String name, List<AllergenResponse> allergens, List<AllergenResponse> childAllergens) {}

    public record ParentMenuPhoto(Long photoId, Instant uploadedAt) {}
}
