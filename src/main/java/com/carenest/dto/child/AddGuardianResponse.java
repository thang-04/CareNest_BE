package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.UserStatus;

/**
 * newAccount = true ⇒ temporaryPassword là mật khẩu tạm hệ thống sinh, chỉ trả đúng lần này để in phiếu (AUTH-18);
 * false ⇒ SĐT đã có tài khoản phụ huynh, chỉ thêm liên kết, temporaryPassword = null.
 */
public record AddGuardianResponse(
        Long userId,
        String username,
        String fullName,
        GuardianRelationship relationship,
        UserStatus status,
        boolean newAccount,
        String temporaryPassword) {

    @Override
    public String toString() {
        return "AddGuardianResponse[userId=" + userId + ", newAccount=" + newAccount + "]";
    }
}
