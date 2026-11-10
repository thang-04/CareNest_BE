package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Ghi hoặc sửa bản lưu mẫu (NUT-21). {@code sampledAt} bỏ trống ⇒ lúc ghi. */
public record FoodSampleRequest(
        @NotNull LocalDate date,
        @NotNull MealSession session,
        @NotEmpty List<@NotNull Long> dishIds,
        Instant sampledAt,
        @Size(max = 500) String note) {}
