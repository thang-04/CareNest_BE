package com.carenest.dto.organization;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Độ tuổi theo tháng, khoảng [from, to): 24–36 và 36–48 không trùng nhau. */
public record AgeGroupRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 50) String shortName,
        @NotNull @Min(0) @Max(72) Integer ageFromMonths,
        @NotNull @Min(1) @Max(72) Integer ageToMonths,
        @NotEmpty List<@NotNull MealSession> mealSessions,
        @NotNull @Min(1) @Max(Short.MAX_VALUE) Integer maxClassSize,
        @Size(max = 500) String nutritionNote) {}
