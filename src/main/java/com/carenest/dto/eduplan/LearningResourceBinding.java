package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LearningResourceBinding(
        @NotBlank @Size(max = 180) String activityKey,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String activitySignature,
        @NotBlank @Size(max = 1000) String activityLabel) {}
