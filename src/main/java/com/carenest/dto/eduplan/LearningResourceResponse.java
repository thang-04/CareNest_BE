package com.carenest.dto.eduplan;

import java.time.Instant;

/** Một dòng gắn học liệu của giáo án ngày ({@code id} = id dòng gắn); giữ trường cũ, thêm thông tin học liệu kho (EDU-82). */
public record LearningResourceResponse(
        Long id,
        String docKey,
        String activityKey,
        String activityLabel,
        String fileName,
        String contentType,
        long byteSize,
        Long uploadedBy,
        Instant createdAt,
        boolean active,
        Long itemId,
        String kind,
        String title,
        String url,
        String scope) {}
