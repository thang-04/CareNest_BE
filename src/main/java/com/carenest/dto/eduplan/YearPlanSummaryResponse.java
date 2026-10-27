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
        Instant editExpiresAt) {}
