package com.carenest.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Sửa hồ sơ nhân sự (AUTH-25): SĐT là tên đăng nhập nên không sửa ở đây; email trống ⇒ giữ email cũ. workCampusId: cơ
 * sở công tác, chỉ với GV (AUTH-15); trống ⇒ giữ cơ sở cũ, GV chưa có cơ sở thì bắt buộc chọn.
 */
public record UpdateAccountProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 255) String email,
        @Positive Long workCampusId) {

    public UpdateAccountProfileRequest(String fullName, String email) {
        this(fullName, email, null);
    }
}
