package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * phone: SĐT Việt Nam, chấp nhận +84/84 và dấu phân cách; chuẩn hóa thành tên đăng nhập. email: tùy chọn, chỉ để liên
 * hệ (AUTH-18). confirmUserId: bắt buộc khi SĐT đã có tài khoản, lấy từ bước tra SĐT sau khi người nhập xác nhận đúng
 * người.
 */
public record AddGuardianRequest(
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull GuardianRelationship relationship,
        @Email @Size(max = 255) String email,
        @Positive Long confirmUserId) {

    @Override
    public String toString() {
        return "AddGuardianRequest[relationship=" + relationship + ", confirmUserId=" + confirmUserId + "]";
    }
}
