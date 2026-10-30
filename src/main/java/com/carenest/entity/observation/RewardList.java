package com.carenest.entity.observation;

import com.carenest.utils.enums.RewardStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Danh sách đề xuất khen thưởng cuối năm của một lớp. */
@Getter
@Entity
@Table(name = "reward_lists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RewardStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "review_by")
    private Long reviewBy;

    @Column(name = "review_at")
    private Instant reviewAt;

    @Column(name = "review_decision", length = 10)
    private String reviewDecision;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "decision_by")
    private Long decisionBy;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @Column(length = 10)
    private String decision;

    @Column(name = "decision_comment", length = 500)
    private String decisionComment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static RewardList draft(Long classroomId, Long campusId, Long schoolYearId, Long userId) {
        RewardList l = new RewardList();
        l.classroomId = classroomId;
        l.campusId = campusId;
        l.schoolYearId = schoolYearId;
        l.status = RewardStatus.DRAFT;
        l.createdBy = userId;
        l.updatedBy = userId;
        return l;
    }

    public boolean isEditable() {
        return status == RewardStatus.DRAFT || status == RewardStatus.RETURNED;
    }

    public void touch(Long userId) {
        this.updatedBy = userId;
    }

    public void submit(Instant at) {
        this.status = RewardStatus.PENDING_VP;
        this.submittedAt = at;
    }

    /** PHT chuyển HT (FORWARD) hoặc trả lại GV (RETURN). */
    public void review(boolean forward, Long userId, Instant at, String comment) {
        this.status = forward ? RewardStatus.PENDING_PRINCIPAL : RewardStatus.RETURNED;
        this.reviewBy = userId;
        this.reviewAt = at;
        this.reviewDecision = forward ? "FORWARD" : "RETURN";
        this.reviewComment = comment;
    }

    /** HT phê duyệt (APPROVE) hoặc trả lại GV (RETURN). */
    public void decide(boolean approve, Long userId, Instant at, String comment) {
        this.status = approve ? RewardStatus.APPROVED : RewardStatus.RETURNED;
        this.decisionBy = userId;
        this.decisionAt = at;
        this.decision = approve ? "APPROVE" : "RETURN";
        this.decisionComment = comment;
    }
}
