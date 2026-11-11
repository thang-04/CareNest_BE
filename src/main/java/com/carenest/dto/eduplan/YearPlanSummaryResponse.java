package com.carenest.dto.eduplan;

import com.carenest.utils.enums.EduPlanStatus;
import java.time.Instant;

/** Một dòng của danh sách mục tiêu năm học. */
public record YearPlanSummaryResponse(
        Long id,
        String code,
        Long schoolYearId,
        String schoolYearName,
        Long ageGroupId,
        String ageGroupName,
        String title,
        EduPlanStatus status,
        long goalCount,
        long themeCount,
        PlanActorResponse owner,
        PlanActorResponse sentBy,
        PlanActorResponse issued,
        PlanActorResponse editing,
        Instant createdAt,
        Instant updatedAt,
        long revision,
        Instant editExpiresAt,
        int versionNo,
        Long sourcePlanId,
        boolean current,
        EduReviewStateResponse review,
        YearPlanDetailResponse.Permissions permissions,
        com.carenest.utils.enums.EduPlanLevel planLevel,
        Long curriculumSourcePlanId,
        String sourceMode,
        @com.fasterxml.jackson.annotation.JsonRawValue String durationPolicySnapshot,
        // Spec 005 FR-021a: màn Kế hoạch quá hạn đọc hạn/khóa từ danh sách
        YearPlanDetailResponse.TimeLock timeLock) {

    @com.fasterxml.jackson.annotation.JsonProperty
    public com.carenest.utils.enums.EduPlanLevel planType() { return planLevel; }

    @com.fasterxml.jackson.annotation.JsonProperty
    public String planTypeLabel() {
        return planLevel == com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR ? "Kế hoạch năm học" : "Kế hoạch tổ";
    }

    @com.fasterxml.jackson.annotation.JsonProperty
    public Instant sentAt() { return sentBy == null ? null : sentBy.at(); }

    public YearPlanSummaryResponse(
            Long id,
            String code,
            Long schoolYearId,
            String schoolYearName,
            Long ageGroupId,
            String ageGroupName,
            String title,
            EduPlanStatus status,
            long goalCount,
            long themeCount,
            PlanActorResponse owner,
            PlanActorResponse sentBy,
            PlanActorResponse issued,
            PlanActorResponse editing,
            Instant createdAt,
            Instant updatedAt,
            long revision,
            Instant editExpiresAt,
            int versionNo,
            Long sourcePlanId,
            boolean current,
            EduReviewStateResponse review,
            YearPlanDetailResponse.Permissions permissions) {
        this(
                id,
                code,
                schoolYearId,
                schoolYearName,
                ageGroupId,
                ageGroupName,
                title,
                status,
                goalCount,
                themeCount,
                owner,
                sentBy,
                issued,
                editing,
                createdAt,
                updatedAt,
                revision,
                editExpiresAt,
                versionNo,
                sourcePlanId,
                current,
                review,
                permissions,
                com.carenest.utils.enums.EduPlanLevel.TEAM,
                null,
                "LEGACY",
                "{\"mode\":\"LEGACY_UNBOUNDED\"}",
                null);
    }
}
