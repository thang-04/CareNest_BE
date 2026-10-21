package com.carenest.mapper.organization;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.SchoolYear;
import org.springframework.stereotype.Component;

@Component
public class SchoolStructureMapper {

    public SchoolYearResponse toResponse(SchoolYear schoolYear) {
        return new SchoolYearResponse(
                schoolYear.getId(), schoolYear.getName(), schoolYear.getStartDate(), schoolYear.getEndDate());
    }

    public AgeGroupResponse toResponse(AgeGroup ageGroup) {
        return new AgeGroupResponse(
                ageGroup.getId(),
                ageGroup.getCode(),
                ageGroup.getName(),
                ageGroup.getShortName(),
                ageGroup.getSortOrder());
    }
}
