package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Thống kê định lượng, dinh dưỡng một ngày thực đơn của một nhóm tuổi (không lưu). {@code date}: ngày lấy định mức hiệu
 * lực và số suất (trống = hôm nay); {@code children} bỏ trống ⇒ BE lấy suất đã xác nhận của điểm trường–ngày, không có
 * thì sĩ số đang học của nhóm tuổi.
 */
public record MenuStatisticsRequest(
        @NotNull Long ageGroupId,
        LocalDate date,
        Long campusId,
        @Min(0) @Max(2000) Integer children,
        @NotNull @Size(max = 60) List<@NotNull @Valid Item> items) {

    public record Item(
            @NotNull MealSession session, @NotNull Long dishId) {}
}
