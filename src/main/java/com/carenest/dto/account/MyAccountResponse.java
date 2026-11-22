package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;
import java.util.Map;

/**
 * Thông tin để client ẩn/hiện menu theo quyền; quyền thật vẫn kiểm ở từng API. {@code profileUpdateRequired}: phụ huynh
 * chưa có ảnh chân dung ⇒ App bắt cập nhật hồ sơ trước khi dùng (PCK-02, user chốt 2026-10-31).
 */
public record MyAccountResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        List<CareNestRole> roles,
        List<Permission> permissions,
        List<Permission> schoolWidePermissions,
        List<StaffAssignmentSummaryResponse> assignments,
        List<Long> childIds,
        boolean photoUploaded,
        boolean profileUpdateRequired,
        Map<Permission, Long> pinnedCampusPermissions) {

    /** pinnedCampusPermissions: mã sửa theo mảng HT giao tại một điểm trường cụ thể → điểm trường (AUTH-20). */
    public MyAccountResponse(
            Long userId,
            String username,
            String fullName,
            String email,
            List<CareNestRole> roles,
            List<Permission> permissions,
            List<Permission> schoolWidePermissions,
            List<StaffAssignmentSummaryResponse> assignments,
            List<Long> childIds,
            boolean photoUploaded,
            boolean profileUpdateRequired) {
        this(
                userId,
                username,
                fullName,
                email,
                roles,
                permissions,
                schoolWidePermissions,
                assignments,
                childIds,
                photoUploaded,
                profileUpdateRequired,
                Map.of());
    }
}
