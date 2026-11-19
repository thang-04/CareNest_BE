package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EduDurationPolicyRequest(
        @NotNull Long revision,
        @NotNull @Valid Limits theme,
        @NotNull @Valid Limits branch) {
    public record Limits(@Positive int minWeeks, @Positive int maxWeeks) {}
}
