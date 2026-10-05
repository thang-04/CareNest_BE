package com.carenest.dto.account;

import com.carenest.security.CareNestRole;
import java.util.List;

public record MyAccountResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        List<CareNestRole> roles
) {
}
