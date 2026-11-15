package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HeightForAgeStatus;
import com.carenest.utils.enums.WeightForAgeStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WhoWeightHeightForAgeTest {

    @ParameterizedTest
    @DisplayName("HLT-20: −2SD, −3SD, trung vị tính từ L, M, S khớp bảng z-score WHO đã công bố")
    @CsvSource({
        // chỉ số, giới tính, tháng, −3SD, −2SD, trung vị, sai số theo số chữ số WHO in (2006: 1 chữ số; 2007: 3)
        "WA, MALE, 24, 8.6, 9.7, 12.2, 0.05",
        "WA, MALE, 36, 10.0, 11.3, 14.3, 0.05",
        "WA, FEMALE, 48, 10.9, 12.3, 16.1, 0.05",
        "WA, MALE, 61, 12.718, 14.367, 18.506, 0.001",
        "WA, FEMALE, 70, 13.270, 15.038, 19.818, 0.001",
        "HA, MALE, 12, 68.6, 71.0, 75.7, 0.05",
        "HA, MALE, 24, 78.0, 81.0, 87.1, 0.05",
        "HA, FEMALE, 48, 89.8, 94.1, 102.7, 0.05",
        "HA, MALE, 61, 96.490, 101.082, 110.265, 0.001",
        "HA, FEMALE, 70, 98.982, 104.040, 114.156, 0.001"
    })
    void tablesMatchPublishedWhoValues(
            String index, Gender gender, int months, double sd3neg, double sd2neg, double median, double tolerance) {
        double[] lms = lms(index, gender, months);
        assertThat(WhoLmsTable.valueAt(-3, lms[0], lms[1], lms[2])).isCloseTo(sd3neg, within(tolerance));
        assertThat(WhoLmsTable.valueAt(-2, lms[0], lms[1], lms[2])).isCloseTo(sd2neg, within(tolerance));
        assertThat(WhoLmsTable.valueAt(0, lms[0], lms[1], lms[2])).isCloseTo(median, within(tolerance));
    }

    @Test
    @DisplayName("HLT-20: ghép bảng — W/A 2006 đến 60 tháng, 2007 từ 61; H/A đo nằm đến 23 tháng, đo đứng từ 24")
    void tablesSpliceAtTheRightMonths() {
        assertThat(WhoWeightForAge.lms(Gender.MALE, 60)[1]).isEqualTo(18.3366);
        assertThat(WhoWeightForAge.lms(Gender.MALE, 61)[1]).isEqualTo(18.5057);
        assertThat(WhoHeightForAge.lms(Gender.MALE, 23)[1]).isEqualTo(86.941);
        assertThat(WhoHeightForAge.lms(Gender.MALE, 24)[1]).isEqualTo(87.1161);
        assertThat(WhoHeightForAge.lms(Gender.FEMALE, 61)[1]).isEqualTo(109.6016);
        // Chiều cao theo tuổi có L = 1 ở mọi tháng
        assertThat(WhoHeightForAge.lms(Gender.FEMALE, 84)[0]).isEqualTo(1.0);
    }

    @ParameterizedTest
    @DisplayName("HLT-20: trung vị WHO ⇒ z = 0, bình thường")
    @CsvSource({"MALE, 0", "FEMALE, 30", "MALE, 60", "FEMALE, 61", "MALE, 84"})
    void medianIsZero(Gender gender, int months) {
        WhoWeightForAge.Result weight =
                WhoWeightForAge.classify(gender, months, BigDecimal.valueOf(WhoWeightForAge.lms(gender, months)[1]));
        assertThat(weight.zScore()).isEqualByComparingTo("0.00");
        assertThat(weight.status()).isEqualTo(WeightForAgeStatus.NORMAL);
        WhoHeightForAge.Result height =
                WhoHeightForAge.classify(gender, months, BigDecimal.valueOf(WhoHeightForAge.lms(gender, months)[1]));
        assertThat(height.zScore()).isEqualByComparingTo("0.00");
        assertThat(height.status()).isEqualTo(HeightForAgeStatus.NORMAL);
    }

    @ParameterizedTest
    @DisplayName("HLT-20: xếp mức theo z đã làm tròn 2 chữ số; −2,00 còn bình thường, −3,00 còn nhẹ cân/thấp còi")
    @CsvSource({
        // z thô, z hiển thị: −2,004 và −3,004 hiện −2,00, −3,00 nên vẫn ở mức trong (so z thô thì sai mức)
        "-1.99, -1.99, NORMAL, NORMAL",
        "-2.00, -2.00, NORMAL, NORMAL",
        "-2.004, -2.00, NORMAL, NORMAL",
        "-2.01, -2.01, UNDERWEIGHT, STUNTED",
        "-3.00, -3.00, UNDERWEIGHT, STUNTED",
        "-3.004, -3.00, UNDERWEIGHT, STUNTED",
        "-3.01, -3.01, SEVERELY_UNDERWEIGHT, SEVERELY_STUNTED",
        "2.50, 2.50, NORMAL, NORMAL"
    })
    void classifiesByRoundedZ(
            double z, String shown, WeightForAgeStatus weightStatus, HeightForAgeStatus heightStatus) {
        double[] w = WhoWeightForAge.lms(Gender.FEMALE, 40);
        WhoWeightForAge.Result weight =
                WhoWeightForAge.classify(Gender.FEMALE, 40, BigDecimal.valueOf(weightAt(z, w[0], w[1], w[2])));
        assertThat(weight.zScore()).isEqualByComparingTo(shown);
        assertThat(weight.status()).isEqualTo(weightStatus);
        double[] h = WhoHeightForAge.lms(Gender.FEMALE, 40);
        WhoHeightForAge.Result height = WhoHeightForAge.classify(
                Gender.FEMALE, 40, BigDecimal.valueOf(WhoLmsTable.valueAt(z, h[0], h[1], h[2])));
        assertThat(height.zScore()).isEqualByComparingTo(shown);
        assertThat(height.status()).isEqualTo(heightStatus);
    }

    @Test
    @DisplayName(
            "HLT-20: cân nặng ngoài −3SD tính theo giới hạn đuôi WHO (công thức thường cho z khác); chiều cao L = 1")
    void restrictedTailsOnlyForWeight() {
        double[] w = WhoWeightForAge.lms(Gender.MALE, 30);
        double sd3 = WhoLmsTable.valueAt(-3, w[0], w[1], w[2]);
        double sd2 = WhoLmsTable.valueAt(-2, w[0], w[1], w[2]);
        double light = sd3 - (sd2 - sd3);
        WhoWeightForAge.Result weight = WhoWeightForAge.classify(Gender.MALE, 30, BigDecimal.valueOf(light));
        assertThat(weight.zScore()).isEqualByComparingTo("-4.00");
        assertThat(weight.status()).isEqualTo(WeightForAgeStatus.SEVERELY_UNDERWEIGHT);
        // Cùng cân nặng, công thức LMS thường (L ≠ 1) cho z thấp hơn hẳn −4: phân biệt được hai cách
        assertThat(WhoLmsTable.zScore(light, w[0], w[1], w[2], false)).isLessThan(-4.05);

        // Chiều cao/tuổi có L = 1 nên z tuyến tính: M·(1 − 4S) ⇒ −4,00
        double[] h = WhoHeightForAge.lms(Gender.MALE, 30);
        assertThat(h[0]).isEqualTo(1.0);
        WhoHeightForAge.Result height =
                WhoHeightForAge.classify(Gender.MALE, 30, BigDecimal.valueOf(h[1] * (1 - 4 * h[2])));
        assertThat(height.zScore()).isEqualByComparingTo("-4.00");
        assertThat(height.status()).isEqualTo(HeightForAgeStatus.SEVERELY_STUNTED);
    }

    @Test
    @DisplayName("HLT-20: không xếp mức khi thiếu giới tính, số đo hoặc tuổi ngoài 0–84 tháng")
    void missingDataIsNotClassified() {
        BigDecimal weight = new BigDecimal("15.0");
        BigDecimal height = new BigDecimal("100.0");
        assertThat(WhoWeightForAge.classify(Gender.MALE, 85, weight)).isNull();
        assertThat(WhoWeightForAge.classify(Gender.MALE, -1, weight)).isNull();
        assertThat(WhoWeightForAge.classify(null, 40, weight)).isNull();
        assertThat(WhoWeightForAge.classify(Gender.MALE, 40, null)).isNull();
        assertThat(WhoWeightForAge.classify(Gender.MALE, 40, BigDecimal.ZERO)).isNull();
        assertThat(WhoHeightForAge.classify(Gender.FEMALE, 85, height)).isNull();
        assertThat(WhoHeightForAge.classify(Gender.FEMALE, -1, height)).isNull();
        assertThat(WhoHeightForAge.classify(null, 40, height)).isNull();
        assertThat(WhoHeightForAge.classify(Gender.FEMALE, 40, null)).isNull();
        assertThat(WhoHeightForAge.classify(Gender.FEMALE, 40, BigDecimal.ZERO)).isNull();
    }

    private static double[] lms(String index, Gender gender, int months) {
        return "WA".equals(index) ? WhoWeightForAge.lms(gender, months) : WhoHeightForAge.lms(gender, months);
    }

    // Cân nặng tại z, gồm đuôi giới hạn ngoài ±3SD (nghịch đảo cách WHO tính z)
    private static double weightAt(double z, double l, double m, double s) {
        if (z < -3) {
            double sd3 = WhoLmsTable.valueAt(-3, l, m, s);
            return sd3 + (z + 3) * (WhoLmsTable.valueAt(-2, l, m, s) - sd3);
        }
        return WhoLmsTable.valueAt(z, l, m, s);
    }
}
