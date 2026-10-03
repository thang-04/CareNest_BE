package com.carenest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeChildParentRequest(
        @NotBlank(message = "Số điện thoại phụ huynh không được để trống")
        @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại phụ huynh không hợp lệ")
        String parentPhone,

        // Used only when a new parent account is created.
        @NotBlank(message = "Tên phụ huynh không được để trống")
        @Size(max = 100, message = "Tên phụ huynh tối đa 100 ký tự")
        String parentName
) {
}
