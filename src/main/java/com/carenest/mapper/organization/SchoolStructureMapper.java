package com.carenest.mapper.organization;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.SchoolYear;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class SchoolStructureMapper {

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public SchoolYearResponse toResponse(SchoolYear schoolYear) {
        return new SchoolYearResponse(
                schoolYear.getId(), schoolYear.getName(), schoolYear.getStartDate(), schoolYear.getEndDate());
    }

    public SchoolYearDetailResponse toDetailResponse(SchoolYear schoolYear, long classCount) {
        return toDetailResponse(schoolYear, classCount, LocalDate.now(SCHOOL_ZONE));
    }

    public SchoolYearDetailResponse toDetailResponse(SchoolYear schoolYear, long classCount, LocalDate today) {
        return new SchoolYearDetailResponse(
                schoolYear.getId(),
                schoolYear.getName(),
                schoolYear.getStartDate(),
                schoolYear.getEndDate(),
                schoolYear.getSemester1Start(),
                schoolYear.getSemester1End(),
                schoolYear.getSemester2Start(),
                schoolYear.getSemester2End(),
                schoolYear.getStatus(),
                classCount,
                schoolYear.getActivatedAt(),
                schoolYear.getActivatedBy(),
                schoolYear.getClosedAt(),
                schoolYear.getClosedBy(),
                schoolYear.getCreatedAt(),
                schoolYear.getUpdatedAt(),
                schoolYear.getPrepDate(),
                schoolYear.isPreparing(today));
    }

    public AgeGroupDetailResponse toDetailResponse(AgeGroup ageGroup) {
        return new AgeGroupDetailResponse(
                ageGroup.getId(),
                ageGroup.getCode(),
                ageGroup.getName(),
                ageGroup.getShortName(),
                ageGroup.getSortOrder(),
                toInteger(ageGroup.getAgeFromMonths()),
                toInteger(ageGroup.getAgeToMonths()),
                ageGroup.getMealSessions(),
                ageGroup.getMealSessions().size(),
                toInteger(ageGroup.getMaxClassSize()),
                ageGroup.getNutritionNote());
    }

    public AgeGroupResponse toResponse(AgeGroup ageGroup) {
        return new AgeGroupResponse(
                ageGroup.getId(),
                ageGroup.getCode(),
                ageGroup.getName(),
                ageGroup.getShortName(),
                ageGroup.getSortOrder());
    }

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }
}
