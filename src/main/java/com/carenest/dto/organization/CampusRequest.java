package com.carenest.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CampusRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String shortName,
        @NotBlank @Size(max = 255) String address,

        @Pattern(regexp = "\\s*(\\d{8,20})?\\s*", message = "must contain 8-20 digits")
        String phone,

        Boolean isMain) {}
