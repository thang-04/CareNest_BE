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

    @Override
    public EduGoalRowKind getKind() {
        return EduGoalRowKind.SUB;
    }

    @Override
    public void attachParents(Long subDomainId, Long expectedResultId) {}
}
