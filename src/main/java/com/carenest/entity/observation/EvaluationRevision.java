package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bản đánh giá đã xác nhận bị thay bằng bản sửa lại (OBS-30); chỉ thêm, không sửa. */
@Getter
@Entity
@Table(name = "evaluation_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationRevision {
    public static final String THEME = "THEME";
    public static final String SEMESTER = "SEMESTER";
    public static final String YEAR = "YEAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(nullable = false, columnDefinition = "text")
    private String snapshot;

    @Column(name = "replaced_by", nullable = false)
    private Long replacedBy;

    @Column(name = "replaced_at", nullable = false)
    private Instant replacedAt;

    public static EvaluationRevision of(String kind, Long evaluationId, String snapshot, Long userId, Instant at) {
        EvaluationRevision r = new EvaluationRevision();
        r.kind = kind;
        r.evaluationId = evaluationId;
        r.snapshot = snapshot;
        r.replacedBy = userId;
        r.replacedAt = at;
        return r;
    }
}
