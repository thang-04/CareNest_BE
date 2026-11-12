package com.carenest.entity.meals;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Bản dự trù hàng tươi của một điểm trường theo kỳ (NUT-07): lưu xong không sửa, muốn đổi thì lập bản mới. */
@Getter
@Entity
@Table(name = "fresh_forecasts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreshForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(nullable = false, length = 500)
    private String note = "";

    @Convert(converter = JsonListConverter.ForecastCounts.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<FreshForecastCount> counts = List.of();

    @OneToMany(mappedBy = "forecast", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("menuDate, id")
    private List<FreshForecastLine> lines = new ArrayList<>();

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FreshForecast of(
            Long campusId,
            LocalDate periodFrom,
            LocalDate periodTo,
            String note,
            List<FreshForecastCount> counts,
            List<FreshForecastLine> lines,
            Long createdBy) {
        FreshForecast forecast = new FreshForecast();
        forecast.campusId = campusId;
        forecast.periodFrom = periodFrom;
        forecast.periodTo = periodTo;
        forecast.note = note;
        forecast.createdBy = createdBy;
        forecast.counts = List.copyOf(counts);
        lines.forEach(line -> {
            line.attachTo(forecast);
            forecast.lines.add(line);
        });
        return forecast;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(periodFrom) && !date.isAfter(periodTo);
    }
}
