package com.carenest.dto.eduplan;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import java.util.List;

/** Dữ liệu tham chiếu của form kế hoạch năm: lĩnh vực, thứ tự ký khi in, năm học, nhóm tuổi. */
public record EduConfigResponse(
        List<Domain> domains,
        List<String> yearSigners,
        List<SchoolYearResponse> schoolYears,
        List<AgeGroupResponse> ageGroups) {

    public record Domain(Long id, String name, String displayLabel, String shortLabel, Short sortOrder) {}
}
