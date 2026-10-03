package com.carenest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Sent as multipart form fields together with the "photo" file part.
public record PickupPersonRequest(
        @NotBlank(message = "Họ tên người đón không được để trống")
        @Size(max = 100, message = "Họ tên người đón tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Quan hệ với trẻ không được để trống")
        @Size(max = 50, message = "Quan hệ với trẻ tối đa 50 ký tự")
        String relationship,

        @NotBlank(message = "Số điện thoại người đón không được để trống")
        @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại người đón không hợp lệ")
        String phoneNumber
) {
}
