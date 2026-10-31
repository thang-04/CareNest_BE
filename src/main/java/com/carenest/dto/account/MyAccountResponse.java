package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;

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
        List<StaffAssignmentSummaryResponse> assignments,
        List<Long> childIds,
        boolean photoUploaded,
        boolean profileUpdateRequired) {}
