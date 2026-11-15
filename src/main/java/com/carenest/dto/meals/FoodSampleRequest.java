package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ghi hoặc sửa bản lưu mẫu (NUT-21). {@code sampledAt} bỏ trống ⇒ lúc ghi. {@code dishDetails}: khối lượng, dụng cụ
 * chứa, nhiệt độ của từng món (Mẫu 5), mỗi món tối đa một dòng và phải nằm trong {@code dishIds}; bỏ trống (null) khi
 * sửa ⇒ giữ ghi chú đã có.
 */
public record FoodSampleRequest(
        @NotNull LocalDate date,
        @NotNull MealSession session,
        @NotEmpty List<@NotNull Long> dishIds,
        Instant sampledAt,
        @Size(max = 500) String note,
        @Valid @Size(max = 50) List<@NotNull DishDetailInput> dishDetails) {

    /** Client chưa gửi ghi chú từng món. */
    public FoodSampleRequest(LocalDate date, MealSession session, List<Long> dishIds, Instant sampledAt, String note) {
        this(date, session, dishIds, sampledAt, note, null);
    }

    public record DishDetailInput(
            @NotNull Long dishId,
            @Size(max = 50) String weight,
            @Size(max = 100) String container,
            @Size(max = 50) String temperature) {}
}
