package com.carenest.dto.health;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Số đo trong kỳ cân đo: chiều cao, cân nặng, ghi chú (HLT-01; vòng đầu chỉ đo ở phiếu khám). Ngày đo là ngày nhập, hệ
 * thống ghi; sửa giữ ngày đo (user chốt 2026-11-04). Tạo mới bắt buộc {@code campaignId} của kỳ đang diễn ra; sửa bỏ qua
 * {@code campaignId}, bắt buộc {@code reason} khi có thay đổi (HLT-13).
 */
public record HealthMeasurementRequest(
        Long campaignId,

        @NotNull @DecimalMin("40.0") @DecimalMax("150.0") @Digits(integer = 3, fraction = 1)
        BigDecimal heightCm,

        @NotNull @DecimalMin("3.0") @DecimalMax("60.0") @Digits(integer = 2, fraction = 1)
        BigDecimal weightKg,

        @Size(max = 500) String note,
        @Size(max = 300) String reason) {}
