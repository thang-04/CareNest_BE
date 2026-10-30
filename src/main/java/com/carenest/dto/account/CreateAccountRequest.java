package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * temporaryPassword: bắt buộc trừ khi có email và hệ thống gửi được email đặt mật khẩu. Mật khẩu chỉ chuyển
 * thẳng sang Keycloak (bắt đổi ở lần đăng nhập đầu), CareNest không lưu/log.
 */
public record CreateAccountRequest(
        @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$")
        String username,

        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 255) String email,
        @NotNull CareNestRole role,
        @Size(min = 8, max = 128) String temporaryPassword,
        // AUTH-15: bắt buộc khi người tạo là PHT (để biết campus); HT bỏ trống được
        @Valid StaffAssignmentScopeRequest assignment) {

    @Override
    public String toString() {
        return "CreateAccountRequest[username=" + username + ", role=" + role + "]";
    }
}
