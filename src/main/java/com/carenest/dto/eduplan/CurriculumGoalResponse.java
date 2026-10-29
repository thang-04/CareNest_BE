package com.carenest.dto.eduplan;

import java.util.List;

public record CurriculumGoalResponse(
        Long id,
        Long sourceId,
        Long domainId,
        String domainName,
        String subDomain,
        String groupNo,
        String groupText,
        String goalNo,
        String goalText,
        String sectionRef,
        Integer sortOrder,
        List<String> contents) {}
