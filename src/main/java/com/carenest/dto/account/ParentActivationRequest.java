package com.carenest.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Phụ huynh kích hoạt tài khoản trên App bằng SĐT + mã SMS rồi tự đặt mật khẩu (AUTH-18, ADR-0024). */
public record ParentActivationRequest(
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotNull @Size(min = 8, max = 128) String newPassword,
        @NotNull @Size(min = 8, max = 128) String confirmPassword) {

    // Không để mã, mật khẩu lọt vào log khi record bị in ra
    @Override
    public String toString() {
        return "ParentActivationRequest[code=***, newPassword=***, confirmPassword=***]";
    }
}
