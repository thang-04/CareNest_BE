package com.carenest.dto.health;

/** Duyệt giải thích xu hướng (HLT-05); {@code summary} tùy chọn: câu chữ đã sửa (tối đa 2000 ký tự), trống = giữ bản AI. */
public record HealthTrendApproveRequest(String summary) {}
