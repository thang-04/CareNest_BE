package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Ghi đè cả lưới kế hoạch tuần nháp: ngày × bữa × khối → món (NUT-13). */
public record MealPlanRequest(@NotNull @Valid List<DayInput> days) {

    public record DayInput(
            @NotNull LocalDate date,
            boolean off,
            @Size(max = 300) String note,
            @NotNull @Valid List<ItemInput> items) {}

    public record ItemInput(
            @NotNull MealSession session,
            @NotNull MenuBlock block,
            @NotNull Long dishId) {}
}
