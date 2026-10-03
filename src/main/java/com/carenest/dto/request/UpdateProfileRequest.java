package com.carenest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Login identifiers (email, phone number) are changed by the school management only.
public record UpdateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName
) {
}
