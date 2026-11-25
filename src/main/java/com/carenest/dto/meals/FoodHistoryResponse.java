package com.carenest.dto.meals;

import java.time.Instant;

/**
 * Một dòng lịch sử thực phẩm: người, lúc, hành động, trường đổi và giá trị trước/sau. Đổi giá và nhà cung cấp (action
 * PRICE_ADJUSTED) kèm ghi chú ngày hiệu lực và lý do.
 */
public record FoodHistoryResponse(
        Long id,
        Long userId,
        String userName,
        Instant at,
        String action,
        String field,
        String oldValue,
        String newValue,
        String note) {}
