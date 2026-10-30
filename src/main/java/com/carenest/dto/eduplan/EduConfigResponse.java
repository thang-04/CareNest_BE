package com.carenest.dto.eduplan;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import java.util.List;

/**
 * Cấu hình kế hoạch giáo dục (EDU-11) và dữ liệu tham chiếu của form: lĩnh vực đang dùng, năm học, nhóm tuổi,
 * chuỗi người ký theo loại tài liệu, dòng hoạt động của kế hoạch tuần.
 */
public record EduConfigResponse(
        List<Domain> domains,
        List<SchoolYearResponse> schoolYears,
        List<AgeGroupResponse> ageGroups,
        Signers signers,
        List<WeekRow> weekRows) {

    public record Domain(
            Long id, String name, String displayLabel, String shortLabel, Short sortOrder, boolean standard) {}

    /** Vai trò ký theo thứ tự (TEAM_LEADER, VICE_PRINCIPAL, PRINCIPAL); week = hồ sơ chủ đề. */
    public record Signers(List<String> year, List<String> branch, List<String> week) {}

    /** canMerge: được gộp cả tuần; allWeek: mặc định gộp; extraLabel: phần chung cả tuần dưới 5 ô ngày. */
    public record WeekRow(String key, String label, boolean canMerge, boolean allWeek, String extraLabel) {}
}
