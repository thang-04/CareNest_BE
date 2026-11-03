package com.carenest.dto.health;

/** Kết quả đóng đợt/kỳ: số trẻ ghi vắng, phiếu khám còn nháp (chưa kết luận), phiếu theo dõi chờ trao đổi phụ huynh. */
public record HealthCheckCloseResponse(
        int absentCount, int notConcludedCount, int waitingFollowUpCount, HealthCheckResponse campaign) {}
