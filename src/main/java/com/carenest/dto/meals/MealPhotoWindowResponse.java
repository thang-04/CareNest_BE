package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.time.LocalTime;

/** Khung giờ đăng ảnh của một bữa (NUT-16), giờ theo múi giờ trường. */
public record MealPhotoWindowResponse(MealSession session, LocalTime startTime, LocalTime endTime) {}
