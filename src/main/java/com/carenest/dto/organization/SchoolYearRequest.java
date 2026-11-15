package com.carenest.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** Tạo/sửa năm học; năm thứ hai = năm thứ nhất + 1 và thứ tự ngày được service kiểm tra. prepDate trống ⇒ 1 tháng trước ngày bắt đầu (CFG-YR-05). */
public record SchoolYearRequest(
        @NotBlank @Pattern(regexp = "\\s*\\d{4}-\\d{4}\\s*", message = "must have the form YYYY-YYYY")
        String name,

        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalDate prepDate) {

    public SchoolYearRequest(String name, LocalDate startDate, LocalDate endDate) {
        this(name, startDate, endDate, null);
    }
}
