package com.carenest.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mật khẩu tạm chỉ chuyển sang Keycloak (bắt đổi ở lần đăng nhập kế tiếp); CareNest không lưu/log. */
public record ResetTemporaryPasswordRequest(
        @NotBlank @Size(min = 8, max = 128) String temporaryPassword) {

    @Override
    public String toString() {
        return "ResetTemporaryPasswordRequest[temporaryPassword=***]";
    }
}
