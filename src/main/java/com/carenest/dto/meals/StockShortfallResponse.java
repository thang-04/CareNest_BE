package com.carenest.dto.meals;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Phần thiếu chờ bổ sung: nhu cầu theo suất đã xác nhận chưa trừ được vì thiếu kho (NUT-11, P-38). */
public record StockShortfallResponse(
        Long campusId, LocalDate mealDate, StockFoodRef food, BigDecimal requiredQty, BigDecimal shortfallQty) {}
