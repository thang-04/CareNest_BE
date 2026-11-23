package com.carenest.dto.eduplan;

import java.util.List;

/**
 * Tab Soạn/duyệt hồ sơ (EDU-108): mỗi loại hồ sơ có vai trò soạn, chuỗi duyệt có thứ tự (workflow_policy) và vai trò
 * đang có mã soạn/duyệt (ma trận quyền). Vai trò: PRINCIPAL, VICE_PRINCIPAL, TEAM_LEADER, TEACHER.
 */
public record EduWorkflowPermissionResponse(long revision, List<Document> documents) {
    public record Document(
            String docType,
            String policyKey,
            String editPermission,
            String approvePermission,
            List<String> creators,
            List<String> reviewers,
            List<String> editRoles,
            List<String> approveRoles) {}
}
