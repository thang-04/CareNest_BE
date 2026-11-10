package com.carenest.entity.meals;

import com.carenest.utils.enums.StockMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Bút toán kho khô của một lô: dương = vào lô, âm = ra lô. Chỉ thêm, không sửa, không xóa (NUT-11). */
@Getter
@Entity
@Table(name = "stock_movements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private StockMovementType movementType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "meal_date")
    private LocalDate mealDate;

    @Column(name = "receipt_id")
    private Long receiptId;

    @Column(nullable = false, length = 500)
    private String reason = "";

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static StockMovement of(
            StockLot lot,
            StockMovementType type,
            BigDecimal quantity,
            LocalDate mealDate,
            Long receiptId,
            String reason,
            Long createdBy) {
        StockMovement movement = new StockMovement();
        movement.lotId = lot.getId();
        movement.campusId = lot.getCampusId();
        movement.foodId = lot.getFoodId();
        movement.movementType = type;
        movement.quantity = quantity;
        movement.mealDate = mealDate;
        movement.receiptId = receiptId;
        movement.reason = reason == null ? "" : reason;
        movement.createdBy = createdBy;
        return movement;
    }
}
