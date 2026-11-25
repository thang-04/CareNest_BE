package com.carenest.dto.meals;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NutritionGroupStatus(
        @NotNull @Positive Long ageGroupId, @NotNull Boolean enabled) {}
