package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** EDU-83: dán link YouTube/Google vào một mục hoạt động của giáo án ngày. */
public record ResourceLinkRequest(
        @NotBlank @Size(max = 2000) String url,
        @Size(max = 255) String title,

        @NotBlank @Pattern(regexp = LearningResourceBinding.KEY_PATTERN)
        String activityKey,

        @NotBlank @Size(max = 1000) String activityLabel,
        @Valid ResourceTagsRequest tags) {

    public LearningResourceBinding binding() {
        return new LearningResourceBinding(activityKey, activityLabel);
    }
}
