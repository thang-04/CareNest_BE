package com.carenest.dto.child;

import java.time.LocalDate;

/**
 * Ngày được chọn ở hộp Cho nghỉ học (G94-180/CHD-03, user chốt 2026-11-24): hôm nay hoặc {@code nextSchoolDay}.
 * {@code todayAllowed} false khi hôm nay không phải ngày học hoặc trẻ đã có điểm danh, sức khỏe, đánh giá trong hôm nay;
 * {@code todayBlockedReason} là câu lỗi ô {@code leftAt} mà cho nghỉ hôm nay sẽ trả (null khi được). {@code nextSchoolDay}
 * = ngày học đầu tiên sau hôm nay, null khi 400 ngày tới không có ngày học.
 */
public record LeaveOptionsResponse(
        LocalDate today, boolean todayAllowed, String todayBlockedReason, LocalDate nextSchoolDay) {}
