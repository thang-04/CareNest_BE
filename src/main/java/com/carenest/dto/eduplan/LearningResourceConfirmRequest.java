package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Xác nhận học liệu đã tải trực tiếp: {@code uploadId} từ bước xin liên kết, kèm tên gốc, mục hoạt động và nhãn (EDU-87). */
public record LearningResourceConfirmRequest(
        @NotBlank @Size(max = 120) String uploadId,
        @NotBlank @Size(max = 255) String fileName,

        @NotBlank @Pattern(regexp = LearningResourceBinding.KEY_PATTERN)
        String activityKey,

        @NotBlank @Size(max = 1000) String activityLabel,
        @Valid ResourceTagsRequest tags) {

    public LearningResourceBinding binding() {
        return new LearningResourceBinding(activityKey, activityLabel);
    }
}
