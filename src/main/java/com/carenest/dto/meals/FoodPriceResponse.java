package com.carenest.dto.meals;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một mức giá; {@code editable} = ngày hiệu lực sau hôm nay nên còn sửa/xóa được (NUT-27). */
public record FoodPriceResponse(
        Long id,
        Long supplierId,
        String supplierName,
        BigDecimal price,
        LocalDate effectiveFrom,
        String reason,
        boolean editable) {}
