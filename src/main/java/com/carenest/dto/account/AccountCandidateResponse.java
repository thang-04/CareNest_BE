package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;

/**
 * exists = false ⇒ tạo tài khoản mới. primaryRole = PARENT ⇒ tạo nhân sự sẽ thêm vai trò vào tài khoản phụ huynh này
 * sau khi người tạo đối chiếu {@code maskedFullName} và gửi {@code confirmUserId}; role khác ⇒ SĐT đã là nhân sự.
 */
public record AccountCandidateResponse(boolean exists, Long userId, String maskedFullName, CareNestRole primaryRole) {

    public static AccountCandidateResponse none() {
        return new AccountCandidateResponse(false, null, null, null);
    }
}
