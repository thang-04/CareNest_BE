package com.carenest.dto.account;

import com.carenest.entity.account.UserStatus;
import com.carenest.security.CareNestRole;

/** setupEmailSent = false: Admin phải giao mật khẩu tạm cho người dùng. */
public record CreateAccountResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        CareNestRole role,
        UserStatus status,
        boolean setupEmailSent
) {
}
