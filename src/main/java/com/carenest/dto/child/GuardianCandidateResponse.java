package com.carenest.dto.child;

/**
 * exists = false ⇒ thêm phụ huynh sẽ tạo tài khoản mới. exists = true ⇒ người nhập đối chiếu {@code maskedFullName}
 * với người thật rồi gửi {@code confirmUserId} = {@code userId} (AUTH-18). {@code staffAccount} = tài khoản nhân sự
 * sẽ được thêm vai trò phụ huynh.
 */
public record GuardianCandidateResponse(
        boolean exists, Long userId, String maskedFullName, boolean staffAccount, boolean alreadyLinked) {

    public static GuardianCandidateResponse none() {
        return new GuardianCandidateResponse(false, null, null, false, false);
    }
}
