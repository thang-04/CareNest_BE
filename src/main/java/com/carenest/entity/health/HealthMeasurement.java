package com.carenest.entity.health;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lần đo của trẻ trong đợt/kỳ (HLT-13); lưu hay sửa là công bố cho phụ huynh (user chốt 2026-10-23). */
@Getter
@Entity
@Table(name = "health_measurements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "head_circumference_cm", precision = 3, scale = 1)
    private BigDecimal headCircumferenceCm;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(name = "measured_by", nullable = false)
    private Long measuredBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private HealthMeasurement(
            Long childId,
            Long campaignId,
            Long classroomId,
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            String note,
            Long measuredBy,
            Instant createdAt) {
        this.childId = childId;
        this.campaignId = campaignId;
        this.classroomId = classroomId;
        this.date = date;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.headCircumferenceCm = headCircumferenceCm;
        this.note = note;
        this.measuredBy = measuredBy;
        this.publishedAt = createdAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Sửa số đo: công bố lại bản mới. */
    public void update(
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            String note,
            Instant at) {
        this.date = date;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.headCircumferenceCm = headCircumferenceCm;
        this.note = note;
        this.publishedAt = at;
        this.updatedAt = at;
    }
}
