package com.carenest.entity.meals;

import com.carenest.utils.enums.StockReceiptStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Phiếu nhập kho khô của một điểm trường (NUT-11): nháp sửa được, xác nhận tạo lô, hủy bằng bút toán đảo. */
@Getter
@Entity
@Table(name = "stock_receipts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockReceiptStatus status = StockReceiptStatus.DRAFT;

    @Column(nullable = false, length = 500)
    private String note = "";

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<StockReceiptLine> lines = new ArrayList<>();

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StockReceipt draft(Long campusId, Long createdBy) {
        StockReceipt receipt = new StockReceipt();
        receipt.campusId = campusId;
        receipt.createdBy = createdBy;
        receipt.updatedBy = createdBy;
        return receipt;
    }

    public boolean isDraft() {
        return status == StockReceiptStatus.DRAFT;
    }

    /** Ghi đè thông tin và toàn bộ dòng của phiếu nháp. */
    public void changeDraft(
            Long supplierId, LocalDate receivedDate, String note, List<StockReceiptLine> newLines, Long updatedBy) {
        this.supplierId = supplierId;
        this.receivedDate = receivedDate;
        this.note = note;
        this.updatedBy = updatedBy;
        lines.clear();
        newLines.forEach(line -> {
            line.attachTo(this);
            lines.add(line);
        });
    }

    public void confirm(Long userId, Instant at) {
        status = StockReceiptStatus.CONFIRMED;
        confirmedBy = userId;
        confirmedAt = at;
        updatedBy = userId;
    }

    public void cancel(Long userId, Instant at, String reason) {
        status = StockReceiptStatus.CANCELLED;
        cancelledBy = userId;
        cancelledAt = at;
        cancelReason = reason;
        updatedBy = userId;
    }
}
