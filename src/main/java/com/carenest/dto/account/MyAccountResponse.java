package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;

/** Thông tin để client ẩn/hiện menu theo quyền; quyền thật vẫn kiểm ở từng API. */
public record MyAccountResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        List<CareNestRole> roles,
        List<Permission> permissions,
        List<StaffAssignmentSummaryResponse> assignments,
        List<Long> childIds) {}
