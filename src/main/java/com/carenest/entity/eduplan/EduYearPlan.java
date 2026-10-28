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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    // EDU-10: khóa "Đang sửa"; editing_at = lần lưu cuối, quá hạn thì job trả về editing_from
    @Column(name = "editing_by")
    private Long editingBy;

    @Column(name = "editing_since")
    private Instant editingSince;

    @Column(name = "editing_at")
    private Instant editingAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "editing_from", length = 20)
    private EduPlanStatus editingFrom;

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
        plan.updatedAt = now;
        plan.startEditing(ownerId, now);
        return plan;
    }

    /** Khóa cho {@code userId}; đang khóa bởi chính người đó thì chỉ gia hạn. */
    public void startEditing(Long userId, Instant now) {
        if (status == EduPlanStatus.EDITING && userId.equals(editingBy)) {
            this.editingAt = now;
            return;
        }
        // Khóa quá hạn của người khác: giữ trạng thái gốc của lượt sửa trước
        EduPlanStatus from = status == EduPlanStatus.EDITING ? editingFrom : status;
        this.status = EduPlanStatus.EDITING;
        this.editingFrom = from;
        this.editingBy = userId;
        this.editingSince = now;
        this.editingAt = now;
    }

    public void touchEditing(Instant now) {
        this.editingAt = now;
    }

    /** Nhả khóa, trả về Nháp / Bị trả lại như trước khi sửa. */
    public void finishEditing() {
        if (status != EduPlanStatus.EDITING) {
            return;
        }
        this.status = editingFrom;
        clearEditing();
    }

    /** Trạng thái thật của nội dung: Đang sửa thì là trạng thái trước khi khóa. */
    public EduPlanStatus baseStatus() {
        return status == EduPlanStatus.EDITING ? editingFrom : status;
    }

    public boolean isEditingBy(Long userId) {
        return status == EduPlanStatus.EDITING && userId.equals(editingBy);
    }

    public boolean isEditLockExpired(Instant now, Duration ttl) {
        return status == EduPlanStatus.EDITING && !editingAt.plus(ttl).isAfter(now);
    }

    private void clearEditing() {
        this.editingBy = null;
        this.editingSince = null;
        this.editingAt = null;
        this.editingFrom = null;
    }

    public void updateInfo(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    public void submit(Long userId, Instant now) {
        clearEditing();
        this.status = EduPlanStatus.PENDING_VP;
        this.sentBy = userId;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public void issue(Long userId, Instant now) {
        clearEditing();
        this.status = EduPlanStatus.APPROVED;
        this.issuedBy = userId;
        this.issuedAt = now;
        this.updatedAt = now;
    }

    public void returnToOwner(Instant now) {
        this.status = EduPlanStatus.REJECTED;
        this.updatedAt = now;
    }
}
