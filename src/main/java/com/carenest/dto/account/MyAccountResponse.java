package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;

/**
 * Thông tin để client ẩn/hiện menu theo quyền; quyền thật vẫn kiểm ở từng API. {@code profileUpdateRequired} luôn
 * {@code false}: ảnh phụ huynh lấy từ hồ sơ tiếp nhận, App không bắt tải ảnh (PCK-02, user chốt 2026-11-13).
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
        boolean profileUpdateRequired) {}
