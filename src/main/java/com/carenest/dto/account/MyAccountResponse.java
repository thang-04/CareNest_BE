package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.security.CareNestRole;
import java.util.List;

/** Thông tin để client ẩn/hiện menu; quyền thật vẫn kiểm ở từng API. */
public record MyAccountResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        List<CareNestRole> roles,
        List<StaffAssignmentSummaryResponse> assignments,
        List<Long> childIds
) {
}
