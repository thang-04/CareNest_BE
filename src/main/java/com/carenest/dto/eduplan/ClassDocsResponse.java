package com.carenest.dto.eduplan;

import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tài liệu kế hoạch của lớp người xem được (EDU-12) kèm lớp và giáo viên, tổ trưởng để màn hình hiện tên, chuỗi duyệt.
 * Tài liệu giữ nguyên dạng soạn của Web: id = khóa tài liệu (vd. {@code bp_<lớp>_<kế hoạch năm>}), nội dung, trạng thái,
 * người duyệt, lịch sử.
 */
public record ClassDocsResponse(
        List<ObjectNode> branchPlans,
        List<ObjectNode> classThemePlans,
        List<ObjectNode> weekPlans,
        List<ClassItem> classes,
        List<StaffItem> staff,
        List<CreationCapability> creationCapabilities,
        // Spec 003: gói học kỳ, hồ sơ chủ đề (kèm hạn), giáo án ngày; năm học chưa cấu hình học kỳ
        List<ObjectNode> semesterPackages,
        List<ObjectNode> themeDossiers,
        List<ObjectNode> dayPlans,
        boolean semesterConfigMissing) {

    public record CreationCapability(
            Long classId, String kind, boolean canCreate, String requires, String reasonCode) {}

    /** id dạng chuỗi như id lớp trên đường dẫn của Web; ageGroupId = mã nhóm tuổi (vd. ag-3). */
    public record ClassItem(
            String id, String name, String ageGroupId, Long campusId, List<Long> teacherIds, Long homeroomTeacherId) {}

    /** role: TEACHER hoặc TEAM_LEADER (tổ trưởng của ageGroupId). */
    public record StaffItem(Long id, String fullName, String role, String ageGroupId) {}
}
