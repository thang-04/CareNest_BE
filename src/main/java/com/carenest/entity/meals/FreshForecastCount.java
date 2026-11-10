package com.carenest.entity.meals;

/**
 * Sĩ số một nhóm tuổi của bản dự trù: số đang học lúc lập (tham chiếu) và số dự kiến PHT chốt để tính suất. Lưu trong
 * cột jsonb {@code fresh_forecasts.counts} vì chỉ vài dòng và không bao giờ sửa riêng.
 */
public record FreshForecastCount(Long ageGroupId, int enrolledCount, int expectedCount) {

    public static FreshForecastCount of(Long ageGroupId, int enrolledCount, int expectedCount) {
        return new FreshForecastCount(ageGroupId, enrolledCount, expectedCount);
    }
}
