package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.List;

/** Một dòng danh sách tài khoản: role là bản sao role Keycloak (null = chưa rõ), kèm phân công đang hiệu lực. */
public record AccountSummaryResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        CareNestRole role,
        UserStatus status,
        List<StaffAssignmentSummaryResponse> activeAssignments) {}
