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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Nhu cầu kho khô của điểm trường × ngày × thực phẩm theo suất đã xác nhận (NUT-11, P-38). {@code shortfallQty} là
 * phần chưa trừ được vì thiếu kho; đã trừ = required − shortfall.
 */
@Getter
@Entity
@Table(name = "stock_daily_demands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDailyDemand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "required_qty", nullable = false, precision = 14, scale = 2)
    private BigDecimal requiredQty = BigDecimal.ZERO;

    @Column(name = "shortfall_qty", nullable = false, precision = 14, scale = 2)
    private BigDecimal shortfallQty = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StockDailyDemand empty(Long campusId, LocalDate mealDate, Long foodId) {
        StockDailyDemand demand = new StockDailyDemand();
        demand.campusId = campusId;
        demand.mealDate = mealDate;
        demand.foodId = foodId;
        return demand;
    }

    public BigDecimal deductedQty() {
        return requiredQty.subtract(shortfallQty);
    }

    public void change(BigDecimal requiredQty, BigDecimal shortfallQty) {
        this.requiredQty = requiredQty;
        this.shortfallQty = shortfallQty;
    }
}
