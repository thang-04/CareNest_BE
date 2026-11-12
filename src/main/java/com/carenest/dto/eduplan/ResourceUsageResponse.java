package com.carenest.dto.eduplan;

/** Nơi học liệu đang được gắn: giáo án ngày + mục hoạt động. */
public record ResourceUsageResponse(
        Long attachmentId, String docKey, Long classroomId, String date, String activityKey, String activityLabel) {}
