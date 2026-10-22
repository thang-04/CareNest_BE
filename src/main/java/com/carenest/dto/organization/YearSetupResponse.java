package com.carenest.dto.organization;

import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Số liệu cấu hình của một năm học cho module khác (xếp lớp, kiểm tra trước khi kích hoạt) đọc qua service,
 * không đọc thẳng bảng của school-structure. sourceYear = năm ACTIVE khác năm đang xem (nơi lấy trẻ để xếp lớp).
 */
public record YearSetupResponse(
        Year year,
        Year sourceYear,
        List<SetupClass> classes,
        List<SetupClass> sourceClasses,
        List<AgeGroupDetailResponse> ageGroups,
        List<CampusBrief> campuses,
        List<Long> campusesWithoutVicePrincipal,
        Set<Long> ageGroupsWithTeamLeader,
        boolean cutoffSet) {

    public record Year(Long id, String name, SchoolYearStatus status, LocalDate startDate, LocalDate endDate) {}

    public record SetupClass(
            Long id,
            String name,
            Long campusId,
            Long ageGroupId,
            Integer capacity,
            Long roomId,
            Integer roomCapacity,
            int teacherCount,
            boolean hasHomeroom) {}

    public record CampusBrief(Long id, String name) {}
}
