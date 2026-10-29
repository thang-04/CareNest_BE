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

    // Định danh ổn định qua các phiên bản (NULL = dòng gốc, lineage là chính id), spec 004
    @Column(name = "lineage_id", updatable = false)
    private Long lineageId;

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

    /** Mục tiêu của Bộ đã dùng để tạo dòng (EDU-28); câu trong kế hoạch là bản chép độc lập. */
    @Column(name = "curriculum_goal_id")
    private Long curriculumGoalId;

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

    public void linkCurriculum(Long curriculumGoalId) {
        this.curriculumGoalId = kind == EduGoalRowKind.GOAL ? curriculumGoalId : null;
    }

    public Long lineage() {
        return lineageId == null ? id : lineageId;
    }

    /** Bản chép sang phiên bản mới: cùng vị trí, chữ, lineage. */
    public static EduGoalRow copyFor(Long yearPlanId, EduGoalRow source) {
        EduGoalRow row = create(yearPlanId);
        row.lineageId = source.lineage();
        row.place(source.domainId, source.sortOrder, source.kind, source.text, source.goalNo);
        row.curriculumGoalId = source.curriculumGoalId;
        return row;
    }
}
