package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduGoalRowKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "edu_plan_expected_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduPlanExpectedResult extends EduGoalRow {
    @Column(name = "sub_domain_id")
    private Long subDomainId;

    @Override
    public EduGoalRowKind getKind() {
        return EduGoalRowKind.GROUP;
    }

    @Override
    public void attachParents(Long subDomainId, Long expectedResultId) {
        this.subDomainId = subDomainId;
    }
}
