package com.carenest.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tra SĐT trước khi tạo tài khoản/thêm phụ huynh; gửi trong body để SĐT không nằm trong URL/log truy cập. */
public record PhoneLookupRequest(@NotBlank @Size(max = 20) String phone) {}
