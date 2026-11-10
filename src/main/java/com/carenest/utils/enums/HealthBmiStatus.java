package com.carenest.utils.enums;

/**
 * Mức BMI theo tuổi của lần đo trong kỳ cân đo, theo chuẩn WHO (HLT-18): gầy còm nặng (< −3SD), gầy còm (< −2SD), bình
 * thường, nguy cơ thừa cân (> +1SD, chỉ đến 60 tháng), thừa cân (> +2SD đến 60 tháng; > +1SD từ 61 tháng), béo phì
 * (> +3SD đến 60 tháng; > +2SD từ 61 tháng). Chỉ đánh dấu sàng lọc, không chẩn đoán (HLT-12).
 */
public enum HealthBmiStatus {
    SEVERE_THINNESS,
    THINNESS,
    NORMAL,
    OVERWEIGHT_RISK,
    OVERWEIGHT,
    OBESE
}
