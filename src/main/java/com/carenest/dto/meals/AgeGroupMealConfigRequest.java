package com.carenest.dto.meals;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Giá suất + chuẩn dinh dưỡng của một nhóm tuổi từ ngày hiệu lực; chuẩn để trống được khi trường chưa chốt. P:L:G là %
 * năng lượng, đạm/béo ĐV-TV và bột đường là gam/trẻ/ngày (NUT-24), {@code *KcalPct*} là % năng lượng của bữa so {@code dailyKcalNeed} = nhu cầu năng lượng cả ngày, kcal/trẻ (NUT-25).
 */
public record AgeGroupMealConfigRequest(
        @NotNull Long ageGroupId,
        @NotNull LocalDate effectiveFrom,
        @NotNull @DecimalMin("0") BigDecimal mealPrice,
        @DecimalMin("0") BigDecimal kcalMin,
        @DecimalMin("0") BigDecimal kcalMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal proteinPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal proteinPctMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal lipidPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal lipidPctMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal glucidPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal glucidPctMax,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal animalProteinGMin,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal animalProteinGMax,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal plantProteinGMin,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal plantProteinGMax,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal animalLipidGMin,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal animalLipidGMax,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal plantLipidGMin,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal plantLipidGMax,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal glucidGMin,
        @DecimalMin("0") @DecimalMax("9999") BigDecimal glucidGMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal morningKcalPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal morningKcalPctMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal lunchKcalPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal lunchKcalPctMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal afternoonKcalPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal afternoonKcalPctMax,

        @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000")
        BigDecimal dailyKcalNeed,

        @Size(max = 300) String normBasis) {}
