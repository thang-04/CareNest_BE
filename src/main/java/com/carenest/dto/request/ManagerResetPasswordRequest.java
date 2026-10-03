package com.carenest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ManagerResetPasswordRequest(
        // Temporary password; the user must change it at the next login.
        @NotBlank(message = "Mật khẩu không được để trống")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
                message = "Mật khẩu phải từ 8 đến 100 ký tự, gồm cả chữ và số")
        String password
) {
}
