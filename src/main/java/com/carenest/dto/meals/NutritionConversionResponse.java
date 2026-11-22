package com.carenest.dto.meals;

import java.math.BigDecimal;

/**
 * NUT-28: hàm lượng quy đổi tại trường (g/trẻ/ngày) từ cơ cấu áp dụng tại hai mốc kcal của trường, kèm hệ số đã dùng.
 * Số giữ nguyên độ chính xác, nơi hiển thị làm tròn 2 chữ số thập phân; null khi chưa đủ cơ cấu hoặc kcal.
 */
public record NutritionConversionResponse(
        BigDecimal proteinKcalPerG,
        BigDecimal lipidKcalPerG,
        BigDecimal glucidKcalPerG,
        BigDecimal animalProteinShare,
        BigDecimal animalLipidShare,
        ConvertedGrams atKcalMin,
        ConvertedGrams atKcalMax) {

    public record ConvertedGrams(
            BigDecimal animalProteinG,
            BigDecimal plantProteinG,
            BigDecimal animalLipidG,
            BigDecimal plantLipidG,
            BigDecimal glucidG) {}
}
