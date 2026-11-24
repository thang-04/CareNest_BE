package com.carenest.entity.meals;

import com.carenest.utils.enums.StockDisposalKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Phiếu xuất kho khô (NUT-29, NUT-30): chứng từ trả NCC hoặc xuất hủy. Chỉ thêm, không sửa; số lượng từng lô nằm ở các
 * bút toán {@code stock_movements.disposal_id} của phiếu.
 */
@Getter
@Entity
@Table(name = "stock_disposals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDisposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockDisposalKind kind;

    /** Chỉ có ở phiếu trả NCC. */
    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static StockDisposal of(
            String code,
            Long campusId,
            StockDisposalKind kind,
            Long supplierId,
            LocalDate disposalDate,
            String reason,
            Long createdBy) {
        StockDisposal disposal = new StockDisposal();
        disposal.code = code;
        disposal.campusId = campusId;
        disposal.kind = kind;
        disposal.supplierId = supplierId;
        disposal.disposalDate = disposalDate;
        disposal.reason = reason;
        disposal.createdBy = createdBy;
        return disposal;
    }
}
