package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * EDU-81: học liệu gắn một mục hoạt động của một ngày trong kế hoạch giáo dục ngày — {@code activityKey} dạng
 * {@code yyyy-MM-dd#mục} (mục: hoc, ngoai_troi, goc, chieu).
 */
public record LearningResourceBinding(
        @NotBlank @Pattern(regexp = LearningResourceBinding.KEY_PATTERN) String activityKey,
        @NotBlank @Size(max = 1000) String activityLabel) {

    public static final String KEY_PATTERN = "\\d{4}-\\d{2}-\\d{2}#(hoc|ngoai_troi|goc|chieu)";
}
