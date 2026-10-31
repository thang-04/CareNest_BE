package com.carenest.dto.attendance;

import com.carenest.utils.enums.PickupVerification;
import java.time.LocalTime;

/**
 * Ghi kết quả xác minh người đón hôm nay. {@code PHOTO_MATCH}: bắt buộc {@code guardianPosition} (phụ huynh trên hồ sơ),
 * {@code handoverTime}; {@code PHONE_CONFIRMED}: bắt buộc {@code handoverTime}, {@code note} (nội dung cuộc gọi, người đón);
 * {@code PHONE_DECLINED}: bắt buộc {@code note}. Giờ trả {@code HH:mm} không sau giờ hiện tại.
 */
public record PickupRequest(
        Long childId, PickupVerification verification, Integer guardianPosition, LocalTime handoverTime, String note) {}
