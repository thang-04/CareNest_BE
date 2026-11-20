package com.carenest.dto.meals;

import com.carenest.utils.enums.StockReceiptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Phiếu nhập kho khô. {@code value} = số lượng quy ra đơn vị mua × giá; {@code canManage}: người xem có
 * {@code STOCK_MANAGE} ở điểm trường của phiếu.
 */
public record StockReceiptResponse(
        Long id,
        Long campusId,
        Long supplierId,
        String supplierName,
        LocalDate receivedDate,
        StockReceiptStatus status,
        String note,
        BigDecimal totalValue,
        List<ReceiptLine> lines,
        Long confirmedBy,
        Instant confirmedAt,
        Long cancelledBy,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        boolean canManage,
        Long forecastId,
        boolean fromIntake,
        List<RejectedLine> rejectedLines,
        // NUT-11: hủy được khi phiếu đã xác nhận, người xem quản lý được và mọi lô chưa bị trừ
        boolean canCancel) {

    /** Dòng kiểm nhận không đạt: không thành lô, {@code note} là biện pháp xử lý. */
    public record RejectedLine(
            StockFoodRef food, String lotNo, LocalDate expiryDate, BigDecimal quantity, String note) {}

    public record ReceiptLine(
            Long id,
            StockFoodRef food,
            String lotNo,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal value) {}
}
