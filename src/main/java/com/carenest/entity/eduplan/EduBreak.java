package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Đợt nghỉ trong năm học; tuần trùng đợt nghỉ không tính vào số tuần của chủ đề. */
@Getter
@Entity
@Table(name = "edu_breaks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduBreak {

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

    // Định danh ổn định qua các phiên bản (NULL = dòng gốc, lineage là chính id), spec 004
    @Column(name = "lineage_id", updatable = false)
    private Long lineageId;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public static EduBreak create(Long yearPlanId) {
        EduBreak item = new EduBreak();
        item.yearPlanId = yearPlanId;
        return item;
    }

    public void update(short sortOrder, String label, LocalDate startDate, LocalDate endDate) {
        this.sortOrder = sortOrder;
        this.label = label;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long lineage() {
        return lineageId == null ? id : lineageId;
    }

    public static EduBreak copyFor(Long yearPlanId, EduBreak source) {
        EduBreak item = create(yearPlanId);
        item.lineageId = source.lineage();
        item.update(source.sortOrder, source.label, source.startDate, source.endDate);
        return item;
    }
}
