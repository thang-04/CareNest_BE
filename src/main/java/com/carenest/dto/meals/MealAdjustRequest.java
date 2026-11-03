package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Điều chỉnh suất thay thế một lớp (NUT-02): session null = mọi bữa của lớp trong ngày; bắt buộc lý do (NUT-03).
 * normal: không sửa được — gửi null (giữ nguyên); khác số hiện tại ⇒ 409 MEAL_COUNT_NORMAL_NOT_EDITABLE.
 */
public record MealAdjustRequest(
        @NotNull Long campusId,
        @NotNull LocalDate date,
        MealSession session,
        @NotNull Long classId,
        @Min(0) @Max(200) Integer normal,
        @NotNull @Min(0) @Max(200) Integer substitute,
        @NotBlank @Size(max = 200) String note) {}
