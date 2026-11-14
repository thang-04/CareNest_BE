package com.carenest.dto.eduplan;

/** Nơi học liệu đang được gắn: giáo án ngày + mục hoạt động; {@code locked} = chặn xóa/đổi tên (EDU-85). */
public record ResourceUsageResponse(
        Long attachmentId,
        String docKey,
        Long classroomId,
        String date,
        String activityKey,
        String activityLabel,
        String status,
        boolean locked) {}
