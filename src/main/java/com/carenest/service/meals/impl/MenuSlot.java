package com.carenest.service.meals.impl;

import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import java.util.Set;

/**
 * Một ô món trong bữa (vd. bữa trưa ô 2 = món mặn): mỗi ngày ô nhận đúng một món thuộc {@code types}. Khóa {@code
 * LUNCH.2} dùng chung cho prompt, output model và lưới nháp.
 */
record MenuSlot(MealSession session, int index, Set<DishType> types) {

    MenuSlot {
        types = Set.copyOf(types);
    }

    String key() {
        return session.name() + "." + (index + 1);
    }
}
