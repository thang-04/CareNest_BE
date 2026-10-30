package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckAction;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đợt khám hoặc kỳ cân đo. {@code progress} tính trên các lớp người xem được; số liệu phiếu khám, phiếu theo dõi
 * ({@code inProgress}, {@code notConcluded}, {@code watch}, {@code closeWatch}, {@code *FollowUpCount}) luôn 0 tới khi có
 * phiếu khám Phụ lục II.
 */
public record HealthCheckResponse(
        Long id,
        HealthCheckKind kind,
        String name,
        Long campusId,
        String campusName,
        LocalDate startDate,
        LocalDate endDate,
        String examiner,
        String note,
        HealthCheckStatus status,
        Long createdBy,
        String createdByName,
        Instant createdAt,
        Long closedBy,
        String closedByName,
        Instant closedAt,
        Instant cancelledAt,
        boolean hasResults,
        HealthCheckProgress progress,
        int waitingFollowUpCount,
        int overdueFollowUpCount,
        List<HealthCheckHistory> history) {

    public record HealthCheckProgress(
            int total,
            int recorded,
            int notRecorded,
            int inProgress,
            int notConcluded,
            int absent,
            int watch,
            int closeWatch) {}

    public record HealthCheckHistory(
            HealthCheckAction action, Long userId, String userName, String reason, Instant at) {}
}
