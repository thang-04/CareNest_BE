package com.carenest.utils.enums;

/** Lý do lỗi từ identity provider; service map sang ApiCode. */
public enum IdentityProviderErrorReason {
    /** Username/email đã tồn tại bên identity provider. */
    CONFLICT,
    /** Provider từ chối dữ liệu (vd. mật khẩu vi phạm policy, họ tên có ký tự bị cấm). */
    REJECTED,
    /** Không gọi được, timeout, lỗi cấu hình hoặc lỗi phía provider. */
    UNAVAILABLE
}
