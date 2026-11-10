package com.carenest.dto.eduplan;

import java.time.Instant;

public record LearningResourceResponse(
        Long id,
        String docKey,
        String activityKey,
        String activitySignature,
        String activityLabel,
        String fileName,
        String contentType,
        long byteSize,
        Long uploadedBy,
        Instant createdAt,
        boolean active) {}
