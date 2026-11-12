package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduPlanLevel;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "edu_year_plans")
public class EduYearPlan extends EduPlan {
    @Override
    public EduPlanLevel getPlanLevel() {
        return EduPlanLevel.SCHOOL_YEAR;
    }

    @Override
    @Access(AccessType.PROPERTY)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duration_policy_snapshot", nullable = false, columnDefinition = "jsonb")
    public String getDurationPolicySnapshot() {
        return durationPolicySnapshot;
    }

    protected void setDurationPolicySnapshot(String value) {
        durationPolicySnapshot = value;
    }
}
