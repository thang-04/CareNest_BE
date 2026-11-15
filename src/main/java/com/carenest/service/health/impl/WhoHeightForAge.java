package com.carenest.service.health.impl;

import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HeightForAgeStatus;
import java.math.BigDecimal;

/**
 * Chiều dài/chiều cao theo tuổi chuẩn WHO (HLT-03, HLT-20) từ {@code health/who-height-for-age.csv}: dưới 24 tháng bảng
 * đo nằm, 24–60 tháng đo đứng (WHO 2006), từ 61 tháng WHO 2007. L = 1 nên dùng công thức LMS thường, không giới hạn
 * đuôi. Hệ thống không ghi cách đo: trẻ dưới 24 tháng coi là đo nằm. Chỉ dùng cho báo cáo tăng trưởng.
 */
final class WhoHeightForAge {

    private static final WhoLmsTable TABLE = WhoLmsTable.load("/health/who-height-for-age.csv", "chiều cao theo tuổi");

    /** Mức và z-score làm tròn 2 chữ số. */
    record Result(HeightForAgeStatus status, BigDecimal zScore) {}

    private WhoHeightForAge() {}

    /** Null khi thiếu giới tính, chiều cao hoặc tuổi ngoài 0–84 tháng. */
    static Result classify(Gender gender, int ageMonths, BigDecimal heightCm) {
        if (heightCm == null) {
            return null;
        }
        BigDecimal z = TABLE.zScore(gender, ageMonths, heightCm.doubleValue(), false);
        if (z == null) {
            return null;
        }
        double v = z.doubleValue();
        HeightForAgeStatus status = v < -3
                ? HeightForAgeStatus.SEVERELY_STUNTED
                : v < -2 ? HeightForAgeStatus.STUNTED : HeightForAgeStatus.NORMAL;
        return new Result(status, z);
    }

    /** L, M, S của một giới tính tại tháng tuổi (để test đối chiếu bảng). */
    static double[] lms(Gender gender, int ageMonths) {
        return TABLE.lms(gender, ageMonths);
    }
}
