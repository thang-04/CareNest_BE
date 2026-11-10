package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một dòng phiếu nhập = một lô khi xác nhận. Số lượng theo đơn vị gốc (g/ml), giá theo đơn vị mua. */
@Getter
@Entity
@Table(name = "stock_receipt_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private StockReceipt receipt;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "lot_no", nullable = false, length = 50)
    private String lotNo;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public static StockReceiptLine of(
            Long foodId, String lotNo, LocalDate expiryDate, BigDecimal quantity, BigDecimal unitPrice) {
        StockReceiptLine line = new StockReceiptLine();
        line.foodId = foodId;
        line.lotNo = lotNo;
        line.expiryDate = expiryDate;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        return line;
    }

    void attachTo(StockReceipt receipt) {
        this.receipt = receipt;
    }
}
