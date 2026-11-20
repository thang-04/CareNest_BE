package com.carenest.dto.meals;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dòng danh sách bản dự trù hàng tươi: {@code foodCount} = số thực phẩm tươi khác nhau trong kỳ; {@code deletable}
 * false khi bản đã dùng làm căn cứ kiểm nhận.
 */
public record FreshForecastSummaryResponse(
        Long id,
        Long campusId,
        LocalDate periodFrom,
        LocalDate periodTo,
        String note,
        int foodCount,
        Instant createdAt,
        boolean deletable) {}
