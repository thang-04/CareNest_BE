package com.carenest.dto.health;

/** Kết quả đóng đợt/kỳ: số trẻ ghi vắng, phiếu khám còn nháp (chưa kết luận). */
public record HealthCheckCloseResponse(int absentCount, int notConcludedCount, HealthCheckResponse campaign) {}
