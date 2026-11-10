package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduGoalRowKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "edu_plan_goals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduPlanGoal extends EduGoalRow {
    @Column(name = "sub_domain_id")
    private Long subDomainId;

    @Column(name = "expected_result_id")
    private Long expectedResultId;

    @Column(name = "goal_no", nullable = false)
    private Short goalNo;

    @Column(name = "curriculum_goal_id")
    private Long curriculumGoalId;

    @Override
    public EduGoalRowKind getKind() {
        return EduGoalRowKind.GOAL;
    }

    @Override
    public void attachParents(Long subDomainId, Long expectedResultId) {
        this.subDomainId = subDomainId;
        this.expectedResultId = expectedResultId;
    }

    @Override
    protected void numberGoal(Short goalNo) {
        this.goalNo = goalNo;
    }

    @Override
    public Long getCurriculumId() {
        return curriculumGoalId;
    }

    @Override
    public void linkCurriculum(Long id) {
        this.curriculumGoalId = id;
    }
}
