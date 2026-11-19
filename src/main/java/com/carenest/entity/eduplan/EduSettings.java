package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cấu hình kế hoạch giáo dục của trường (1 dòng, EDU-11, EDU-54): chuỗi người ký, workflow policy và dòng hoạt động tuần dạng JSON. */
@Getter
@Entity
@Table(name = "edu_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private String signers;

    @Column(name = "legacy_signers")
    private String legacySigners;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_policy", nullable = false, columnDefinition = "jsonb")
    private String workflowPolicy;

    @Column(name = "workflow_revision", nullable = false)
    private long workflowRevision = 1;

    @Column(name = "workflow_updated_by")
    private Long workflowUpdatedBy;

    @Column(name = "workflow_updated_at")
    private Instant workflowUpdatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duration_policy", nullable = false, columnDefinition = "jsonb")
    private String durationPolicy;

    @Column(name = "duration_revision", nullable = false)
    private long durationRevision = 1;

    public void updateDurationPolicy(String policy, Long userId, Instant now) {
        this.durationPolicy = policy;
        this.durationRevision++;
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    @Column(name = "week_rows", nullable = false)
    private String weekRows;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void update(String signers, String weekRows, Long userId, Instant now) {
        this.signers = signers;
        this.weekRows = weekRows;
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    public void updateWorkflowPolicy(String workflowPolicy, Long userId, Instant now) {
        this.workflowPolicy = workflowPolicy;
        this.workflowRevision++;
        this.workflowUpdatedBy = userId;
        this.workflowUpdatedAt = now;
    }
}
