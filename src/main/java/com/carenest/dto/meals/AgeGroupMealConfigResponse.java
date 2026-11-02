package com.carenest.dto.meals;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code editable} = chưa tới ngày hiệu lực; bản đã hiệu lực chỉ đọc. */
public record AgeGroupMealConfigResponse(
        Long id,
        Long ageGroupId,
        LocalDate effectiveFrom,
        BigDecimal mealPrice,
        BigDecimal kcalMin,
        BigDecimal kcalMax,
        BigDecimal proteinPctMin,
        BigDecimal proteinPctMax,
        BigDecimal lipidPctMin,
        BigDecimal lipidPctMax,
        BigDecimal glucidPctMin,
        BigDecimal glucidPctMax,
        String normBasis,
        boolean editable) {}
