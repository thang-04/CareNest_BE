package com.carenest.dto.child;

import java.time.LocalDate;

/**
 * Ngày được chọn ở hộp Cho nghỉ học (G94-180/CHD-03, user chốt 2026-11-25): bất kỳ ngày nào từ {@code minDate} tới
 * {@code maxDate}. {@code todayAllowed} false khi trẻ đã có lịch nghỉ, đã có dữ liệu trong hôm nay (điểm danh, sức khỏe,
 * đón trẻ, đánh giá, số đo, phiếu khám) hoặc hôm nay không hợp lệ; {@code todayBlockedReason} là câu lỗi ô
 * {@code leftAt} mà cho nghỉ hôm nay sẽ trả (null khi được). Trong năm học: {@code minDate} = hôm nay khi
 * {@code todayAllowed}, không thì ngày mai; {@code maxDate} = ngày kết thúc năm học đang hoạt động. Nghỉ hè (năm đang
 * hoạt động đã kết thúc) hoặc chưa có năm đang hoạt động: {@code minDate} = {@code maxDate} = hôm nay, chọn được chỉ khi
 * {@code todayAllowed}. {@code minDate} sau {@code maxDate} (ngày cuối năm mà hôm nay bị chặn) ⇒ không còn ngày nào.
 */
public record LeaveOptionsResponse(
        LocalDate today, boolean todayAllowed, String todayBlockedReason, LocalDate minDate, LocalDate maxDate) {}
