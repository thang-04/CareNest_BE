package com.carenest.dto.request;

import com.carenest.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại không hợp lệ")
        String phoneNumber,

        // Temporary password; the user must change it at first login.
        @NotBlank(message = "Mật khẩu không được để trống")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
                message = "Mật khẩu phải từ 8 đến 100 ký tự, gồm cả chữ và số")
        String password,

        // For example: kế toán, y tế, bảo vệ, cấp dưỡng. Display only.
        @Size(max = 100, message = "Chức vụ tối đa 100 ký tự")
        String position,

        // VICE_PRINCIPAL, TEACHER or STAFF.
        @NotNull(message = "Vai trò không được để trống")
        Role role
) {
}
