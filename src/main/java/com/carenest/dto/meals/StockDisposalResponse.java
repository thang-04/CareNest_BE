package com.carenest.dto.meals;

import com.carenest.utils.enums.StockDisposalKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Phiếu xuất kho khô đã lập; {@code quantity} từng dòng là lượng đã xuất khỏi lô (số dương). */
public record StockDisposalResponse(
        Long id,
        String code,
        StockDisposalKind kind,
        Long campusId,
        LocalDate disposalDate,
        Long supplierId,
        String supplierName,
        String reason,
        List<DisposalLine> lines,
        Long createdBy,
        Instant createdAt) {

    public record DisposalLine(
            Long lotId, String lotNo, StockFoodRef food, LocalDate expiryDate, BigDecimal quantity) {}
}
