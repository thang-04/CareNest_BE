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
public record FreshCheckResponse(
        Long campusId, LocalDate date, boolean canManage, List<FreshCheckRound> rounds, DryIntake dry) {

    /**
     * Hàng khô giao trong ngày (NUT-11, NUT-12): {@code orders} theo bản dự trù có kỳ chứa ngày ({@code forecastId} null ⇒
     * không có, mọi dòng là ngoài đơn); {@code receipts} các đợt đã kiểm nhận trong ngày.
     */
    public record DryIntake(
            Long forecastId,
            LocalDate periodFrom,
            LocalDate periodTo,
            List<DryOrderStatus> orders,
            List<StockReceiptResponse> receipts) {}

    /**
     * {@code ordered} null ⇒ thực phẩm nhận ngoài đơn; {@code received}: tổng dòng đạt của các phiếu đã xác nhận gắn bản
     * dự trù, tính cả hôm nay.
     */
    public record DryOrderStatus(StockFoodRef food, BigDecimal ordered, BigDecimal received) {}

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

    /** Nhà cung cấp của lần giao; không có số CCCD (dữ liệu cá nhân). Null khi chưa ghi hoặc chưa chọn. */
    public record SupplierInfo(Long id, String name, String phone, String address, String deliveryPerson) {}

    public record FreshCheckLine(
            StockFoodRef food, BigDecimal expectedQty, BigDecimal receivedQty, FreshCheckResult result, String note) {}
}
