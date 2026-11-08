package com.carenest.dto.health;

import java.util.List;

/**
 * Chi tiết đợt/kỳ: tiến độ theo lớp người xem được. {@code watchList}: trẻ cần chú ý — đợt khám: cần theo dõi, theo dõi
 * sát trước rồi theo tên; kỳ cân đo: có cảnh báo BMI (HLT-18), mức nặng trước rồi theo tên.
 */
public record HealthCheckDetailResponse(
        HealthCheckResponse campaign,
        List<HealthCheckClassProgress> classes,
        List<HealthCheckClassResponse.HealthCheckChild> watchList) {

    /** {@code bmiWarning}: số trẻ có mức BMI khác bình thường (kỳ cân đo; đợt khám luôn 0). */
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
            int bmiWarning,
            boolean canRecord) {}
}
