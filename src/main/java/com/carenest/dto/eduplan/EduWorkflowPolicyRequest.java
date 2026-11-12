package com.carenest.dto.eduplan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Request cập nhật quy trình soạn/duyệt kế hoạch giáo dục (EDU-54).
 */
public record EduWorkflowPolicyRequest(
        @NotNull Long revision, @NotNull Map<String, @Valid PolicyEntry> policies) {
    public record PolicyEntry(List<String> creators, List<String> reviewers) {}
}
