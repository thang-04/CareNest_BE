package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Lưới món người duyệt đã sửa trên nháp gợi ý tuần: đủ mọi ngày phục vụ, mỗi ô ({@code slot}, vd. {@code LUNCH.2})
 * đúng một món thuộc ứng viên của ô. Dùng để chấm thử và để dùng nháp kèm chỉnh sửa (AI-02).
 */
public record MenuAiDraftGridRequest(@NotNull @Valid List<Day> days) {

    public record Day(
            @NotNull LocalDate date, @NotNull @Valid List<Item> items) {}

    public record Item(@NotBlank String slot, @NotNull Long dishId) {}
}
