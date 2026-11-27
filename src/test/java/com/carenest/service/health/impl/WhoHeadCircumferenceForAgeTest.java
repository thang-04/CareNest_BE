package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WhoHeadCircumferenceForAgeTest {

    @Test
    @DisplayName("Vòng đầu theo tuổi WHO 2006: khoảng −2SD..+2SD khớp số WHO in sẵn, theo giới tính và tháng tuổi")
    void rangeMatchesWhoPrintedSd() {
        assertRange(Gender.MALE, 0, "31.9", "37.0");
        assertRange(Gender.MALE, 12, "43.5", "48.6");
        assertRange(Gender.MALE, 24, "45.5", "51.0");
        assertRange(Gender.MALE, 60, "47.7", "53.7");
        assertRange(Gender.FEMALE, 0, "31.5", "36.2");
        assertRange(Gender.FEMALE, 24, "44.4", "50.0");
        assertRange(Gender.FEMALE, 60, "47.1", "52.8");
    }

    @Test
    @DisplayName("Ngoài 0–60 tháng hoặc thiếu giới tính ⇒ không có khoảng")
    void noRangeOutsideTable() {
        assertThat(WhoHeadCircumferenceForAge.range(Gender.MALE, 61)).isNull();
        assertThat(WhoHeadCircumferenceForAge.range(Gender.FEMALE, -1)).isNull();
        assertThat(WhoHeadCircumferenceForAge.range(null, 24)).isNull();
    }

    private static void assertRange(Gender gender, int month, String low, String high) {
        var range = WhoHeadCircumferenceForAge.range(gender, month);
        assertThat(range.low()).isEqualByComparingTo(low);
        assertThat(range.high()).isEqualByComparingTo(high);
    }
}
