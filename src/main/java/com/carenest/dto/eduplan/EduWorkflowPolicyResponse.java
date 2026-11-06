package com.carenest.dto.eduplan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Snapshot cấu hình quy trình soạn/duyệt kế hoạch giáo dục (EDU-54).
 */
public record EduWorkflowPolicyResponse(
        long revision, Map<String, PolicyEntry> policies, boolean canConfigure, Instant updatedAt) {
    public record PolicyEntry(List<String> creators, List<String> reviewers) {}
}
