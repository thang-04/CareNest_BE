package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Thống kê định lượng, dinh dưỡng một ngày thực đơn (không lưu). {@code ageGroupId} null = tất cả nhóm tuổi có món;
 * {@code items[].block} giới hạn món theo khối. {@code date} trống = hôm nay; {@code children} chỉ áp dụng cho một nhóm,
 * bỏ trống ⇒ BE lấy suất đã xác nhận của điểm trường–ngày, không có thì sĩ số đang học.
 */
public record MenuStatisticsRequest(
        Long ageGroupId,
        LocalDate date,
        Long campusId,
        @Min(0) @Max(2000) Integer children,
        @NotNull @Size(max = 60) List<@NotNull @Valid Item> items) {

    @Schema(name = "MenuStatisticsItem")
    public record Item(
            @NotNull MealSession session,
            MenuBlock block,
            @NotNull Long dishId) {
        public Item(MealSession session, Long dishId) {
            this(session, null, dishId);
        }
    }
}
