package com.carenest.dto.health;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Số đo trong kỳ cân đo. Tạo mới bắt buộc {@code campaignId} của kỳ đang diễn ra; sửa bỏ qua {@code campaignId}, bắt
 * buộc {@code reason} (HLT-13). Vòng đầu chỉ lưu khi trẻ chưa quá 60 tháng tại ngày đo.
 */
public record HealthMeasurementRequest(
        Long campaignId,
        @NotNull LocalDate date,

        @NotNull @DecimalMin("40.0") @DecimalMax("150.0") @Digits(integer = 3, fraction = 1)
        BigDecimal heightCm,

        @NotNull @DecimalMin("3.0") @DecimalMax("60.0") @Digits(integer = 2, fraction = 1)
        BigDecimal weightKg,

        @DecimalMin("30.0") @DecimalMax("60.0") @Digits(integer = 2, fraction = 1)
        BigDecimal headCircumferenceCm,

        @Size(max = 500) String note,
        @Size(max = 300) String reason) {}
