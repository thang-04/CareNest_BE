package com.carenest.dto.child;

import com.carenest.utils.enums.UserStatus;

/**
 * exists = false ⇒ thêm phụ huynh sẽ tạo tài khoản mới. exists = true ⇒ người nhập đối chiếu {@code maskedFullName}
 * với người thật rồi gửi {@code confirmUserId} = {@code userId} (AUTH-18). {@code staffAccount} = tài khoản nhân sự
 * sẽ được thêm vai trò phụ huynh. status = INACTIVE ⇒ liên kết xong tài khoản vẫn khóa tới khi HT/PHT mở lại.
 */
public record GuardianCandidateResponse(
        boolean exists,
        Long userId,
        String maskedFullName,
        boolean staffAccount,
        boolean alreadyLinked,
        UserStatus status) {

    public static GuardianCandidateResponse none() {
        return new GuardianCandidateResponse(false, null, null, false, false, null);
    }
}
