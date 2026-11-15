package com.carenest.dto.eduplan;

import java.time.Instant;

public record EduDurationPolicyResponse(
        long revision,
        EduDurationPolicyRequest.Limits theme,
        EduDurationPolicyRequest.Limits branch,
        boolean canConfigure,
        Instant updatedAt) {}
