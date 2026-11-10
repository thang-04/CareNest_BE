package com.carenest.dto.meals;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "^[0-9+ .]{8,20}$") String phone,
        @Size(max = 300) String address,
        @Pattern(regexp = "^[0-9]{9,12}$") String idCard,
        @Size(max = 100) String deliveryPerson) {}
