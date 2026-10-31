package com.carenest.dto.eduplan;

import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình kế hoạch giáo dục (EDU-11) và dữ liệu tham chiếu của form: lĩnh vực đang dùng, năm học, nhóm tuổi,
 * chuỗi người ký theo loại tài liệu, dòng hoạt động của kế hoạch tuần.
 */
public record EduConfigResponse(
        List<Domain> domains,
        List<SchoolYearResponse> schoolYears,
        List<AgeGroupResponse> ageGroups,
        Signers signers,
        List<WeekRow> weekRows,
        SignerNames signerNames) {

    public record Domain(
            Long id, String name, String displayLabel, String shortLabel, Short sortOrder, boolean standard) {}

    /** Vai trò ký theo thứ tự (TEAM_LEADER, VICE_PRINCIPAL, PRINCIPAL); week = hồ sơ chủ đề. */
    public record Signers(List<String> year, List<String> branch, List<String> week) {}

    /**
     * Họ tên in dưới chỗ ký tay trên bản in (EDU-21): Hiệu trưởng, Phó HT đang hoạt động (client chỉ in khi đúng một người;
     * người đã ban hành / duyệt thì lấy theo lịch sử) và Tổ trưởng theo năm học (tên năm, vd. 2027-2028) rồi mã nhóm tuổi
     * (vd. ag-4): phân công hiệu lực hôm nay nếu hôm nay thuộc năm học đó, ngược lại phân công hiệu lực ngày đầu năm học.
     */
    public record SignerNames(
            List<String> principals, List<String> vicePrincipals, Map<String, Map<String, List<String>>> teamLeaders) {}

    /** canMerge: được gộp cả tuần; allWeek: mặc định gộp; extraLabel: phần chung cả tuần dưới 5 ô ngày. */
    public record WeekRow(String key, String label, boolean canMerge, boolean allWeek, String extraLabel) {}
}
