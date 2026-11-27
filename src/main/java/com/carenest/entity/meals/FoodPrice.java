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
import org.hibernate.annotations.UpdateTimestamp;

/** Một mức giá của thực phẩm (NUT-27): giá theo đơn vị mua và NCC áp dụng từ {@code effectiveFrom}. */
@Getter
@Entity
@Table(name = "food_prices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false, length = 300)
    private String reason = "";

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

    public static FoodPrice of(
            Long foodId, Long supplierId, BigDecimal price, LocalDate effectiveFrom, String reason, Long userId) {
        FoodPrice row = new FoodPrice();
        row.foodId = foodId;
        row.createdBy = userId;
        row.change(supplierId, price, effectiveFrom, reason, userId);
        return row;
    }

    public void change(Long supplierId, BigDecimal price, LocalDate effectiveFrom, String reason, Long userId) {
        this.supplierId = supplierId;
        this.price = price;
        this.effectiveFrom = effectiveFrom;
        this.reason = reason == null ? "" : reason;
        this.updatedBy = userId;
    }
}
