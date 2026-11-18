package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Sửa ngoại lệ một ngày của thực đơn tuần đã công bố (NUT-08, P-39): món, ghi chú mới của ngày và lý do bắt buộc. */
public record MealPlanAmendRequest(
        @Size(max = 300) String note,
        @NotNull @Valid List<MealPlanRequest.ItemInput> items,
        @Size(max = 500) String reason) {}
