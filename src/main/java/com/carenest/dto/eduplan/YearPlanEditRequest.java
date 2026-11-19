package com.carenest.dto.eduplan;

import java.util.UUID;

public record YearPlanEditRequest(UUID sessionId, Long revision, UUID requestId) {
    public YearPlanEditRequest(Long revision, UUID requestId) {
        this(null, revision, requestId);
    }
}
