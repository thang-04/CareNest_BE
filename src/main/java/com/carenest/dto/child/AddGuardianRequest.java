package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** phone: SĐT Việt Nam, chấp nhận +84/84 và dấu phân cách; chuẩn hóa thành tên đăng nhập. */
public record AddGuardianRequest(
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull GuardianRelationship relationship) {}
