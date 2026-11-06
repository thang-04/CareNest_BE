package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduPlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
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
@Entity
@Table(name = "edu_year_plans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduYearPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "school_year_id", nullable = false, updatable = false)
    private Long schoolYearId;

    @Column(name = "age_group_id", nullable = false, updatable = false)
    private Long ageGroupId;

    // Spec 004: số phiên bản trong chuỗi (năm học, nhóm tuổi) và phiên bản được chép
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo = 1;

    @Column(name = "source_plan_id", updatable = false)
    private Long sourcePlanId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EduPlanStatus status;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "owner_title", length = 100)
    private String ownerTitle;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_at")
    private Instant issuedAt;

    // EDU-10: phiên sửa được duy trì độc lập với lưu nội dung.
    @Column(name = "editing_by")
    private Long editingBy;

    @Column(name = "editing_since")
    private Instant editingSince;

    @Column(name = "editing_at")
    private Instant editingAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "editing_from", length = 20)
    private EduPlanStatus editingFrom;

    @Column(name = "edit_session_id")
    private UUID editSessionId;

    @Column(name = "content_revision", nullable = false)
    private long contentRevision;

    @Column(name = "last_save_request_id")
    private UUID lastSaveRequestId;

    @Column(name = "created_request_id", updatable = false)
    private UUID createdRequestId;

    @Column(name = "review_round", nullable = false)
    private int reviewRound = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String approvals = "[]";

    @Column(name = "applied_policy_revision")
    private Long appliedPolicyRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completion_policy", columnDefinition = "jsonb")
    private String completionPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "print_snapshot", columnDefinition = "jsonb")
    private String printSnapshot;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "completion_mode", length = 50)
    private String completionMode;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "author_tracking_since", nullable = false)
    private Instant authorTrackingSince;

    @Column(name = "author_coverage", nullable = false, length = 50)
    private String authorCoverage = "COMPLETE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Gán tay mỗi lần lưu: sửa bảng con không làm Hibernate cập nhật dòng cha
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EduYearPlan createDraft(
            String code,
            Long schoolYearId,
            Long ageGroupId,
            String title,
            String description,
            Long ownerId,
            String ownerTitle,
            Instant now) {
        EduYearPlan plan = new EduYearPlan();
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
        plan.startEditing(ownerId, now);
        return plan;
    }

    /** Phiên bản n+1 chép từ bản đã ban hành; nội dung con do service chép, người tạo là owner (EDU-02). */
    public static EduYearPlan createVersion(
            EduYearPlan source, String code, Long ownerId, String ownerTitle, Instant now) {
        EduYearPlan plan = createDraft(
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
        return plan;
    }

    /** Khóa cho {@code userId}; đang khóa bởi chính người đó thì chỉ gia hạn. */
    public void startEditing(Long userId, Instant now) {
        if (userId.equals(editingBy)) {
            this.editingAt = now;
            return;
        }
        this.status = EduPlanStatus.DRAFT;
        this.editingFrom = EduPlanStatus.DRAFT;
        this.editingBy = userId;
        this.editSessionId = UUID.randomUUID();
        this.editingSince = now;
        this.editingAt = now;
    }

    public void touchEditing(Instant now) {
        this.editingAt = now;
    }

    /** Nhả khóa, trả về Nháp. */
    public void finishEditing() {
        if (editingBy == null) {
            return;
        }
        this.status = EduPlanStatus.DRAFT;
        clearEditing();
    }

    /** Trạng thái thật của nội dung. */
    public EduPlanStatus baseStatus() {
        return status;
    }

    public boolean isEditingBy(Long userId) {
        return userId.equals(editingBy);
    }

    public boolean isEditLockExpired(Instant now, Duration ttl) {
        return editingAt != null && !editingAt.plus(ttl).isAfter(now);
    }

    private void clearEditing() {
        this.editingBy = null;
        this.editingSince = null;
        this.editingAt = null;
        this.editingFrom = null;
        this.editSessionId = null;
    }

    public void acquireSession(Long userId, UUID sessionId, Instant now) {
        if (!userId.equals(editingBy) || !sessionId.equals(editSessionId)) {
            startEditing(userId, now);
        }
        this.editSessionId = sessionId;
        this.editingAt = now;
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

    public void submit(Long userId, Instant now) {
        clearEditing();
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
        clearEditing();
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
        clearEditing();
        this.status = EduPlanStatus.APPROVED;
        this.issuedBy = userId;
        this.issuedAt = now;
        this.publishedBy = userId;
        this.completedAt = now;
        this.completionMode = "HUMAN_APPROVAL";
        this.updatedAt = now;
    }

    public void returnToDraft(Instant now) {
        clearEditing();
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
