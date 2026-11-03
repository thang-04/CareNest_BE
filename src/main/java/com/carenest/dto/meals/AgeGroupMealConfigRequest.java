package com.carenest.dto.meals;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Giá suất + chuẩn dinh dưỡng của một nhóm tuổi từ ngày hiệu lực; chuẩn để trống được khi trường chưa chốt. Tỷ lệ động vật = % đạm, béo động vật trên tổng (NUT-24). */
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
        @DecimalMin("0") @DecimalMax("100") BigDecimal animalProteinPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal animalProteinPctMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal animalLipidPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal animalLipidPctMax,
        @Size(max = 300) String normBasis) {}
