package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lưu cả bộ thực đơn mẫu: thông tin bộ và toàn bộ lưới 4 tuần × thứ 2–6 × bữa (thay lưới cũ). */
public record MenuTemplateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String seasonLabel,
        @NotNull MenuBlock block,
        @NotNull @Size(max = 600) List<@NotNull @Valid Item> items) {

    public record Item(
            @NotNull @Min(1) @Max(4) Integer weekNo,
            @NotNull @Min(1) @Max(5) Integer weekday,
            @NotNull MealSession session,
            @NotNull Long dishId) {}
}
