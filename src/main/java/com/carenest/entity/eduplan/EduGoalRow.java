package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduGoalRowKind;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Dòng bảng mục tiêu trong 1 lĩnh vực, theo thứ tự hiển thị; chỉ dòng GOAL có số MT (EDU-09). */
@Getter
@Entity
@Table(name = "edu_goal_rows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduGoalRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_plan_id", nullable = false, updatable = false)
    private Long yearPlanId;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Convert(converter = EduGoalRowKindConverter.class)
    @Column(nullable = false, length = 10)
    private EduGoalRowKind kind;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "goal_no")
    private Short goalNo;

    public static EduGoalRow create(Long yearPlanId) {
        EduGoalRow row = new EduGoalRow();
        row.yearPlanId = yearPlanId;
        return row;
    }

    public void place(Long domainId, short sortOrder, EduGoalRowKind kind, String text, Short goalNo) {
        this.domainId = domainId;
        this.sortOrder = sortOrder;
        this.kind = kind;
        this.text = text;
        this.goalNo = goalNo;
    }
}
