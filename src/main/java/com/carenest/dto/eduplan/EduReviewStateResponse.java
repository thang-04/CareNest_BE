package com.carenest.dto.eduplan;

import java.time.Instant;
import java.util.List;

/**
 * Trạng thái quy trình duyệt của tài liệu giáo dục (EDU-54).
 */
public record EduReviewStateResponse(
        Long policyRevision,
        Integer round,
        List<String> roles,
        String waitingRole,
        List<ApprovalItem> approvals,
        String authorCoverage,
        Instant authorTrackingSince,
        String completionMode,
        Instant completedAt,
        Object completionPolicy,
        Object printSnapshot) {

    public record ApprovalItem(Long userId, String name, String roleKey, Instant at, String note, int round) {}
}
