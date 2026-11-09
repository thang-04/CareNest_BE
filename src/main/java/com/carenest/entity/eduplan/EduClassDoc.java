package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduClassDocStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Tài liệu kế hoạch của lớp (EDU-12): chủ đề nhánh, kế hoạch chủ đề, giáo án tuần (kế hoạch tuần + 5 kế hoạch ngày).
 * Nội dung soạn, người duyệt và lịch sử lưu JSONB (chuỗi JSON ở tầng Java).
 */
@Getter
@Entity
@Table(name = "edu_class_docs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduClassDoc {
    public static final String BRANCH = "branch";
    public static final String THEME = "theme";
    public static final String WEEK = "week";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_key", nullable = false, length = 80)
    private String docKey;

    @Column(nullable = false, length = 10)
    private String kind;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "team_plan_id", nullable = false)
    private Long yearPlanId;

    @Column(name = "theme_id")
    private Long themeId;

    @Column(name = "week_start")
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EduClassDocStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String approvals;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String history;

    // Spec 004: bản chép từ tài liệu cùng lớp ở phiên bản kế hoạch năm cũ
    @Column(name = "source_doc_id")
    private Long sourceDocId;

    // "Kế hoạch điều chỉnh và bổ sung" theo ngày của giáo án tuần; tách khỏi nội dung để sửa được sau khi duyệt
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String adjustments;

    // EDU-18: chuỗi role duyệt chụp lúc gửi; null = hồ sơ gửi trước khi có cột
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sign_chain", columnDefinition = "jsonb")
    private String signChain;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @Column(name = "review_round", nullable = false)
    private int reviewRound = 0;

    @Column(name = "applied_policy_revision")
    private Long appliedPolicyRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completion_policy", columnDefinition = "jsonb")
    private String completionPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "print_snapshot", columnDefinition = "jsonb")
    private String printSnapshot;

    @Column(name = "completion_mode", length = 50)
    private String completionMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duration_policy_snapshot", columnDefinition = "jsonb")
    private String durationPolicySnapshot;

    public void assignDurationPolicySnapshot(String snapshot) {
        if (this.durationPolicySnapshot == null) {
            this.durationPolicySnapshot = snapshot;
        }
    }

    public void setDurationPolicySnapshot(String snapshot) {
        this.durationPolicySnapshot = snapshot;
    }

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "author_tracking_since", nullable = false)
    private Instant authorTrackingSince;

    @Column(name = "author_coverage", nullable = false, length = 50)
    private String authorCoverage = "COMPLETE";

    public static EduClassDoc create(
            String docKey,
            String kind,
            Long classroomId,
            Long yearPlanId,
            Long themeId,
            LocalDate weekStart,
            Long userId) {
        EduClassDoc doc = new EduClassDoc();
        doc.docKey = docKey;
        doc.kind = kind;
        doc.classroomId = classroomId;
        doc.yearPlanId = yearPlanId;
        doc.themeId = themeId;
        doc.weekStart = weekStart;
        doc.status = EduClassDocStatus.DRAFT;
        doc.content = "{}";
        doc.approvals = "[]";
        doc.history = "[]";
        doc.adjustments = "{}";
        doc.createdBy = userId;
        doc.updatedBy = userId;
        doc.reviewRound = 0;
        doc.authorTrackingSince = Instant.now();
        doc.authorCoverage = "COMPLETE";
        return doc;
    }

    public EduClassDocStatus publicStatus() {
        return this.status != null ? this.status : EduClassDocStatus.DRAFT;
    }

    public void submit(int reviewRound) {
        this.status = EduClassDocStatus.REVIEW;
        this.reviewRound = reviewRound;
        this.approvals = "[]";
    }

    public void advanceReview(String approvals, Instant now) {
        this.status = EduClassDocStatus.REVIEW;
        this.approvals = approvals != null ? approvals : "[]";
        this.updatedAt = now;
    }

    public void publish(
            Long userId,
            String approvals,
            Long appliedPolicyRevision,
            String completionPolicy,
            String printSnapshot,
            Instant completedAt) {
        this.status = EduClassDocStatus.APPROVED;
        this.approvals = approvals != null ? approvals : "[]";
        this.appliedPolicyRevision = appliedPolicyRevision;
        this.completionPolicy = completionPolicy;
        this.printSnapshot = printSnapshot;
        this.completionMode = "MANUAL";
        this.completedAt = completedAt;
        this.updatedBy = userId;
    }

    public void returnToDraft(String history, Long userId) {
        this.status = EduClassDocStatus.DRAFT;
        this.history = history != null ? history : "[]";
        this.updatedBy = userId;
    }

    public void changeContent(String content, Long userId) {
        this.content = content;
        this.updatedBy = userId;
    }

    public void touchDossier(Instant now) {
        this.updatedAt = updatedAt == null || now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }

    public void changeStatus(EduClassDocStatus status, String approvals, String history, Long userId) {
        this.status = status;
        this.approvals = approvals;
        this.history = history;
        this.updatedBy = userId;
    }

    public void snapshotSignChain(String signChain) {
        this.signChain = signChain;
    }

    public void linkSource(Long sourceDocId) {
        this.sourceDocId = sourceDocId;
    }

    public void changeAdjustments(String adjustments, String history, Long userId) {
        this.adjustments = adjustments;
        this.history = history;
        this.updatedBy = userId;
    }

    public void updateWorkflowState(
            EduClassDocStatus status,
            int reviewRound,
            String approvals,
            String history,
            Long appliedPolicyRevision,
            String completionPolicy,
            String printSnapshot,
            String completionMode,
            Instant completedAt,
            Long userId) {
        this.status = status;
        this.reviewRound = reviewRound;
        this.approvals = approvals != null ? approvals : "[]";
        this.history = history != null ? history : "[]";
        this.appliedPolicyRevision = appliedPolicyRevision;
        this.completionPolicy = completionPolicy;
        this.printSnapshot = printSnapshot;
        this.completionMode = completionMode;
        this.completedAt = completedAt;
        this.updatedBy = userId;
    }

    public void backfillLegacySnapshot(String printSnapshot, String authorCoverage, Long userId) {
        if (this.printSnapshot == null || this.printSnapshot.isBlank()) {
            this.printSnapshot = printSnapshot;
        }
        if (this.authorCoverage == null) {
            this.authorCoverage = authorCoverage;
        }
        this.updatedBy = userId;
    }
}
