package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduGoalRowKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "edu_plan_sub_domains")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduPlanSubDomain extends EduGoalRow {

    @Column(name = "curriculum_sub_domain_id")
    private Long curriculumSubDomainId;

    @Override
    public EduGoalRowKind getKind() {
        return EduGoalRowKind.SUB;
    }

    @Override
    public void attachParents(Long subDomainId, Long expectedResultId) {}

    @Override
    public Long getCurriculumId() {
        return curriculumSubDomainId;
    }

    @Override
    public void linkCurriculum(Long id) {
        this.curriculumSubDomainId = id;
    }
}
