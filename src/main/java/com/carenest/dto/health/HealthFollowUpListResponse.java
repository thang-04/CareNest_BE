package com.carenest.dto.health;

import java.util.List;
import java.util.Map;

/**
 * Danh sách phiếu theo dõi trong phạm vi, đã lọc. {@code counts} đếm trước khi lọc trạng thái/quá hạn, luôn đủ khóa
 * {@code WAITING_PARENT_CONTACT}, {@code PARENT_CONTACTED}, {@code EXTERNAL_RESULT_RECORDED}, {@code CLOSED},
 * {@code OVERDUE}.
 */
public record HealthFollowUpListResponse(List<HealthFollowUpResponse> items, Map<String, Integer> counts) {}
