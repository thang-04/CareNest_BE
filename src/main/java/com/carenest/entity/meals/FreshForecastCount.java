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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Sĩ số một nhóm tuổi của bản dự trù: số đang học lúc lập (tham chiếu) và số dự kiến PHT chốt để tính suất. */
@Getter
@Entity
@Table(name = "fresh_forecast_counts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreshForecastCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forecast_id")
    private FreshForecast forecast;

    @Column(name = "age_group_id", nullable = false)
    private Long ageGroupId;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Column(name = "expected_count", nullable = false)
    private int expectedCount;

    public static FreshForecastCount of(Long ageGroupId, int enrolledCount, int expectedCount) {
        FreshForecastCount count = new FreshForecastCount();
        count.ageGroupId = ageGroupId;
        count.enrolledCount = enrolledCount;
        count.expectedCount = expectedCount;
        return count;
    }

    void attachTo(FreshForecast forecast) {
        this.forecast = forecast;
    }
}
