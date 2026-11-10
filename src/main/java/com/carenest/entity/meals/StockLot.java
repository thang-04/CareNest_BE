package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Lô kho khô tạo khi xác nhận phiếu nhập (NUT-11). Tồn = tổng bút toán của lô, không lưu tại chỗ. */
@Getter
@Entity
@Table(name = "stock_lots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "receipt_line_id", nullable = false)
    private Long receiptLineId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "lot_no", nullable = false, length = 50)
    private String lotNo;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static StockLot fromReceipt(StockReceipt receipt, StockReceiptLine line) {
        StockLot lot = new StockLot();
        lot.campusId = receipt.getCampusId();
        lot.foodId = line.getFoodId();
        lot.receiptLineId = line.getId();
        lot.supplierId = receipt.getSupplierId();
        lot.lotNo = line.getLotNo();
        lot.expiryDate = line.getExpiryDate();
        lot.quantity = line.getQuantity();
        lot.unitPrice = line.getUnitPrice();
        lot.receivedDate = receipt.getReceivedDate();
        return lot;
    }
}
