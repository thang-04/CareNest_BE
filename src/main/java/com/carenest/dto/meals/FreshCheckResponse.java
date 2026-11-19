package com.carenest.dto.meals;

import com.carenest.utils.enums.FreshCheckResult;
import com.carenest.utils.enums.FreshCheckSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Kiểm nhận hàng tươi của điểm trường–ngày, luôn đủ hai lần. {@code canManage}: người xem có {@code STOCK_MANAGE} ở
 * điểm trường.
 */
public record FreshCheckResponse(Long campusId, LocalDate date, boolean canManage, List<FreshCheckRound> rounds) {

    /**
     * {@code available} false ⇒ chưa ghi được, lý do ở {@code unavailableReason}. Chưa ghi ({@code recorded} false) thì
     * {@code lines} là danh sách cần nhận với {@code result} null; đã ghi thì là kết quả đã lưu (lượng cần nhận lúc ghi).
     * {@code forecastId} chỉ có ở lần 1.
     */
    public record FreshCheckRound(
            int round,
            boolean available,
            String unavailableReason,
            FreshCheckSource source,
            Long forecastId,
            boolean recorded,
            Instant checkedAt,
            Long checkedBy,
            String note,
            List<FreshCheckLine> lines,
            SupplierInfo supplier) {}

    /** Nhà cung cấp; không có số CCCD (dữ liệu cá nhân). Cấp lần kiểm chỉ còn cho dữ liệu cũ. */
    public record SupplierInfo(Long id, String name, String phone, String address, String deliveryPerson) {}

    /** {@code supplier}: NCC đã ghi của dòng; chưa ghi thì là NCC của thực phẩm tại ngày kiểm (điền sẵn — NUT-12). */
    public record FreshCheckLine(
            StockFoodRef food,
            BigDecimal expectedQty,
            BigDecimal receivedQty,
            FreshCheckResult result,
            String note,
            SupplierInfo supplier) {}
}
