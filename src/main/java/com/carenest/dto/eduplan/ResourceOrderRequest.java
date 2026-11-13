package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** EDU-82: thứ tự mới của mọi dòng gắn trong một mục hoạt động (id dòng gắn, đầu danh sách = đứng đầu). */
public record ResourceOrderRequest(
        @NotBlank @Pattern(regexp = LearningResourceBinding.KEY_PATTERN) String activityKey,
        @NotEmpty @Size(max = 20) List<Long> ids) {}
