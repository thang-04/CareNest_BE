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
        BigDecimal animalProteinGMin,
        BigDecimal animalProteinGMax,
        BigDecimal plantProteinGMin,
        BigDecimal plantProteinGMax,
        BigDecimal animalLipidGMin,
        BigDecimal animalLipidGMax,
        BigDecimal plantLipidGMin,
        BigDecimal plantLipidGMax,
        BigDecimal glucidGMin,
        BigDecimal glucidGMax,
        BigDecimal morningKcalPctMin,
        BigDecimal morningKcalPctMax,
        BigDecimal lunchKcalPctMin,
        BigDecimal lunchKcalPctMax,
        BigDecimal afternoonKcalPctMin,
        BigDecimal afternoonKcalPctMax,
        BigDecimal dailyKcalNeed,
        String normBasis,
        boolean editable) {}
