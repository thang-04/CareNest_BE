package com.carenest.dto.meals;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Điều chỉnh giá (NUT-27): giá theo đơn vị mua, NCC áp dụng, ngày hiệu lực (≥ hôm nay), lý do/phụ lục. */
public record FoodPriceRequest(
        Long supplierId,
        @NotNull @DecimalMin("0") BigDecimal price,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 300) String reason) {}
