package com.carenest.dto.account;

import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;

/** setupEmailSent = false: người tạo phải giao mật khẩu tạm; assignment = phân công vừa tạo kèm (null nếu không). */
public record CreateAccountResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        CareNestRole role,
        UserStatus status,
        boolean setupEmailSent,
        StaffAssignmentResponse assignment) {}
