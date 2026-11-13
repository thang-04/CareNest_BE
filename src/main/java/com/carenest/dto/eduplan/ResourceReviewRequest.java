package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** EDU-84: duyệt / trả lại đề nghị lên kho trường; trả lại bắt buộc lý do. */
public record ResourceReviewRequest(
        @NotNull Boolean approve, @Size(max = 1000) String reason) {}
