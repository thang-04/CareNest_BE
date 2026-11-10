package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthBmiStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit test thuần (không Spring, không DB): mức BMI theo tuổi chuẩn WHO của kỳ cân đo. */
class WhoBmiForAgeTest {

    // Chiều cao 100 cm ⇒ cân nặng (kg) bằng BMI
    private static final BigDecimal ONE_METER = new BigDecimal("100");

    @ParameterizedTest
    @DisplayName("HLT-18: ngưỡng WHO 2006 đến 60 tháng, WHO 2007 từ 61 tháng")
    @CsvSource({
        "MALE, 46, -3.5, SEVERE_THINNESS",
        "MALE, 46, -2.5, THINNESS",
        "MALE, 46, 0, NORMAL",
        "MALE, 46, 0.9, NORMAL",
        "MALE, 46, 1.5, OVERWEIGHT_RISK",
        "MALE, 46, 2.5, OVERWEIGHT",
        "MALE, 46, 3.5, OBESE",
        "FEMALE, 30, -2.5, THINNESS",
        "FEMALE, 30, 1.5, OVERWEIGHT_RISK",
        "MALE, 60, 1.5, OVERWEIGHT_RISK",
        "MALE, 61, 1.5, OVERWEIGHT",
        "FEMALE, 65, -3.5, SEVERE_THINNESS",
        "FEMALE, 65, -2.5, THINNESS",
        "FEMALE, 65, 1.5, OVERWEIGHT",
        "FEMALE, 65, 2.5, OBESE",
        "FEMALE, 84, 0, NORMAL",
        "MALE, 0, 0, NORMAL"
    })
    void classifiesByWhoCutOffs(Gender gender, int months, double z, HealthBmiStatus expected) {
        assertThat(WhoBmiForAge.classify(gender, months, ONE_METER, bmiAt(gender, months, z))
                        .status())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("HLT-03: bảng L, M, S khớp bảng z-score WHO đã công bố")
    void tableMatchesPublishedWhoValues() {
        // Bé trai 61 tháng (WHO 2007): +2SD = 18,259; −2SD = 13,031
        double[] boy61 = WhoBmiForAge.lms(Gender.MALE, 61);
        assertThat(WhoBmiForAge.valueAt(2, boy61[0], boy61[1], boy61[2])).isCloseTo(18.259, within(0.001));
        assertThat(WhoBmiForAge.valueAt(-2, boy61[0], boy61[1], boy61[2])).isCloseTo(13.031, within(0.001));
        // Bé gái 65 tháng (WHO 2007): +1SD = 16,911
        double[] girl65 = WhoBmiForAge.lms(Gender.FEMALE, 65);
        assertThat(WhoBmiForAge.valueAt(1, girl65[0], girl65[1], girl65[2])).isCloseTo(16.911, within(0.001));
        // 24 tháng dùng bảng 2–5 tuổi (đo đứng), 23 tháng bảng 0–2 tuổi (đo nằm)
        assertThat(WhoBmiForAge.lms(Gender.MALE, 24)[1]).isEqualTo(16.0189);
        assertThat(WhoBmiForAge.lms(Gender.MALE, 23)[1]).isEqualTo(15.7852);
        // Bé trai 24 tháng: +2SD bảng WHO làm tròn 18,9
        double[] boy24 = WhoBmiForAge.lms(Gender.MALE, 24);
        assertThat(WhoBmiForAge.valueAt(2, boy24[0], boy24[1], boy24[2])).isCloseTo(18.9, within(0.05));
    }

    @Test
    @DisplayName("HLT-18: ngoài ±3SD tính z-score theo cách giới hạn đuôi của WHO")
    void restrictedTailsAboveThreeSd() {
        double[] lms = WhoBmiForAge.lms(Gender.FEMALE, 30);
        double sd3 = WhoBmiForAge.valueAt(3, lms[0], lms[1], lms[2]);
        double sd2 = WhoBmiForAge.valueAt(2, lms[0], lms[1], lms[2]);
        WhoBmiForAge.Result r =
                WhoBmiForAge.classify(Gender.FEMALE, 30, ONE_METER, BigDecimal.valueOf(sd3 + (sd3 - sd2)));
        assertThat(r.zScore()).isEqualByComparingTo("4.00");
        assertThat(r.status()).isEqualTo(HealthBmiStatus.OBESE);
        double sd3neg = WhoBmiForAge.valueAt(-3, lms[0], lms[1], lms[2]);
        double sd2neg = WhoBmiForAge.valueAt(-2, lms[0], lms[1], lms[2]);
        assertThat(WhoBmiForAge.classify(Gender.FEMALE, 30, ONE_METER, BigDecimal.valueOf(sd3neg - (sd2neg - sd3neg)))
                        .zScore())
                .isEqualByComparingTo("-4.00");
    }

    @ParameterizedTest
    @DisplayName("HLT-18: xếp mức theo z-score đã làm tròn 2 chữ số; đúng ngưỡng còn ở mức trong")
    @CsvSource({
        "46, 1.000, NORMAL, 1.00",
        "46, 1.004, NORMAL, 1.00",
        "46, 1.006, OVERWEIGHT_RISK, 1.01",
        "46, 2.000, OVERWEIGHT_RISK, 2.00",
        "46, 2.006, OVERWEIGHT, 2.01",
        "46, 3.000, OVERWEIGHT, 3.00",
        "46, 3.006, OBESE, 3.01",
        "46, -2.000, NORMAL, -2.00",
        "46, -2.004, NORMAL, -2.00",
        "46, -2.006, THINNESS, -2.01",
        "46, -3.000, THINNESS, -3.00",
        "70, 2.004, OVERWEIGHT, 2.00",
        "70, 2.006, OBESE, 2.01"
    })
    void classifiesOnRoundedZScore(int months, double z, HealthBmiStatus expected, String shown) {
        WhoBmiForAge.Result r = WhoBmiForAge.classify(Gender.MALE, months, ONE_METER, bmiAt(Gender.MALE, months, z));
        assertThat(r.zScore()).isEqualByComparingTo(shown);
        assertThat(r.zScore().scale()).isEqualTo(2);
        assertThat(r.status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("HLT-18: không xếp mức khi thiếu dữ liệu hoặc ngoài 0–84 tháng")
    void noStatusWithoutDataOrOutsideAgeRange() {
        BigDecimal weight = new BigDecimal("15.0");
        assertThat(WhoBmiForAge.classify(Gender.MALE, 85, ONE_METER, weight)).isNull();
        assertThat(WhoBmiForAge.classify(Gender.MALE, -1, ONE_METER, weight)).isNull();
        assertThat(WhoBmiForAge.classify(null, 40, ONE_METER, weight)).isNull();
        assertThat(WhoBmiForAge.classify(Gender.MALE, 40, null, weight)).isNull();
        assertThat(WhoBmiForAge.classify(Gender.MALE, 40, ONE_METER, null)).isNull();
        assertThat(WhoBmiForAge.classify(Gender.MALE, 40, BigDecimal.ZERO, weight))
                .isNull();
    }

    // BMI tại z-score theo công thức LMS của bảng, không giới hạn đuôi (ngoài ±3SD vẫn đúng phía ngưỡng)
    private static BigDecimal bmiAt(Gender gender, int months, double z) {
        double[] lms = WhoBmiForAge.lms(gender, months);
        return BigDecimal.valueOf(WhoBmiForAge.valueAt(z, lms[0], lms[1], lms[2]));
    }
}
