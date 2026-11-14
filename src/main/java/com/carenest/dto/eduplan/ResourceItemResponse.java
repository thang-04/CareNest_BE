package com.carenest.dto.eduplan;

import java.time.Instant;
import java.util.List;

/** Học liệu trong kho cá nhân (EDU-84) kèm chủ đề đã dạy, số lượt đang dùng và mức gợi ý ({@code GOAL}/{@code THEME}). */
public record ResourceItemResponse(
        Long id,
        String kind,
        String title,
        String url,
        String fileName,
        String contentType,
        Long byteSize,
        Long ageGroupId,
        List<ResourceTagResponse> tags,
        List<TaughtTheme> taughtThemes,
        long usageCount,
        String match,
        boolean attached,
        Instant updatedAt) {

    /** EDU-87: chủ đề đã dạy (gộp nhãn không trùng theo tên + năm học). */
    public record TaughtTheme(String name, Long schoolYearId, String schoolYearName) {}
}
