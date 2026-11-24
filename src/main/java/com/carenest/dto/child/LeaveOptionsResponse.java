package com.carenest.dto.child;

import java.time.LocalDate;

/**
 * Ngày được chọn ở hộp Cho nghỉ học (G94-180/CHD-03, user chốt 2026-11-25): bất kỳ ngày nào từ {@code minDate} tới
 * {@code maxDate}. {@code todayAllowed} false khi trẻ đã có dữ liệu trong hôm nay (điểm danh, sức khỏe, đón trẻ, đánh
 * giá, số đo, phiếu khám) hoặc hôm nay không hợp lệ; {@code todayBlockedReason} là câu lỗi ô {@code leftAt} mà cho nghỉ
 * hôm nay sẽ trả (null khi được). {@code minDate} = hôm nay khi được chọn hôm nay, không thì ngày mai; {@code maxDate} =
 * ngày kết thúc năm học đang hoạt động (null khi không có năm đang hoạt động). {@code minDate} sau {@code maxDate} ⇒ không
 * còn ngày nào chọn được.
 */
public record LeaveOptionsResponse(
        LocalDate today, boolean todayAllowed, String todayBlockedReason, LocalDate minDate, LocalDate maxDate) {}
