package com.carenest.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sửa hồ sơ nhân sự (AUTH-25): SĐT là tên đăng nhập nên không sửa ở đây; email trống ⇒ giữ email cũ. */
public record UpdateAccountProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 255) String email) {}
