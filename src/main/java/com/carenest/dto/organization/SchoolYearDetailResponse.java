package com.carenest.dto.organization;

import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;

public record SchoolYearDetailResponse(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate semester1Start,
        LocalDate semester1End,
        LocalDate semester2Start,
        LocalDate semester2End,
        SchoolYearStatus status,
        long classCount,
        Instant activatedAt,
        Long activatedBy,
        Instant closedAt,
        Long closedBy,
        Instant createdAt,
        Instant updatedAt) {}
