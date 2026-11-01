package com.carenest.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** Tạo/sửa năm học; năm thứ hai = năm thứ nhất + 1 và thứ tự ngày được service kiểm tra. */
public record SchoolYearRequest(
        @NotBlank @Pattern(regexp = "\\s*\\d{4}-\\d{4}\\s*", message = "must have the form YYYY-YYYY")
        String name,

        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {}
