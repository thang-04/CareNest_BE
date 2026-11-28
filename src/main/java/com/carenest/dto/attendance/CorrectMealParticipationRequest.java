package com.carenest.dto.attendance;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

/** Sửa nhầm bữa ăn sau giờ chốt, chỉ trong ngày hiện tại. Có meals thì theo từng bữa, không thì theo participates. */
public record CorrectMealParticipationRequest(
        @NotNull LocalDate date, boolean participates, Map<MealSession, Boolean> meals) {

    public CorrectMealParticipationRequest(LocalDate date, boolean participates) {
        this(date, participates, null);
    }
}
