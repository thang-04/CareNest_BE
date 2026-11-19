package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bản dự trù hàng tươi của điểm trường theo kỳ (≤ 31 ngày). {@code counts} null ⇒ điền sẵn sĩ số đang học; nhóm tuổi
 * không gửi cũng lấy sĩ số đang học. {@code dryOrder}: lượng hàng khô đặt cho kỳ PHT đã sửa (đơn vị gốc g/ml); null ⇒
 * lấy gợi ý phần thiếu tới hết kỳ.
 */
public record FreshForecastRequest(
        @NotNull Long campusId,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate periodTo,
        @Size(max = 500) String note,
        @Valid List<FreshForecastCountInput> counts,
        @Valid List<DryOrderInput> dryOrder) {

    public FreshForecastRequest(
            Long campusId,
            LocalDate periodFrom,
            LocalDate periodTo,
            String note,
            List<FreshForecastCountInput> counts) {
        this(campusId, periodFrom, periodTo, note, counts, null);
    }

    public record FreshForecastCountInput(
            @NotNull Long ageGroupId,
            @NotNull @PositiveOrZero @Max(10000) Integer expectedCount) {}

    public record DryOrderInput(
            @NotNull Long foodId,

            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2)
            BigDecimal quantity) {}
}
