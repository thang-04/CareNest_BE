package com.carenest.dto.meals;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Giá suất, phí dịch vụ (để trống = 0, không vượt giá suất — NUT-19) + chuẩn dinh dưỡng của một nhóm tuổi từ ngày hiệu lực; chuẩn để trống được khi trường chưa chốt. P:L:G là %
 * năng lượng, đạm/béo ĐV-TV và bột đường là gam/trẻ/ngày (NUT-24), {@code *KcalPct*} là % năng lượng của bữa so {@code dailyKcalMax} = nhu cầu năng lượng cả ngày tối đa, kcal/trẻ (NUT-25).
 * Năng lượng khuyến nghị tại trường và khoảng tỷ lệ đạm/béo/đường do hệ thống đặt theo Thông tư 28/2016 (NUT-33), không nhận từ client.
 * Vi chất (NUT-26) là tổng mỗi trẻ mỗi ngày trên các bữa trường phục vụ; max để trống = chỉ cần đạt mức tối thiểu.
 */
public record AgeGroupMealConfigRequest(
        @NotNull Long ageGroupId,
        @NotNull LocalDate effectiveFrom,
        @NotNull @DecimalMin("0") BigDecimal mealPrice,
        @DecimalMin("0") BigDecimal serviceFee,
        @DecimalMin("0") @DecimalMax("1250") BigDecimal animalProteinGMin,
        @DecimalMin("0") @DecimalMax("1250") BigDecimal animalProteinGMax,
        @DecimalMin("0") @DecimalMax("1250") BigDecimal plantProteinGMin,
        @DecimalMin("0") @DecimalMax("1250") BigDecimal plantProteinGMax,
        @DecimalMin("0") @DecimalMax("555") BigDecimal animalLipidGMin,
        @DecimalMin("0") @DecimalMax("555") BigDecimal animalLipidGMax,
        @DecimalMin("0") @DecimalMax("555") BigDecimal plantLipidGMin,
        @DecimalMin("0") @DecimalMax("555") BigDecimal plantLipidGMax,
        @DecimalMin("0") @DecimalMax("1250") BigDecimal glucidGMin,
        @DecimalMin("0") @DecimalMax("1250") BigDecimal glucidGMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal morningKcalPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal morningKcalPctMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal lunchKcalPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal lunchKcalPctMax,
        @DecimalMin("0") @DecimalMax("100") BigDecimal afternoonKcalPctMin,
        @DecimalMin("0") @DecimalMax("100") BigDecimal afternoonKcalPctMax,

        // NUT-25: nhu cầu năng lượng cả ngày (kcal/trẻ); nhập đủ cả hai hoặc để trống cả hai
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000")
        BigDecimal dailyKcalMin,

        @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000")
        BigDecimal dailyKcalMax,

        @DecimalMin("0") @DecimalMax("100000") BigDecimal calciumMin,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal calciumMax,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal phosphorusMin,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal phosphorusMax,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal ironMin,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal ironMax,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminAMin,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminAMax,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminB1Min,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminB1Max,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminB2Min,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminB2Max,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminPpMin,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminPpMax,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminCMin,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal vitaminCMax,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal zincMin,
        @DecimalMin("0") @DecimalMax("100000") BigDecimal zincMax,

        // NUT-28: cơ cấu áp dụng P:L:G (% năng lượng); cả ba để trống = chưa chọn, đã chọn thì tổng 100
        @DecimalMin("0") @DecimalMax("100") BigDecimal appliedProteinPct,
        @DecimalMin("0") @DecimalMax("100") BigDecimal appliedLipidPct,
        @DecimalMin("0") @DecimalMax("100") BigDecimal appliedGlucidPct,

        @Size(max = 300) String normBasis,

        // NUT-19: biên % quanh ngân sách thực phẩm để tính đạt tiền; để trống = 5
        @DecimalMin("0") @DecimalMax("100") BigDecimal budgetTolerancePct) {

    /** Chưa nêu biên tiền (BE dùng mặc định). */
    public AgeGroupMealConfigRequest(
            Long ageGroupId,
            LocalDate effectiveFrom,
            BigDecimal mealPrice,
            BigDecimal serviceFee,
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
            BigDecimal dailyKcalMin,
            BigDecimal dailyKcalMax,
            BigDecimal calciumMin,
            BigDecimal calciumMax,
            BigDecimal phosphorusMin,
            BigDecimal phosphorusMax,
            BigDecimal ironMin,
            BigDecimal ironMax,
            BigDecimal vitaminAMin,
            BigDecimal vitaminAMax,
            BigDecimal vitaminB1Min,
            BigDecimal vitaminB1Max,
            BigDecimal vitaminB2Min,
            BigDecimal vitaminB2Max,
            BigDecimal vitaminPpMin,
            BigDecimal vitaminPpMax,
            BigDecimal vitaminCMin,
            BigDecimal vitaminCMax,
            BigDecimal zincMin,
            BigDecimal zincMax,
            BigDecimal appliedProteinPct,
            BigDecimal appliedLipidPct,
            BigDecimal appliedGlucidPct,
            String normBasis) {
        this(
                ageGroupId,
                effectiveFrom,
                mealPrice,
                serviceFee,
                animalProteinGMin,
                animalProteinGMax,
                plantProteinGMin,
                plantProteinGMax,
                animalLipidGMin,
                animalLipidGMax,
                plantLipidGMin,
                plantLipidGMax,
                glucidGMin,
                glucidGMax,
                morningKcalPctMin,
                morningKcalPctMax,
                lunchKcalPctMin,
                lunchKcalPctMax,
                afternoonKcalPctMin,
                afternoonKcalPctMax,
                dailyKcalMin,
                dailyKcalMax,
                calciumMin,
                calciumMax,
                phosphorusMin,
                phosphorusMax,
                ironMin,
                ironMax,
                vitaminAMin,
                vitaminAMax,
                vitaminB1Min,
                vitaminB1Max,
                vitaminB2Min,
                vitaminB2Max,
                vitaminPpMin,
                vitaminPpMax,
                vitaminCMin,
                vitaminCMax,
                zincMin,
                zincMax,
                appliedProteinPct,
                appliedLipidPct,
                appliedGlucidPct,
                normBasis,
                null);
    }
}
