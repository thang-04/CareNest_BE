package com.carenest.dto.attendance;

import java.time.Instant;
import java.time.LocalDate;

public record DayUnlockResponse(
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
        Instant revokedAt) {}
