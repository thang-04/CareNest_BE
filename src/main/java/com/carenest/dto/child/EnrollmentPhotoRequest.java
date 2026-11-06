package com.carenest.dto.child;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

/** Mô tả OpenAPI của multipart; controller giữ map đầy đủ để phát hiện part lặp/sai. */
public record EnrollmentPhotoRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EnrollChildRequest payload,
        @Schema(type = "string", format = "binary") MultipartFile childPhoto,
        @Schema(type = "string", format = "binary") MultipartFile guardianPhoto0,
        @Schema(type = "string", format = "binary") MultipartFile guardianPhoto1,
        @Schema(type = "string", format = "binary") MultipartFile guardianPhoto2) {}
