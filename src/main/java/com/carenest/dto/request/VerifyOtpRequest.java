package com.carenest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyOtpRequest(
        @NotBlank(message = "Email hoặc số điện thoại không được để trống")
        String identifier,

        @NotNull(message = "Mã OTP không được để trống")
        @Min(value = 100000, message = "Mã OTP gồm 6 chữ số")
        @Max(value = 999999, message = "Mã OTP gồm 6 chữ số")
        Integer otp
) {
}
