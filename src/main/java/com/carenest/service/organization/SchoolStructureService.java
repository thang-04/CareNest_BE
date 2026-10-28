package com.carenest.service.organization;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.ClassroomResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.Optional;

/**
 * Danh mục năm học, nhóm tuổi cho module khác (education-plan) đọc qua service, không qua repository; lớp hiện tại của
 * campus cho form phân công, lọc theo phạm vi người xem (AUTH-01, AUTH-02).
 */
public interface SchoolStructureService {

    List<SchoolYearResponse> findSchoolYears();

    Optional<SchoolYearResponse> findSchoolYear(Long schoolYearId);

    List<AgeGroupResponse> findAgeGroups();

    Optional<AgeGroupResponse> findAgeGroup(Long ageGroupId);

    /** Lớp của năm học đang diễn ra tại campus. */
    List<ClassroomResponse> listCurrentClassrooms(CurrentUser actor, Long campusId);
}
