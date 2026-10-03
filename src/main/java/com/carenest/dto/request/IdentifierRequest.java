package com.carenest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IdentifierRequest(
        // Email or phone number
        @NotBlank(message = "Email hoặc số điện thoại không được để trống")
        String identifier
) {
}
