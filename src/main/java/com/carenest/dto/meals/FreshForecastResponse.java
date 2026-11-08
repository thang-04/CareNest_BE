package com.carenest.dto.meals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bản dự trù hàng tươi. {@code id}, {@code createdAt}, {@code createdBy} null khi xem trước (chưa lưu). Số lượng theo
 * đơn vị gốc (g/ml), đã tính thải bỏ. {@code canManage}: người xem có {@code STOCK_MANAGE} ở điểm trường.
 */
public record FreshForecastResponse(
        Long id,
        Long campusId,
        LocalDate periodFrom,
        LocalDate periodTo,
        String note,
        List<FreshForecastCount> counts,
        List<FreshForecastDay> days,
        List<FreshForecastLine> lines,
        Instant createdAt,
        Long createdBy,
        boolean canManage) {

    /** {@code enrolledCount}: số trẻ đang học lúc lập; {@code expectedCount}: số dùng để tính suất. */
    public record FreshForecastCount(Long ageGroupId, String ageGroupName, int enrolledCount, int expectedCount) {}

    /** {@code planStatus} của thực đơn tuần chứa ngày: NONE, DRAFT, PUBLISHED; ngày nghỉ không có lượng. */
    public record FreshForecastDay(LocalDate date, boolean off, String planStatus) {}

    public record FreshForecastLine(StockFoodRef food, BigDecimal totalQty, List<FreshForecastDayQty> byDate) {}

    public record FreshForecastDayQty(LocalDate date, BigDecimal quantity) {}
}
