package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;

/** Thực đơn tuần đã công bố của một lớp cho giáo viên trên App: khối và bữa của lớp, không số suất. */
public record ClassMenuResponse(Long classId, LocalDate weekStart, boolean published, List<ClassMenuDay> days) {

    /** {@code inSchoolYear} false: ngày ngoài năm học của lớp. */
    public record ClassMenuDay(LocalDate date, boolean inSchoolYear, boolean dayOff, List<ClassMenuMeal> meals) {}

    public record ClassMenuMeal(MealSession session, List<ClassMenuDish> dishes) {}

    public record ClassMenuDish(String name, List<AllergenResponse> allergens) {}
}
