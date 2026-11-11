package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Xác nhận học liệu đã tải trực tiếp: {@code uploadId} từ bước xin liên kết, kèm tên gốc và hoạt động gắn vào. */
public record LearningResourceConfirmRequest(
        @NotBlank @Size(max = 120) String uploadId,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 180) String activityKey,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String activitySignature,
        @NotBlank @Size(max = 1000) String activityLabel) {

    public LearningResourceBinding binding() {
        return new LearningResourceBinding(activityKey, activitySignature, activityLabel);
    }
}
