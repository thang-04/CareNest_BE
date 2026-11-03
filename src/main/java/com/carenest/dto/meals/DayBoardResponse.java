package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;

/** Bán trú hôm nay: việc của ngày theo điểm trường, chỉ đọc. */
public record DayBoardResponse(LocalDate date, List<CampusBoard> campuses) {

    /**
     * {@code freshReceiptStatus} (kiểm nhận hàng tươi): DAY_OFF, NO_FORECAST (chưa có bản dự trù chứa ngày),
     * ROUND1_PENDING, ROUND1_DONE (chờ thực đơn công bố và suất chốt để kiểm lần 2), ROUND2_PENDING, DONE.
     * {@code foodSampleStatus} (lưu mẫu, NUT-21): PENDING_APPROVAL (còn bản lưu mẫu hoặc hủy mẫu chờ duyệt của điểm
     * trường, mọi ngày — số chờ ở {@code foodSamplePendingCount}), UP_TO_DATE (không chờ duyệt, ngày có bản ghi), NONE
     * (không chờ duyệt, ngày chưa có bản ghi).
     */
    public record CampusBoard(
            Long campusId,
            String campusName,
            WeekMenu menu,
            MealCounts mealCounts,
            DryStock dryStock,
            String freshReceiptStatus,
            String foodSampleStatus,
            int foodSamplePendingCount) {}

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
