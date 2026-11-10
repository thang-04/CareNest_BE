package com.carenest.dto.organization;

import com.carenest.utils.enums.MealSession;
import java.util.List;

public record AgeGroupDetailResponse(
        Long id,
        String code,
        String name,
        String shortName,
        Short sortOrder,
        Integer ageFromMonths,
        Integer ageToMonths,
        List<MealSession> mealSessions,
        int mealsPerDay,
        Integer maxClassSize,
        String nutritionNote) {}
