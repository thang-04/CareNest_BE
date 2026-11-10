package com.carenest.service.meals;

import com.carenest.dto.meals.FreshCheckRequest;
import com.carenest.dto.meals.FreshCheckResponse;
import com.carenest.dto.meals.FreshForecastRequest;
import com.carenest.dto.meals.FreshForecastResponse;
import com.carenest.dto.meals.FreshForecastSummaryResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/**
 * Hàng tươi (NUT-10, NUT-12): bản dự trù theo kỳ (NUT-07) và kiểm nhận 2 lần/ngày. Hàng tươi không vào kho nên không
 * có sổ kho. Đọc: HT/PHT; ghi: {@code STOCK_MANAGE} ở điểm trường (AUTH-20).
 */
public interface FreshGoodsService {

    /** Bản dự trù của điểm trường, mới lập nhất trước. */
    List<FreshForecastSummaryResponse> getForecasts(CurrentUser currentUser, Long campusId);

    /** Tính bản dự trù theo thực đơn tuần và sĩ số, không lưu. */
    FreshForecastResponse previewForecast(CurrentUser currentUser, FreshForecastRequest request);

    FreshForecastResponse createForecast(CurrentUser currentUser, FreshForecastRequest request);

    FreshForecastResponse getForecast(CurrentUser currentUser, Long forecastId);

    void deleteForecast(CurrentUser currentUser, Long forecastId);

    /** Cả hai lần kiểm nhận của điểm trường–ngày, kèm danh sách cần nhận. */
    FreshCheckResponse getChecks(CurrentUser currentUser, Long campusId, LocalDate date);

    /** Ghi (hoặc ghi đè) lần kiểm {@code round} (1: so bản dự trù, 2: so nhu cầu theo suất đã chốt). */
    FreshCheckResponse recordCheck(
            CurrentUser currentUser, Long campusId, LocalDate date, int round, FreshCheckRequest request);

    /**
     * Trạng thái hàng tươi của điểm trường–ngày cho trang Bán trú hôm nay: DAY_OFF, NO_FORECAST, ROUND1_PENDING,
     * ROUND1_DONE (chờ thực đơn công bố và suất chốt để kiểm lần 2), ROUND2_PENDING, DONE.
     */
    String getReceiptStatus(Long campusId, LocalDate date);
}
