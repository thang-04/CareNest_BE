package com.carenest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateChildRequest(
        @NotBlank(message = "Tên trẻ không được để trống")
        @Size(max = 100, message = "Tên trẻ tối đa 100 ký tự")
        String childName,

        @Past(message = "Ngày sinh phải là ngày trong quá khứ")
        LocalDate dateOfBirth
) {
}
