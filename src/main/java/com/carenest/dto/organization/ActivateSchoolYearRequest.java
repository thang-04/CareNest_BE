package com.carenest.dto.organization;

import jakarta.validation.constraints.NotBlank;

/** Người dùng gõ lại đúng tên năm học để xác nhận kích hoạt. */
public record ActivateSchoolYearRequest(@NotBlank String confirmName) {}
