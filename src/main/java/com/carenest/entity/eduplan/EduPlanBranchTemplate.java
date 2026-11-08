package com.carenest.entity.eduplan;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "edu_plan_branch_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduPlanBranchTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_plan_id", nullable = false)
    private Long yearPlanId;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    @Column(name = "source_template_id")
    private Long sourceTemplateId;

    @Column(name = "lineage_id")
    private Long lineageId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static EduPlanBranchTemplate create(
            Long planId,
            Long themeId,
            Long source,
            Long lineageId,
            String name,
            LocalDate start,
            LocalDate end,
            int order) {
        EduPlanBranchTemplate t = new EduPlanBranchTemplate();
        t.yearPlanId = planId;
        t.themeId = themeId;
        t.sourceTemplateId = source;
        t.lineageId = lineageId;
        t.name = name;
        t.startDate = start;
        t.endDate = end;
        t.sortOrder = order;
        return t;
    }

    public void update(Long themeId, String name, LocalDate start, LocalDate end, int order) {
        this.themeId = themeId;
        this.name = name;
        this.startDate = start;
        this.endDate = end;
        this.sortOrder = order;
    }

    public long lineage() {
        return lineageId == null ? id : lineageId;
    }
}
