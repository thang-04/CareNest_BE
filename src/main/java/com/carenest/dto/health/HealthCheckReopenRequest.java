package com.carenest.dto.health;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mở lại đợt/kỳ đã đóng: bắt buộc lý do. */
public record HealthCheckReopenRequest(
        @NotBlank @Size(max = 300) String reason) {}
