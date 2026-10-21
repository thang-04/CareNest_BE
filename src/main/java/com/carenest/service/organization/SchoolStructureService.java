package com.carenest.service.organization;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import java.util.List;
import java.util.Optional;

/** Danh mục năm học, nhóm tuổi cho module khác (education-plan) đọc qua service, không qua repository. */
public interface SchoolStructureService {

    List<SchoolYearResponse> findSchoolYears();

    Optional<SchoolYearResponse> findSchoolYear(Long schoolYearId);

    List<AgeGroupResponse> findAgeGroups();

    Optional<AgeGroupResponse> findAgeGroup(Long ageGroupId);
}
