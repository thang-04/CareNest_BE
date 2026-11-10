package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import java.time.Instant;
import java.time.LocalTime;

/** Khung giờ đăng ảnh của một bữa, chung toàn trường (NUT-16); lưu trong {@link MealPhotoSettings}. */
public record MealPhotoWindow(
        MealSession session, LocalTime startTime, LocalTime endTime, Long updatedBy, Instant updatedAt) {

    public boolean contains(LocalTime time) {
        return !time.isBefore(startTime) && !time.isAfter(endTime);
    }
}
