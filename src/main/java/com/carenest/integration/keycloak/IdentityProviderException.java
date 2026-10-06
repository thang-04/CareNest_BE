package com.carenest.integration.keycloak;

import lombok.Getter;

@Getter
public class IdentityProviderException extends RuntimeException {

    public enum Reason {
        /** Username/email đã tồn tại bên identity provider. */
        CONFLICT,
        /** Provider từ chối dữ liệu (vd. mật khẩu vi phạm policy, họ tên có ký tự bị cấm). */
        REJECTED,
        /** Không gọi được, timeout, lỗi cấu hình hoặc lỗi phía provider. */
        UNAVAILABLE
    }

    private final Reason reason;

    public IdentityProviderException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
