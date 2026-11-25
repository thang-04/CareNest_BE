package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthExamReference.HealthExamHeadReference;
import com.carenest.utils.enums.Gender;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Vòng đầu theo tuổi của WHO Child Growth Standards 2006 ({@code health/who-head-circumference-for-age.csv}, 0–60 tháng
 * tròn, hai giới tính). Khoảng tham khảo của phiếu khám là −2SD..+2SD, làm tròn 1 chữ số như bảng WHO in (user chốt
 * 2026-11-22). Chỉ là gợi ý cho người nhập, bác sĩ chọn trạng thái.
 */
final class WhoHeadCircumferenceForAge {

    static final int MAX_MONTHS = 60;
    private static final WhoLmsTable TABLE =
            WhoLmsTable.load("/health/who-head-circumference-for-age.csv", "vòng đầu theo tuổi", MAX_MONTHS);

    private WhoHeadCircumferenceForAge() {}

    /** Khoảng −2SD..+2SD (cm); null khi thiếu giới tính hoặc tuổi ngoài 0–60 tháng. */
    static HealthExamHeadReference range(Gender gender, int ageMonths) {
        if (gender == null || ageMonths < 0 || ageMonths > MAX_MONTHS) {
            return null;
        }
        double[] lms = TABLE.lms(gender, ageMonths);
        return new HealthExamHeadReference(
                cm(WhoLmsTable.valueAt(-2, lms[0], lms[1], lms[2])),
                cm(WhoLmsTable.valueAt(2, lms[0], lms[1], lms[2])));
    }

    private static BigDecimal cm(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
