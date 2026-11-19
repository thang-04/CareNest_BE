package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.SQLRestriction;

/**
 * Lô kho khô (NUT-11): chính là dòng phiếu nhập sau khi phiếu được xác nhận, không lưu bảng riêng. Chỉ đọc; tồn = tổng
 * bút toán của lô ({@code stock_movements.lot_id} = id dòng phiếu). Phiếu nháp chưa có lô; phiếu đã hủy giữ lô với tồn
 * 0 để tra lịch sử.
 */
@Getter
@Entity
@Immutable
@Table(name = "stock_receipt_lines")
@SQLRestriction("receipt_id IN (SELECT r.id FROM stock_receipts r WHERE r.status <> 'DRAFT')")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLot {

    @Id
    private Long id;

    @Formula("(SELECT r.campus_id FROM stock_receipts r WHERE r.id = receipt_id)")
    private Long campusId;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Formula("(SELECT r.supplier_id FROM stock_receipts r WHERE r.id = receipt_id)")
    private Long supplierId;

    @Column(name = "lot_no", nullable = false, length = 50)
    private String lotNo;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Formula("(SELECT r.received_date FROM stock_receipts r WHERE r.id = receipt_id)")
    private LocalDate receivedDate;
}
