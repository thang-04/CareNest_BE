package com.carenest.dto.meals;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Request lưu mẫu có hai constructor: JSON cũ (không có ghi chú từng món) và mới đều phải đọc được. */
class FoodSampleRequestJsonTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("NUT-21: JSON cũ không có dishDetails ⇒ null (giữ ghi chú khi sửa)")
    void legacyJsonHasNoDetails() {
        FoodSampleRequest request = MAPPER.readValue(
                "{\"date\":\"2026-11-09\",\"session\":\"LUNCH\",\"dishIds\":[1,2],\"note\":\"x\"}",
                FoodSampleRequest.class);

        assertThat(request.date()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(request.session()).isEqualTo(MealSession.LUNCH);
        assertThat(request.dishDetails()).isNull();
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("NUT-21: JSON mới đọc đủ ba trường từng món; quá dài bị từ chối")
    void newJsonReadsDetailsAndValidatesLength() {
        FoodSampleRequest request = MAPPER.readValue(
                "{\"date\":\"2026-11-09\",\"session\":\"LUNCH\",\"dishIds\":[1],\"dishDetails\":"
                        + "[{\"dishId\":1,\"weight\":\"100 g\",\"container\":\"Hộp nhựa\",\"temperature\":\"2-8 °C\"}]}",
                FoodSampleRequest.class);

        assertThat(request.dishDetails()).singleElement().satisfies(d -> {
            assertThat(d.dishId()).isEqualTo(1L);
            assertThat(d.weight()).isEqualTo("100 g");
            assertThat(d.container()).isEqualTo("Hộp nhựa");
            assertThat(d.temperature()).isEqualTo("2-8 °C");
        });
        assertThat(VALIDATOR.validate(request)).isEmpty();

        FoodSampleRequest tooLong = new FoodSampleRequest(
                LocalDate.of(2026, 11, 9),
                MealSession.LUNCH,
                java.util.List.of(1L),
                null,
                null,
                java.util.List.of(new FoodSampleRequest.DishDetailInput(1L, "x".repeat(51), null, null)));
        assertThat(VALIDATOR.validate(tooLong)).hasSize(1);
    }
}
