package com.carenest.dto.eduplan;

public record CurriculumContentResponse(
        Long id,
        Long sourceId,
        Long domainId,
        String domainName,
        String subDomain,
        String categoryNo,
        String category,
        String label,
        String text,
        String sectionRef,
        Integer sortOrder) {}
