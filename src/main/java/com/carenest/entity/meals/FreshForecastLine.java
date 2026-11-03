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

/** Lượng một thực phẩm tươi cần mua cho một ngày của kỳ dự trù, theo đơn vị gốc (g/ml), đã tính thải bỏ. */
@Getter
@Entity
@Table(name = "fresh_forecast_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreshForecastLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forecast_id")
    private FreshForecast forecast;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    public static FreshForecastLine of(LocalDate menuDate, Long foodId, BigDecimal quantity) {
        FreshForecastLine line = new FreshForecastLine();
        line.menuDate = menuDate;
        line.foodId = foodId;
        line.quantity = quantity;
        return line;
    }

    void attachTo(FreshForecast forecast) {
        this.forecast = forecast;
    }
}
