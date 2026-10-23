package com.carenest.dto.eduplan;

import java.time.Instant;

/** Người soạn / gửi / ban hành / đang sửa; at là thời điểm gửi, ban hành hoặc bắt đầu sửa (null với người soạn). */
public record PlanActorResponse(Long userId, String name, String title, Instant at) {}
