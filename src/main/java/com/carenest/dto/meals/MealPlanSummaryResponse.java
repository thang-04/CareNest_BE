package com.carenest.dto.meals;

import com.carenest.utils.enums.MealPlanStatus;
import java.time.Instant;
import java.time.LocalDate;

public record MealPlanSummaryResponse(Long id, LocalDate weekStart, MealPlanStatus status, Instant publishedAt) {}
