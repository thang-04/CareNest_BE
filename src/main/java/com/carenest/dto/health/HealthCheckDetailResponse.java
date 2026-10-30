package com.carenest.dto.health;

import java.util.List;

/**
 * Chi tiết đợt/kỳ: tiến độ theo lớp người xem được. {@code watchList} (trẻ cần theo dõi) rỗng tới khi có phiếu khám.
 */
public record HealthCheckDetailResponse(
        HealthCheckResponse campaign,
        List<HealthCheckClassProgress> classes,
        List<HealthCheckClassResponse.HealthCheckChild> watchList) {

    public record HealthCheckClassProgress(
            Long classId,
            String className,
            Long ageGroupId,
            int total,
            int recorded,
            int notRecorded,
            int inProgress,
            int notConcluded,
            int absent,
            int watch,
            int closeWatch,
            boolean canRecord) {}
}
