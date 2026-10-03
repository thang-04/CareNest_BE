package com.carenest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Last step: allowed only after the OTP of this account has been verified.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Email hoặc số điện thoại không được để trống")
        String identifier,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
                message = "Mật khẩu phải từ 8 đến 100 ký tự, gồm cả chữ và số")
        String password,

        @NotBlank(message = "Vui lòng xác nhận mật khẩu")
        String confirmPassword
) {
}
