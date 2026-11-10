package com.carenest.dto.meals;

import java.time.LocalDate;
import java.util.List;

/** Sổ báo ăn của một ngày: ngày học, giờ chốt, đã khóa chưa, các bữa của mọi điểm trường trong phạm vi. */
public record MealCountDayResponse(
        LocalDate date,
        LocalDate today,
        String cutoff,
        boolean schoolDay,
        boolean locked,
        List<MealCountResponse> counts) {}
