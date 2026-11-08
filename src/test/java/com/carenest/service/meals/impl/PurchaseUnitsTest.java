package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PurchaseUnitsTest {

    @ParameterizedTest
    @ValueSource(strings = {"hộp 100 g", "quả", "chai 1 kg", "Gói 400g", "bìa 300 g", "bó"})
    @DisplayName("NUT-12: hộp, quả, chai, gói… là đơn vị đếm được")
    void countableUnits(String unit) {
        assertThat(PurchaseUnits.countable(unit)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kg", "Kg", "g", "lít", "Lít", "l", "ml", "lạng", "kilogam", " "})
    @DisplayName("NUT-12: đơn vị cân đo (hoặc trống) mua lẻ được")
    void measureUnits(String unit) {
        assertThat(PurchaseUnits.countable(unit)).isFalse();
    }

    @Test
    @DisplayName("NUT-12: làm tròn lên bội số quy cách khi đếm được; kg giữ nguyên")
    void roundUp() {
        assertThat(PurchaseUnits.roundUp(new BigDecimal("1140"), "hộp 100 g", new BigDecimal("100")))
                .isEqualByComparingTo("1200");
        assertThat(PurchaseUnits.roundUp(new BigDecimal("1200"), "hộp 100 g", new BigDecimal("100")))
                .isEqualByComparingTo("1200");
        assertThat(PurchaseUnits.roundUp(new BigDecimal("48530.68"), "quả", new BigDecimal("55")))
                .isEqualByComparingTo("48565");
        assertThat(PurchaseUnits.roundUp(new BigDecimal("59300.5"), "kg", new BigDecimal("1000")))
                .isEqualByComparingTo("59300.5");
        assertThat(PurchaseUnits.roundUp(BigDecimal.ZERO, "hộp 100 g", new BigDecimal("100")))
                .isEqualByComparingTo("0");
    }
}
