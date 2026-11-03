package com.carenest.dto.meals;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lô kho khô; {@code remaining} = tổng bút toán của lô (NUT-11). */
public record StockLotResponse(
        Long id,
        Long campusId,
        StockFoodRef food,
        String lotNo,
        LocalDate expiryDate,
        LocalDate receivedDate,
        Long supplierId,
        String supplierName,
        BigDecimal quantity,
        BigDecimal remaining,
        BigDecimal unitPrice) {}
