package com.carenest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the editable fields: a blank email or phone number removes it.
 * Staff must keep an email, parents must keep a phone number.
 */
public record UpdateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @Pattern(regexp = "^((0|\\+84)\\d{9})?$", message = "Số điện thoại không hợp lệ")
        String phoneNumber,

        // For example: kế toán, y tế, bảo vệ, cấp dưỡng.
        @Size(max = 100, message = "Chức vụ tối đa 100 ký tự")
        String position
) {
}
