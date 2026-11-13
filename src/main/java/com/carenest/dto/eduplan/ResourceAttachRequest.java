package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** EDU-82: gắn học liệu có sẵn trong kho vào một mục hoạt động. */
public record ResourceAttachRequest(
        @NotEmpty @Size(max = 20) List<Long> itemIds,

        @NotBlank @Pattern(regexp = LearningResourceBinding.KEY_PATTERN)
        String activityKey,

        @NotBlank @Size(max = 1000) String activityLabel) {

    public LearningResourceBinding binding() {
        return new LearningResourceBinding(activityKey, activityLabel);
    }
}
