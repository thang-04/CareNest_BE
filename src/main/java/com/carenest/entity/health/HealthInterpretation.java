package com.carenest.entity.health;

import com.carenest.utils.enums.InterpretationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Giải thích xu hướng sức khỏe (HLT-04, HLT-05; V88): AI viết nháp {@code source=AI} kèm model, thời điểm (AI-01); nhân
 * sự duyệt (sửa câu chữ thì {@code source=HUMAN}) mới thành chính thức (AI-02). Cảnh báo xu hướng tính xác định (HLT-02)
 * lưu kèm. Dữ liệu sức khỏe: không log.
 */
@Getter
@Entity
@Table(name = "health_interpretations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthInterpretation {
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_HUMAN = "HUMAN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterpretationStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    /** Nội dung có cấu trúc (HLT-21, V129) dạng JSON của {@code HealthTrendContent}; null ở bản ghi trước V129. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String warnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "measurement_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String measurementIds;

    @Column(nullable = false, length = 10)
    private String source;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "generated_by", nullable = false, updatable = false)
    private Long generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static HealthInterpretation draft(
            Long childId,
            String summary,
            String contentJson,
            String warningsJson,
            String measurementIdsJson,
            String model,
            Long generatedBy,
            Instant generatedAt) {
        HealthInterpretation i = new HealthInterpretation();
        i.childId = childId;
        i.status = InterpretationStatus.DRAFT;
        i.summary = summary;
        i.content = contentJson;
        i.warnings = warningsJson;
        i.measurementIds = measurementIdsJson;
        i.source = SOURCE_AI;
        i.model = model;
        i.generatedBy = generatedBy;
        i.generatedAt = generatedAt;
        return i;
    }

    public boolean isApproved() {
        return status == InterpretationStatus.APPROVED;
    }

    /** Duyệt (AI-02); {@code editedSummary} khác bản AI ⇒ nhân sự chịu trách nhiệm nội dung ({@code source=HUMAN}). */
    public void approve(String editedSummary, Long userId, Instant at) {
        if (editedSummary != null && !editedSummary.equals(summary)) {
            this.summary = editedSummary;
            this.source = SOURCE_HUMAN;
        }
        this.status = InterpretationStatus.APPROVED;
        this.approvedBy = userId;
        this.approvedAt = at;
    }

    @Override
    public String toString() {
        return "HealthInterpretation[id=" + id + ", status=" + status + "]";
    }
}
