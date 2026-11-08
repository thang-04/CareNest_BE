package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckAction;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đợt khám hoặc kỳ cân đo. {@code progress} tính trên các lớp người xem được: phiếu khám nháp ({@code inProgress}, đợt
 * đã đóng {@code notConcluded}), trẻ cần theo dõi / theo dõi sát theo kết luận bác sĩ ({@code watch}, {@code
 * closeWatch}), số trẻ theo mức BMI của kỳ cân đo ({@code bmi}, đợt khám luôn 0 — HLT-18).
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
        List<HealthCheckHistory> history) {

    public record HealthCheckProgress(
            int total,
            int recorded,
            int notRecorded,
            int inProgress,
            int notConcluded,
            int absent,
            int watch,
            int closeWatch,
            HealthBmiProgress bmi) {}

    /** Số trẻ có cảnh báo BMI theo từng mức (không đếm mức bình thường). */
    public record HealthBmiProgress(int severeThinness, int thinness, int overweightRisk, int overweight, int obese) {}

    public record HealthCheckHistory(
            HealthCheckAction action, Long userId, String userName, String reason, Instant at) {}
}
