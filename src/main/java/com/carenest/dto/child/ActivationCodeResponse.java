package com.carenest.dto.child;

import com.carenest.utils.enums.SmsStatus;
import java.time.Instant;

/**
 * Gửi lại mã kích hoạt (AUTH-18). activationCode chỉ có khi không gửi được SMS thật (cổng giả hoặc FAILED) để in
 * phiếu; gửi SMS thật thành công ⇒ null.
 */
public record ActivationCodeResponse(
        Long userId, String username, SmsStatus smsStatus, Instant expiresAt, String activationCode) {

    // Không để mã lọt vào log khi record bị in ra
    @Override
    public String toString() {
        return "ActivationCodeResponse[userId=" + userId + ", smsStatus=" + smsStatus + ", expiresAt=" + expiresAt
                + "]";
    }
}
