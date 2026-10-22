package com.carenest.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CampusRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String shortName,
        @NotBlank @Size(max = 255) String address,

        // Số Việt Nam: di động 10 số (03/05/07/08/09) hoặc cố định 11 số (02x); nhận +84/84, dấu cách, chấm, gạch ngang
        @Pattern(
                regexp = "\\s*((\\+?84|0)[\\s.\\-]*([35789]([\\s.\\-]?\\d){8}|2([\\s.\\-]?\\d){9}))?\\s*",
                message = "must be a Vietnamese phone number, e.g. 0912345678")
        String phone,

        Boolean isMain) {}
