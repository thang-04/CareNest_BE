package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * phone: SĐT di động, chuẩn hóa thành tên đăng nhập (bếp dùng SĐT bếp của cơ sở). temporaryPassword: bắt buộc trừ khi
 * có email và hệ thống gửi được email đặt mật khẩu; chỉ chuyển thẳng sang Keycloak, CareNest không lưu/log.
 * confirmUserId: bắt buộc khi SĐT đã là tài khoản phụ huynh (thêm vai trò nhân sự vào tài khoản đó).
 */
public record CreateAccountRequest(
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 255) String email,
        @NotNull CareNestRole role,
        @Size(min = 8, max = 128) String temporaryPassword,
        // AUTH-15: bắt buộc khi người tạo là PHT (để biết campus); HT bỏ trống được
        @Valid StaffAssignmentScopeRequest assignment,
        @Positive Long confirmUserId) {

    @Override
    public String toString() {
        return "CreateAccountRequest[role=" + role + "]";
    }
}
