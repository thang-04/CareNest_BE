package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;

/** HT đặt khung giờ đăng ảnh: bữa nào có trong danh sách thì được ghi đè, bữa không nêu giữ nguyên. */
public record MealPhotoWindowsRequest(@NotEmpty @Valid List<WindowInput> windows) {

    public record WindowInput(
            @NotNull MealSession session,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime) {}
}
