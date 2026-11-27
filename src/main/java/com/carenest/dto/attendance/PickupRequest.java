package com.carenest.dto.attendance;

import com.carenest.utils.enums.PickupVerification;
import java.time.LocalTime;

/** Giờ trả do BE ghi tại lúc xác nhận; handoverTime cũ được bỏ qua để tương thích client cũ. */
public record PickupRequest(
        Long childId, PickupVerification verification, Integer guardianPosition, LocalTime handoverTime, String note) {}
