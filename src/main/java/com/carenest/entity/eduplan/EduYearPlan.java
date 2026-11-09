package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduPlanLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "edu_year_plans")
public class EduYearPlan extends EduPlan {
    @Override
    public EduPlanLevel getPlanLevel() { return EduPlanLevel.SCHOOL_YEAR; }
}
