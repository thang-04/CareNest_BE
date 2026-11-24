package com.carenest.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * NUT-28: hệ số quy đổi cơ cấu năng lượng ra gam (kcal/g) và tỷ lệ đạm/béo động vật dùng chung mọi nhóm tuổi.
 * Mặc định khớp dữ liệu mẫu NutriBot/PMS (4,1 – 9,3 – 4,1; 60% động vật); trường chọn 4–9–4 thì đổi qua cấu hình.
 */
@ConfigurationProperties("carenest.nutrition.conversion")
public record NutritionConversionProperties(
        @DefaultValue("4.1") BigDecimal proteinKcalPerG,
        @DefaultValue("9.3") BigDecimal lipidKcalPerG,
        @DefaultValue("4.1") BigDecimal glucidKcalPerG,
        @DefaultValue("0.60") BigDecimal animalProteinShare,
        @DefaultValue("0.60") BigDecimal animalLipidShare) {}
