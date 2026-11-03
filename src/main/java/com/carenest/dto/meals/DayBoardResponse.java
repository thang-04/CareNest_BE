package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;

/** Bán trú hôm nay: việc của ngày theo điểm trường, chỉ đọc. */
public record DayBoardResponse(LocalDate date, List<CampusBoard> campuses) {

    /** {@code freshReceiptStatus}, {@code foodSampleStatus}: NOT_AVAILABLE tới khi có lát hàng tươi, lưu mẫu. */
    public record CampusBoard(
            Long campusId,
            String campusName,
            WeekMenu menu,
            MealCounts mealCounts,
            DryStock dryStock,
            String freshReceiptStatus,
            String foodSampleStatus) {}

    /** Thực đơn tuần chứa ngày và tuần sau: NONE, DRAFT, PUBLISHED. */
    public record WeekMenu(
            LocalDate weekStart, String status, boolean dayOff, LocalDate nextWeekStart, String nextWeekStatus) {}

    /** {@code status}: NONE (chưa tổng hợp), PENDING_CONFIRMATION, NEEDS_RECONFIRMATION, CONFIRMED. */
    public record MealCounts(String status, int totalServings, List<SessionServings> sessions) {}

    public record SessionServings(MealSession session, int servings) {}

    /**
     * {@code status}: DAY_OFF, NO_MENU (tuần chưa công bố), WAITING_COUNT (suất chưa xác nhận), NOT_SYNCED (suất đã
     * chốt nhưng kho chưa trừ đúng nhu cầu — {@code foodCount} = số thực phẩm lệch, đồng bộ lại bằng
     * {@code POST /nutrition/stock/days/{date}/sync}), NOTHING_TO_DEDUCT, DEDUCTED, SHORTFALL.
     * {@code openShortfallCount}: số dòng thiếu còn mở của điểm trường (mọi ngày).
     */
    public record DryStock(String status, int foodCount, int shortfallFoodCount, int openShortfallCount) {}
}
