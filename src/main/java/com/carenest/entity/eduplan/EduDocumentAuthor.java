package com.carenest.entity.eduplan;

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

/** Sổ ghi nhận người soạn kế hoạch giáo dục (EDU-54), phục vụ đối soát cấm tự duyệt. */
@Getter
@Entity
@Table(name = "edu_document_authors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduDocumentAuthor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @org.hibernate.annotations.Formula("coalesce(school_year_plan_id, team_plan_id)")
    private Long yearPlanId;

    @Column(name = "school_year_plan_id", updatable = false)
    private Long schoolYearPlanId;

    @Column(name = "team_plan_id", updatable = false)
    private Long teamPlanId;

    public void initializeOwner(EduPlan plan) {
        if (yearPlanId != null && !yearPlanId.equals(plan.getId()))
            throw new IllegalArgumentException("Plan owner differs");
        yearPlanId = plan.getId();
        schoolYearPlanId = plan.getPlanLevel() == com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR ? plan.getId() : null;
        teamPlanId = plan.getPlanLevel() == com.carenest.utils.enums.EduPlanLevel.TEAM ? plan.getId() : null;
    }

    @Column(name = "class_doc_id")
    private Long classDocId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String origin;

    @Column(name = "first_authored_at", nullable = false)
    private Instant firstAuthoredAt;

    @Column(name = "last_authored_at", nullable = false)
    private Instant lastAuthoredAt;

    public static EduDocumentAuthor forYearPlan(Long yearPlanId, Long userId, String origin, Instant now) {
        EduDocumentAuthor author = new EduDocumentAuthor();
        author.yearPlanId = yearPlanId;
        author.userId = userId;
        author.origin = origin;
        author.firstAuthoredAt = now;
        author.lastAuthoredAt = now;
        return author;
    }

    public static EduDocumentAuthor forClassDoc(Long classDocId, Long userId, String origin, Instant now) {
        EduDocumentAuthor author = new EduDocumentAuthor();
        author.classDocId = classDocId;
        author.userId = userId;
        author.origin = origin;
        author.firstAuthoredAt = now;
        author.lastAuthoredAt = now;
        return author;
    }

    public void touch(Instant now) {
        this.lastAuthoredAt = now;
    }
}
