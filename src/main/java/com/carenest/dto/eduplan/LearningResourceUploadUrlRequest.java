package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Xin liên kết tải học liệu trực tiếp lên kho: tên gốc, loại và dung lượng khai báo (được ký vào liên kết). */
public record LearningResourceUploadUrlRequest(
        @NotBlank @Size(max = 255) String fileName, @NotBlank @Size(max = 120) String contentType, @Positive long size) {}
