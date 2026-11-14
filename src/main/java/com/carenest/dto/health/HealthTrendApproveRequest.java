package com.carenest.dto.health;

/**
 * Duyệt giải thích xu hướng (HLT-05, HLT-21); {@code content} tùy chọn: bản đã sửa từng mục (mã cảnh báo phải có trong
 * bản phân tích, giới hạn độ dài như bản AI), trống = giữ bản AI.
 */
public record HealthTrendApproveRequest(HealthTrendContent content) {}
