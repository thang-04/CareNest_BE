package com.carenest.dto.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/** Mở lại lớp/ngày đã khóa tới validUntil (sau hiện tại, không quá ngày kết thúc năm học). */
public record DayUnlockRequest(
        @NotNull Long classId,
        @NotNull LocalDate date,
        @NotNull Instant validUntil,
        @NotBlank @Size(max = 300) String reason) {}
