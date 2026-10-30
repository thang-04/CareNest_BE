package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduGoalRowKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class EduGoalRow {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "edu_plan_rows")
    @SequenceGenerator(name = "edu_plan_rows", sequenceName = "edu_plan_rows_seq", allocationSize = 1)
    private Long id;

    @Column(name = "year_plan_id", nullable = false, updatable = false)
    private Long yearPlanId;

    @Column(name = "lineage_id", updatable = false)
    private Long lineageId;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    public abstract EduGoalRowKind getKind();

    public Long getSubDomainId() {
        return null;
    }

    public Long getExpectedResultId() {
        return null;
    }

    public Short getGoalNo() {
        return null;
    }

    /** Dòng danh mục của Bộ cùng tầng mà dòng này lấy từ đó (EDU-28); null nếu tự gõ. */
    public abstract Long getCurriculumId();

    public abstract void linkCurriculum(Long id);

    public abstract void attachParents(Long subDomainId, Long expectedResultId);

    protected void numberGoal(Short goalNo) {}

    public static EduGoalRow create(Long yearPlanId, EduGoalRowKind kind) {
        EduGoalRow row =
                switch (kind) {
                    case SUB -> new EduPlanSubDomain();
                    case GROUP -> new EduPlanExpectedResult();
                    case GOAL -> new EduPlanGoal();
                };
        row.yearPlanId = yearPlanId;
        return row;
    }

    public void place(Long domainId, short sortOrder, EduGoalRowKind kind, String text, Short goalNo) {
        if (kind != getKind()) throw new IllegalArgumentException("Row kind cannot change");
        this.domainId = domainId;
        this.sortOrder = sortOrder;
        this.text = text;
        numberGoal(goalNo);
    }

    public Long lineage() {
        return lineageId == null ? id : lineageId;
    }

    public static EduGoalRow copyFor(Long yearPlanId, EduGoalRow source) {
        EduGoalRow row = create(yearPlanId, source.getKind());
        row.lineageId = source.lineage();
        row.place(source.domainId, source.sortOrder, source.getKind(), source.text, source.getGoalNo());
        row.linkCurriculum(source.getCurriculumId());
        return row;
    }
}
