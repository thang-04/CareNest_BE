package com.carenest.dto.meals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bản dự trù hàng tươi. {@code id}, {@code createdAt}, {@code createdBy} null khi xem trước (chưa lưu). Số lượng theo
 * đơn vị gốc (g/ml), đã tính thải bỏ. {@code canManage}: người xem có {@code STOCK_MANAGE} ở điểm trường.
 * {@code dryLines}: hàng khô cần nhập thêm (NUT-11, P-24 user đổi 08/11/2026) — tính lại mỗi lần xem theo tồn hiện tại,
 * không lưu; kỳ đã qua thì rỗng.
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
        List<DryLine> dryLines,
        Instant createdAt,
        Long createdBy,
        boolean canManage) {

    /** {@code enrolledCount}: số trẻ đang học lúc lập; {@code expectedCount}: số dùng để tính suất. */
    public record FreshForecastCount(Long ageGroupId, String ageGroupName, int enrolledCount, int expectedCount) {}

    /** {@code planStatus} của thực đơn tuần chứa ngày: NONE, DRAFT, PUBLISHED; ngày nghỉ không có lượng. */
    public record FreshForecastDay(LocalDate date, boolean off, String planStatus) {}

    public record FreshForecastLine(StockFoodRef food, BigDecimal totalQty, List<FreshForecastDayQty> byDate) {}

    public record FreshForecastDayQty(LocalDate date, BigDecimal quantity) {}

    /**
     * {@code requiredQty}: nhu cầu từ hôm nay tới hết kỳ (ngày chưa chốt suất); {@code availableQty}: tồn còn dùng được;
     * {@code shortQty}: lượng cần nhập thêm; {@code shortFrom}: ngày bắt đầu thiếu.
     */
    public record DryLine(
            StockFoodRef food,
            BigDecimal requiredQty,
            BigDecimal availableQty,
            BigDecimal shortQty,
            LocalDate shortFrom) {}
}
