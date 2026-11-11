package com.carenest.dto.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.HealthObservation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyHealthRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Thân nhiệt hợp lệ 20,0–45,0 °C (Cần chốt 11/11/2026)")
    void temperatureMustBeWithinRange() {
        assertThat(validator.validate(request("36.8"))).isEmpty();
        assertThat(validator.validate(request("20.0"))).isEmpty();
        assertThat(validator.validate(request("45.0"))).isEmpty();
        assertThat(validator.validate(request("19.9"))).isNotEmpty();
        assertThat(validator.validate(request("83.5")))
                .extracting(v -> v.getMessage())
                .contains("Thân nhiệt phải từ 20,0 đến 45,0 °C");
    }

    private static DailyHealthRequest request(String temperature) {
        return new DailyHealthRequest(
                "req-1",
                1L,
                2L,
                LocalDate.of(2026, 11, 11),
                new BigDecimal(temperature),
                HealthObservation.NO_CONCERN,
                null,
                null,
                null,
                null);
    }
}
