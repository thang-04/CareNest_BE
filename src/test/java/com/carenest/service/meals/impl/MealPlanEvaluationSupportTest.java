package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.config.NutritionConversionProperties;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.utils.NutritionConversion;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MealPlanEvaluationSupportTest {

    private static final NutritionConversion CONVERTER = new NutritionConversion(new NutritionConversionProperties(
            new BigDecimal("4.1"),
            new BigDecimal("9.3"),
            new BigDecimal("4.1"),
            new BigDecimal("0.60"),
            new BigDecimal("0.60")));

    @Test
    @DisplayName(
            "NUT-34: đã chọn cơ cấu áp dụng thì định mức gam là hàm lượng quy đổi tại hai mốc kcal, làm tròn 2 số lẻ")
    void gramNormsFollowConvertedContent() {
        Norm norm = MealPlanEvaluationSupport.toNorm(config("615", "726", "14", "26", "60", "99"), CONVERTER);

        // Bộ nghiệm thu NUT-27: đạm ĐV 12,60 → 14,87; đường 90,00 → 106,24 (thay định mức gam đã lưu 99)
        assertThat(norm.animalProteinG().min()).isEqualByComparingTo("12.60");
        assertThat(norm.animalProteinG().max()).isEqualByComparingTo("14.87");
        assertThat(norm.glucidG().min()).isEqualByComparingTo("90.00");
        assertThat(norm.glucidG().max()).isEqualByComparingTo("106.24");
        // Cơ cấu áp dụng đã cấu hình đi kèm định mức để màn hiển thị đúng % trường chọn
        assertThat(norm.applied().protein()).isEqualByComparingTo("14");
        assertThat(norm.applied().glucid()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("NUT-34: chưa chọn cơ cấu áp dụng thì giữ định mức gam đã lưu")
    void gramNormsFallBackToStoredRange() {
        Norm norm = MealPlanEvaluationSupport.toNorm(config("615", "726", null, null, null, "11"), CONVERTER);

        assertThat(norm.animalProteinG().min()).isEqualByComparingTo("11");
    }

    private static AgeGroupMealConfig config(
            String kcalMin, String kcalMax, String protein, String lipid, String glucid, String animalProteinGMin) {
        AgeGroupMealConfig config = AgeGroupMealConfig.create(4L, 3L);
        ReflectionTestUtils.setField(config, "kcalMin", new BigDecimal(kcalMin));
        ReflectionTestUtils.setField(config, "kcalMax", new BigDecimal(kcalMax));
        ReflectionTestUtils.setField(config, "animalProteinGMin", new BigDecimal(animalProteinGMin));
        ReflectionTestUtils.setField(config, "appliedProteinPct", protein == null ? null : new BigDecimal(protein));
        ReflectionTestUtils.setField(config, "appliedLipidPct", lipid == null ? null : new BigDecimal(lipid));
        ReflectionTestUtils.setField(config, "appliedGlucidPct", glucid == null ? null : new BigDecimal(glucid));
        return config;
    }
}
