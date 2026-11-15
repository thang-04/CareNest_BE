package com.carenest.utils.enums;

public enum EduPlanLevel {
    SCHOOL_YEAR,
    TEAM;

    public String workflowKey() {
        return this == SCHOOL_YEAR ? "schoolYear" : "year";
    }
}
