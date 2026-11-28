package com.carenest.dto.attendance;

import com.carenest.utils.enums.PickupVerification;
import java.time.LocalTime;

/**
 * Ghi kết quả xác minh người đón hôm nay. {@code PHOTO_MATCH}: bắt buộc {@code guardianPosition} (phụ huynh trên hồ sơ);
 * {@code PHONE_CONFIRMED}, {@code PHONE_DECLINED}: bắt buộc {@code note}. Giờ trả lấy theo giờ máy chủ lúc bấm;
 * {@code handoverTime} giữ để client cũ không lỗi và bị bỏ qua (PCK-04).
 */
public record PickupRequest(
        Long childId, PickupVerification verification, Integer guardianPosition, LocalTime handoverTime, String note) {}
