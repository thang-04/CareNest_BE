package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Tạo/sửa đợt khám hoặc kỳ cân đo. {@code kind} trống ⇒ đợt khám; khi sửa bỏ qua {@code kind}, {@code campusId} (không
 * đổi sau khi tạo). Kỳ cân đo không có đơn vị khám ({@code examiner} bị bỏ).
 */
public record HealthCheckRequest(
        HealthCheckKind kind,
        @NotBlank @Size(max = 150) String name,
        @NotNull Long campusId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 150) String examiner,
        @Size(max = 500) String note) {}
