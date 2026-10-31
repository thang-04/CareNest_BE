package com.carenest.entity.health;

import com.carenest.utils.enums.HealthExamStatus;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một lần đo của trẻ trong đợt/kỳ (HLT-13); lưu hay sửa là công bố cho phụ huynh (user chốt 2026-10-23). Ở đợt khám,
 * lần đo mang phiếu khám Phụ lục II ({@code exam}): nháp chưa công bố, chưa tính là kết quả; kết luận là công bố (user
 * chốt 2026-10-31).
 */
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

    @Column(name = "height_cm", precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "head_circumference_cm", precision = 3, scale = 1)
    private BigDecimal headCircumferenceCm;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(name = "measured_by", nullable = false)
    private Long measuredBy;

    /** Null khi phiếu khám còn nháp. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    /** Nội dung phiếu khám (JSON {@code HealthExamForm} đã chuẩn hóa); null với số đo của kỳ cân đo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String exam;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_status", length = 20)
    private HealthExamStatus examStatus;

    /** Lần kết luận đầu tiên. */
    @Column(name = "concluded_at")
    private Instant concludedAt;

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
        this.publishedBy = measuredBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Phiếu khám mới của trẻ trong đợt khám, nội dung gán bằng {@link #saveExam}. */
    public static HealthMeasurement newExam(Long childId, Long campaignId, Long measuredBy, Instant at) {
        HealthMeasurement m = new HealthMeasurement();
        m.childId = childId;
        m.campaignId = campaignId;
        m.note = "";
        m.measuredBy = measuredBy;
        m.examStatus = HealthExamStatus.DRAFT;
        m.createdAt = at;
        m.updatedAt = at;
        return m;
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

    /** Lưu phiếu khám; {@code conclude}: kết luận và công bố (lần đầu ghi {@code concludedAt}), không thì giữ nháp. */
    public void saveExam(
            Long classroomId,
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            String exam,
            boolean conclude,
            Long userId,
            Instant at) {
        this.classroomId = classroomId;
        this.date = date;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.headCircumferenceCm = headCircumferenceCm;
        this.exam = exam;
        this.updatedAt = at;
        if (conclude) {
            this.examStatus = HealthExamStatus.CONCLUDED;
            if (this.concludedAt == null) {
                this.concludedAt = at;
            }
            this.publishedAt = at;
            this.publishedBy = userId;
        }
    }

    public boolean isExam() {
        return examStatus != null;
    }

    /** Nháp phiếu khám: chưa công bố, không tính là kết quả. */
    public boolean isDraft() {
        return examStatus == HealthExamStatus.DRAFT;
    }
}
