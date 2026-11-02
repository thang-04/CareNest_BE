package com.carenest.dto.observation;

import java.time.Instant;
import java.time.LocalDate;

/** Một lần mở lại ngày đánh giá; active = còn hiệu lực lúc trả về. */
public record AssessmentDayUnlockResponse(
        Long id,
        Long classId,
        String className,
        Long campusId,
        LocalDate date,
        Instant validUntil,
        String reason,
        Long unlockedBy,
        String unlockedByName,
        Instant unlockedAt,
        Long revokedBy,
        String revokedByName,
        Instant revokedAt,
        boolean active) {}
