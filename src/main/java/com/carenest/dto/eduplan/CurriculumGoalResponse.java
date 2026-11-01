package com.carenest.dto.eduplan;

import java.util.List;

public record CurriculumGoalResponse(
        Long id,
        Long sourceId,
        Long domainId,
        String domainName,
        Long subDomainId,
        String subDomain,
        Long expectedResultId,
        String groupNo,
        String groupText,
        String goalNo,
        String goalText,
        String sectionRef,
        Integer sortOrder,
        List<String> contents) {}
