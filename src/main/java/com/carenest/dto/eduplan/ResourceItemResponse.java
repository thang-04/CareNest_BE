package com.carenest.dto.eduplan;

import java.time.Instant;
import java.util.List;

/** Học liệu trong kho kèm số lượt đang dùng, mức gợi ý ({@code GOAL}/{@code THEME}) và quyền của người xem. */
public record ResourceItemResponse(
        Long id,
        String kind,
        String title,
        String url,
        String fileName,
        String contentType,
        Long byteSize,
        Long ownerId,
        boolean mine,
        String scope,
        Long classroomId,
        String shareStatus,
        String shareReason,
        Long ageGroupId,
        boolean hidden,
        List<ResourceTagResponse> tags,
        long usageCount,
        String match,
        boolean attached,
        boolean canEdit,
        boolean canReview,
        Instant updatedAt) {}
