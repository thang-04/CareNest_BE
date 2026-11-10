package com.carenest.service.health.impl;

import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthBmiStatus;
import java.math.BigDecimal;

/**
 * Mức BMI theo tuổi theo chuẩn WHO (HLT-03, HLT-18) từ bảng L, M, S theo tháng tuổi tròn trong
 * {@code health/who-bmi-for-age.csv}: đến 60 tháng WHO 2006, từ 61 tháng WHO 2007. Z-score theo phương pháp LMS; ngoài
 * ±3SD tính theo cách giới hạn đuôi của WHO.
 */
final class WhoBmiForAge {

    // Từ 61 tháng theo bảng WHO 2007: thừa cân > +1SD, béo phì > +2SD (đến 60 tháng: > +2SD, > +3SD)
    private static final int REFERENCE_2007_FROM_MONTHS = 61;
    private static final WhoLmsTable TABLE = WhoLmsTable.load("/health/who-bmi-for-age.csv", "BMI theo tuổi");

    /** Mức và z-score làm tròn 2 chữ số của một lần đo. */
    record Result(HealthBmiStatus status, BigDecimal zScore) {}

    private WhoBmiForAge() {}

    /** Null khi thiếu giới tính, chiều cao, cân nặng hoặc tuổi ngoài 0–84 tháng. */
    static Result classify(Gender gender, int ageMonths, BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || weightKg == null || heightCm.signum() <= 0 || weightKg.signum() <= 0) {
            return null;
        }
        double heightM = heightCm.doubleValue() / 100;
        // Xếp mức theo z đã làm tròn 2 chữ số như phần mềm WHO, để mức luôn khớp z-score hiển thị
        BigDecimal z = TABLE.zScore(gender, ageMonths, weightKg.doubleValue() / (heightM * heightM), true);
        return z == null ? null : new Result(status(z.doubleValue(), ageMonths), z);
    }

    /** L, M, S của một giới tính tại tháng tuổi (để test đối chiếu bảng). */
    static double[] lms(Gender gender, int ageMonths) {
        return TABLE.lms(gender, ageMonths);
    }

    /** BMI tại z-score {@code z} (công thức LMS, không giới hạn đuôi). */
    static double valueAt(double z, double l, double m, double s) {
        return WhoLmsTable.valueAt(z, l, m, s);
    }

    private static HealthBmiStatus status(double z, int ageMonths) {
        if (z < -3) {
            return HealthBmiStatus.SEVERE_THINNESS;
        }
        if (z < -2) {
            return HealthBmiStatus.THINNESS;
        }
        if (z <= 1) {
            return HealthBmiStatus.NORMAL;
        }
        if (ageMonths >= REFERENCE_2007_FROM_MONTHS) {
            return z <= 2 ? HealthBmiStatus.OVERWEIGHT : HealthBmiStatus.OBESE;
        }
        if (z <= 2) {
            return HealthBmiStatus.OVERWEIGHT_RISK;
        }
        return z <= 3 ? HealthBmiStatus.OVERWEIGHT : HealthBmiStatus.OBESE;
    }
}
