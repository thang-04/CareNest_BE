package com.carenest.service.meals;

import com.carenest.dto.meals.MealPhotoWeekResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

public interface MealPhotoWeekService {

    /** Ảnh bữa ăn và thông tin đối chiếu của tuần chứa {@code weekStart} (null = tuần hiện tại), thứ 2–6. */
    MealPhotoWeekResponse getWeek(CurrentUser currentUser, Long campusId, LocalDate weekStart);
}
