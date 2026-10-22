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
        return plan;
    }

    public void updateInfo(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    public void submit(Long userId, Instant now) {
        this.status = EduPlanStatus.PENDING_VP;
        this.sentBy = userId;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public void issue(Long userId, Instant now) {
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
