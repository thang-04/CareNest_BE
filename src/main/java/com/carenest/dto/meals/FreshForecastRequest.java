package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Bản dự trù hàng tươi của điểm trường theo kỳ (≤ 31 ngày). {@code counts} null ⇒ điền sẵn sĩ số đang học; nhóm tuổi
 * không gửi cũng lấy sĩ số đang học.
 */
public record FreshForecastRequest(
        @NotNull Long campusId,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate periodTo,
        @Size(max = 500) String note,
        @Valid List<FreshForecastCountInput> counts) {

    public record FreshForecastCountInput(
            @NotNull Long ageGroupId,
            @NotNull @PositiveOrZero @Max(10000) Integer expectedCount) {}
}
