package com.carenest.dto.meals;

import com.carenest.utils.enums.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng lịch sử nhập/trừ: số dương vào lô, âm ra lô; {@code mealDate} = ngày ăn của bút toán trừ. {@code
 * correctionFor} có giá trị khi bút toán điều chỉnh cho ngày ăn đã ký M1 kho khô: {@code mealDate} là ngày thực hiện.
 */
public record StockMovementResponse(
        Long id,
        Long campusId,
        Long lotId,
        String lotNo,
        StockFoodRef food,
        StockMovementType type,
        BigDecimal quantity,
        LocalDate mealDate,
        LocalDate correctionFor,
        Long receiptId,
        Long disposalId,
        String reason,
        Long createdBy,
        Instant createdAt) {}
