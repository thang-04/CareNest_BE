package com.carenest.dto.eduplan;

import java.time.LocalDate;

public record CurriculumSourceResponse(
        Long id,
        String code,
        String title,
        String documentNo,
        LocalDate issuedDate,
        String issuer,
        String officialUrl,
        String retrievedFromUrl,
        LocalDate retrievedAt,
        String scopeNote) {}
