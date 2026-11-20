package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** EDU-97: Hiệu trưởng mở lại hạn xử lý một lượt REVIEW đã bị khóa. */
public record YearPlanReviewDeadlineReopenRequest(
        @NotBlank String note, @NotNull LocalDate deadline) {}
