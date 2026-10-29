package com.carenest.dto.observation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/** Mở lại lớp / ngày đánh giá đã khóa tới validUntil (sau hiện tại, không quá ngày kết thúc năm học). */
public record AssessmentDayUnlockRequest(
        @NotNull Long classId,
        @NotNull LocalDate date,
        @NotNull Instant validUntil,
        @NotBlank @Size(max = 300) String reason) {}
