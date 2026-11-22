package com.carenest.utils;

import com.carenest.config.NutritionConversionProperties;
import com.carenest.dto.meals.NutritionConversionResponse;
import com.carenest.dto.meals.NutritionConversionResponse.ConvertedGrams;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * NUT-28: quy đổi cơ cấu áp dụng P:L:G (% năng lượng) ra gam/trẻ/ngày tại một mốc kcal. Giữ độ chính xác khi tính;
 * làm tròn chỉ ở nơi hiển thị.
 */
public class NutritionConversion {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_UP);

    private final NutritionConversionProperties factors;

    public NutritionConversion(NutritionConversionProperties factors) {
        this.factors = factors;
    }

    /** Null khi thiếu kcal hoặc một chất trong cơ cấu; kcal phải lớn hơn 0. */
    public ConvertedGrams convert(BigDecimal kcal, BigDecimal proteinPct, BigDecimal lipidPct, BigDecimal glucidPct) {
        if (kcal == null || proteinPct == null || lipidPct == null || glucidPct == null) {
            return null;
        }
        if (kcal.signum() <= 0) {
            return null;
        }
        BigDecimal proteinTotal = grams(kcal, proteinPct, factors.proteinKcalPerG());
        BigDecimal lipidTotal = grams(kcal, lipidPct, factors.lipidKcalPerG());
        BigDecimal glucid = grams(kcal, glucidPct, factors.glucidKcalPerG());
        return new ConvertedGrams(
                proteinTotal.multiply(factors.animalProteinShare(), PRECISION),
                proteinTotal.multiply(BigDecimal.ONE.subtract(factors.animalProteinShare()), PRECISION),
                lipidTotal.multiply(factors.animalLipidShare(), PRECISION),
                lipidTotal.multiply(BigDecimal.ONE.subtract(factors.animalLipidShare()), PRECISION),
                glucid);
    }

    /** Hệ số đã dùng và hai mốc kcal của trường; null khi chưa chọn cơ cấu áp dụng. */
    public NutritionConversionResponse toResponse(
            BigDecimal kcalMin,
            BigDecimal kcalMax,
            BigDecimal proteinPct,
            BigDecimal lipidPct,
            BigDecimal glucidPct) {
        if (proteinPct == null || lipidPct == null || glucidPct == null) {
            return null;
        }
        return new NutritionConversionResponse(
                factors.proteinKcalPerG(),
                factors.lipidKcalPerG(),
                factors.glucidKcalPerG(),
                factors.animalProteinShare(),
                factors.animalLipidShare(),
                convert(kcalMin, proteinPct, lipidPct, glucidPct),
                convert(kcalMax, proteinPct, lipidPct, glucidPct));
    }

    private static BigDecimal grams(BigDecimal kcal, BigDecimal pct, BigDecimal kcalPerG) {
        return kcal.multiply(pct, PRECISION).divide(ONE_HUNDRED, PRECISION).divide(kcalPerG, PRECISION);
    }
}
