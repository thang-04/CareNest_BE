package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Xác nhận học liệu đã tải trực tiếp: {@code uploadId} từ bước xin liên kết, kèm tên gốc và mục hoạt động của ngày gắn vào. */
public record LearningResourceConfirmRequest(
        @NotBlank @Size(max = 120) String uploadId,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Pattern(regexp = LearningResourceBinding.KEY_PATTERN) String activityKey,
        @NotBlank @Size(max = 1000) String activityLabel) {

    public LearningResourceBinding binding() {
        return new LearningResourceBinding(activityKey, activityLabel);
    }
}
