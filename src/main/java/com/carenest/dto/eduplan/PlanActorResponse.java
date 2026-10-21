package com.carenest.dto.eduplan;

import java.time.Instant;

/** Người soạn / gửi / ban hành; at là thời điểm gửi hoặc ban hành (null với người soạn). */
public record PlanActorResponse(Long userId, String name, String title, Instant at) {}
