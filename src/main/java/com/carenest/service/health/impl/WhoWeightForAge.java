package com.carenest.service.health.impl;

import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.WeightForAgeStatus;
import java.math.BigDecimal;

/**
 * Cân nặng theo tuổi chuẩn WHO (HLT-03, HLT-20) từ {@code health/who-weight-for-age.csv}: đến 60 tháng WHO 2006, từ 61
 * tháng WHO 2007. Ngoài ±3SD tính theo cách giới hạn đuôi của WHO (như BMI). Chỉ dùng cho báo cáo tăng trưởng.
 */
final class WhoWeightForAge {

    private static final WhoLmsTable TABLE = WhoLmsTable.load("/health/who-weight-for-age.csv", "cân nặng theo tuổi");

    /** Mức và z-score làm tròn 2 chữ số. */
    record Result(WeightForAgeStatus status, BigDecimal zScore) {}

    private WhoWeightForAge() {}

    /** Null khi thiếu giới tính, cân nặng hoặc tuổi ngoài 0–84 tháng. */
    static Result classify(Gender gender, int ageMonths, BigDecimal weightKg) {
        if (weightKg == null) {
            return null;
        }
        BigDecimal z = TABLE.zScore(gender, ageMonths, weightKg.doubleValue(), true);
        if (z == null) {
            return null;
        }
        double v = z.doubleValue();
        WeightForAgeStatus status = v < -3
                ? WeightForAgeStatus.SEVERELY_UNDERWEIGHT
                : v < -2 ? WeightForAgeStatus.UNDERWEIGHT : WeightForAgeStatus.NORMAL;
        return new Result(status, z);
    }

    /** L, M, S của một giới tính tại tháng tuổi (để test đối chiếu bảng). */
    static double[] lms(Gender gender, int ageMonths) {
        return TABLE.lms(gender, ageMonths);
    }
}
