package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Kế hoạch giáo dục năm học của 1 nhóm tuổi (EDU-01); trạng thái chỉ đổi qua các method nghiệp vụ dưới đây. */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class EduPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "edu_plan_ids")
    @SequenceGenerator(name = "edu_plan_ids", sequenceName = "edu_plan_id_seq", allocationSize = 1)
    protected Long id;

    @Column(nullable = false, updatable = false, length = 30)
    protected String code;

    @Transient
    protected Long curriculumSourcePlanId;

    @Transient
    protected boolean legacyTeamWithoutSource;

    @Transient
    protected String durationPolicySnapshot = "{\"mode\":\"LEGACY_UNBOUNDED\"}";

    public void initializePlanSource(EduPlanLevel level, Long sourceId, boolean legacy, String snapshot) {
        if (level != getPlanLevel()) throw new IllegalArgumentException("Plan type cannot change");
        this.curriculumSourcePlanId = sourceId;
        this.legacyTeamWithoutSource = legacy;
        this.durationPolicySnapshot = snapshot;
    }

    public String sourceMode() {
        return getPlanLevel() == EduPlanLevel.SCHOOL_YEAR ? "ROOT" : curriculumSourcePlanId == null ? "LEGACY" : "INHERITED";
    }

    public String workflowKey() {
        return getPlanLevel().workflowKey();
    }

    @Column(name = "school_year_id", nullable = false, updatable = false)
    protected Long schoolYearId;

    @Column(name = "age_group_id", nullable = false, updatable = false)
    protected Long ageGroupId;

    // Spec 004: số phiên bản trong chuỗi (năm học, nhóm tuổi) và phiên bản được chép
    @Column(name = "version_no", nullable = false, updatable = false)
    protected int versionNo = 1;

    @Column(name = "source_plan_id", updatable = false)
    protected Long sourcePlanId;

    @Column(nullable = false, length = 150)
    protected String title;

    @Column(nullable = false, length = 500)
    protected String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    protected EduPlanStatus status;

    @Column(name = "disabled", nullable = false)
    protected boolean disabled = false;

    @Column(name = "owner_id", nullable = false, updatable = false)
    protected Long ownerId;

    @Column(name = "owner_title", length = 100)
    protected String ownerTitle;

    @Column(name = "sent_by")
    protected Long sentBy;

    @Column(name = "sent_at")
    protected Instant sentAt;

    @Column(name = "issued_by")
    protected Long issuedBy;

    @Column(name = "issued_at")
    protected Instant issuedAt;

    @Column(name = "content_revision", nullable = false)
    protected long contentRevision;

    @Column(name = "last_save_request_id")
    protected UUID lastSaveRequestId;

    @Column(name = "created_request_id", updatable = false)
    protected UUID createdRequestId;

    @Column(name = "review_round", nullable = false)
    protected int reviewRound = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    protected String approvals = "[]";

    @Column(name = "applied_policy_revision")
    protected Long appliedPolicyRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completion_policy", columnDefinition = "jsonb")
    protected String completionPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "print_snapshot", columnDefinition = "jsonb")
    protected String printSnapshot;

    @Column(name = "published_by")
    protected Long publishedBy;

    @Column(name = "completion_mode", length = 50)
    protected String completionMode;

    @Column(name = "completed_at")
    protected Instant completedAt;

    @Column(name = "author_tracking_since", nullable = false)
    protected Instant authorTrackingSince;

    @Column(name = "author_coverage", nullable = false, length = 50)
    protected String authorCoverage = "COMPLETE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    // Gán tay mỗi lần lưu: sửa bảng con không làm Hibernate cập nhật dòng cha
    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;

    public abstract EduPlanLevel getPlanLevel();

    public static EduPlan createDraft(String code, Long schoolYearId, Long ageGroupId, String title,
            String description, Long ownerId, String ownerTitle, Instant now) {
        return createDraft(EduPlanLevel.TEAM, code, schoolYearId, ageGroupId, title, description, ownerId, ownerTitle, now);
    }

    public static EduPlan createDraft(
            EduPlanLevel level,
            String code,
            Long schoolYearId,
            Long ageGroupId,
            String title,
            String description,
            Long ownerId,
            String ownerTitle,
            Instant now) {
        EduPlan plan = level == EduPlanLevel.SCHOOL_YEAR ? new EduYearPlan() : new EduTeamPlan();
        plan.code = code;
        plan.schoolYearId = schoolYearId;
        plan.ageGroupId = ageGroupId;
        plan.title = title;
        plan.description = description;
        plan.ownerId = ownerId;
        plan.ownerTitle = ownerTitle;
        plan.status = EduPlanStatus.DRAFT;
        plan.reviewRound = 0;
        plan.approvals = "[]";
        plan.authorTrackingSince = now;
        plan.authorCoverage = "COMPLETE";
        plan.updatedAt = now;
        return plan;
    }

    /** Phiên bản n+1 chép từ bản đã ban hành; nội dung con do service chép, người tạo là owner (EDU-02). */
    public static EduPlan createVersion(
            EduPlan source, String code, Long ownerId, String ownerTitle, Instant now) {
        EduPlan plan = createDraft(
                source.getPlanLevel(),
                code,
                source.schoolYearId,
                source.ageGroupId,
                source.title,
                source.description,
                ownerId,
                ownerTitle,
                now);
        plan.versionNo = source.versionNo + 1;
        plan.sourcePlanId = source.id;
        plan.initializePlanSource(
                source.getPlanLevel(),
                source.curriculumSourcePlanId,
                source.legacyTeamWithoutSource,
                source.durationPolicySnapshot);
        return plan;
    }

    public void initializeSave(UUID requestId) {
        this.createdRequestId = requestId;
        recordSave(requestId);
    }

    public void recordSave(UUID requestId) {
        this.lastSaveRequestId = requestId;
        this.contentRevision++;
    }

    public void updateInfo(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    public EduPlanStatus publicStatus() {
        return status;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void markDisabled() {
        this.disabled = true;
    }

    public void markDisabled(Instant now) {
        this.disabled = true;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void submit(Long userId, Instant now) {
        this.status = EduPlanStatus.REVIEW;
        this.sentBy = userId;
        this.sentAt = now;
        this.reviewRound++;
        this.approvals = "[]";
        this.updatedAt = now;
    }

    public void advanceReview(String approvals, Instant now) {
        this.status = EduPlanStatus.REVIEW;
        this.approvals = approvals != null ? approvals : "[]";
        this.updatedAt = now;
    }

    public void publish(
            Long publisherId,
            Long legacyIssuedBy,
            Instant legacyIssuedAt,
            String approvals,
            Long policyRevision,
            String completionPolicy,
            String printSnapshot,
            Instant now) {
        this.status = EduPlanStatus.APPROVED;
        this.publishedBy = publisherId;
        this.completedAt = now;
        this.completionMode = "HUMAN_APPROVAL";
        this.approvals = approvals != null ? approvals : "[]";
        this.appliedPolicyRevision = policyRevision;
        this.completionPolicy = completionPolicy;
        this.printSnapshot = printSnapshot;
        this.issuedBy = legacyIssuedBy;
        this.issuedAt = legacyIssuedAt;
        this.updatedAt = now;
    }

    public void publish(
            Long publisherId,
            String approvals,
            Long policyRevision,
            String completionPolicy,
            String printSnapshot,
            Instant now) {
        publish(publisherId, publisherId, now, approvals, policyRevision, completionPolicy, printSnapshot, now);
    }

    public void issue(Long userId, Instant now) {
        this.status = EduPlanStatus.APPROVED;
        this.issuedBy = userId;
        this.issuedAt = now;
        this.publishedBy = userId;
        this.completedAt = now;
        this.completionMode = "HUMAN_APPROVAL";
        this.updatedAt = now;
    }

    public void returnToDraft(Instant now) {
        this.status = EduPlanStatus.DRAFT;
        this.updatedAt = now;
    }

    public void returnToOwner(Instant now) {
        returnToDraft(now);
    }

    public void projectLegacyIssuer(Long vicePrincipalId, Instant approvedAt) {
        this.issuedBy = vicePrincipalId;
        this.issuedAt = approvedAt;
    }

    public void updateWorkflowState(
            EduPlanStatus status,
            int reviewRound,
            String approvals,
            Long appliedPolicyRevision,
            String completionPolicy,
            String printSnapshot,
            Long publishedBy,
            String completionMode,
            Instant completedAt,
            Instant now) {
        this.status = status;
        this.reviewRound = reviewRound;
        this.approvals = approvals != null ? approvals : "[]";
        this.appliedPolicyRevision = appliedPolicyRevision;
        this.completionPolicy = completionPolicy;
        this.printSnapshot = printSnapshot;
        this.publishedBy = publishedBy;
        this.completionMode = completionMode;
        this.completedAt = completedAt;
        this.updatedAt = now;
    }

    public void backfillLegacySnapshot(String printSnapshot, String authorCoverage, Instant now) {
        if (this.printSnapshot == null || this.printSnapshot.isBlank()) {
            this.printSnapshot = printSnapshot;
        }
        if (this.authorCoverage == null) {
            this.authorCoverage = authorCoverage;
        }
        this.updatedAt = now;
    }
}
