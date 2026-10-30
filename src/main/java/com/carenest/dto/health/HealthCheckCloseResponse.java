package com.carenest.dto.health;

/** Kết quả đóng đợt/kỳ: số trẻ ghi vắng; phiếu chưa kết luận, phiếu theo dõi chờ trao đổi luôn 0 tới khi có phiếu khám. */
public record HealthCheckCloseResponse(
        int absentCount, int notConcludedCount, int waitingFollowUpCount, HealthCheckResponse campaign) {}
