package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduPlanLevel;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "edu_team_plans")
public class EduTeamPlan extends EduPlan {
    @Column(name = "previous_source_plan_id")
    private Long previousSourcePlanId;

    @Override
    public EduPlanLevel getPlanLevel() { return EduPlanLevel.TEAM; }

    @Override
    @Access(AccessType.PROPERTY)
    @Column(name = "curriculum_source_plan_id")
    public Long getCurriculumSourcePlanId() { return curriculumSourcePlanId; }
    protected void setCurriculumSourcePlanId(Long value) { curriculumSourcePlanId = value; }

    @Override
    @Access(AccessType.PROPERTY)
    @Column(name = "legacy_team_without_source", nullable = false)
    public boolean isLegacyTeamWithoutSource() { return legacyTeamWithoutSource; }
    protected void setLegacyTeamWithoutSource(boolean value) { legacyTeamWithoutSource = value; }

    @Override
    @Access(AccessType.PROPERTY)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duration_policy_snapshot", nullable = false, columnDefinition = "jsonb")
    public String getDurationPolicySnapshot() { return durationPolicySnapshot; }
    protected void setDurationPolicySnapshot(String value) { durationPolicySnapshot = value; }

    public Long getPreviousSourcePlanId() { return previousSourcePlanId; }

    public void changeCurriculumSource(Long sourceId, java.time.Instant now) {
        if (java.util.Objects.equals(curriculumSourcePlanId, sourceId)) return;
        previousSourcePlanId = curriculumSourcePlanId;
        curriculumSourcePlanId = sourceId;
        returnToDraft(now);
        contentRevision++;
        lastSaveRequestId = null;
    }
}
