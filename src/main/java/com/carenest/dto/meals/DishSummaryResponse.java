package com.carenest.dto.meals;

import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;

public record DishSummaryResponse(
        Long id,
        String code,
        String name,
        DishType dishType,
        String description,
        boolean reference,
        CatalogStatus status) {}
