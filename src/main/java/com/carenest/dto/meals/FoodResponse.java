package com.carenest.dto.meals;

import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FoodUnit;
import java.math.BigDecimal;
import java.util.List;

public record FoodResponse(
        Long id,
        String code,
        String name,
        FoodGroup foodGroup,
        FoodUnit unit,
        String purchaseUnit,
        BigDecimal purchaseUnitSize,
        BigDecimal unitPrice,
        BigDecimal kcal,
        BigDecimal protein,
        BigDecimal lipid,
        BigDecimal glucid,
        String nutrientSource,
        String nutrientSourceVersion,
        BigDecimal wastePct,
        FoodStorage storage,
        FoodOrigin origin,
        String note,
        CatalogStatus status,
        List<AllergenResponse> allergens) {}
