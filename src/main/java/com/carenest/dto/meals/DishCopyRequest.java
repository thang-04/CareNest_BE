package com.carenest.dto.meals;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sao chép món (kể cả món tham khảo) thành món của trường, giữ nguyên định lượng. Mã để trống ⇒ tự sinh. */
public record DishCopyRequest(
        @Size(max = 20) String code,
        @NotBlank @Size(max = 150) String name) {}
