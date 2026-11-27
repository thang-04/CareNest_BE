package com.carenest.dto.attendance;

import java.time.LocalTime;

/** Sửa giờ trả trẻ trong ngày (PCK-04): giờ đúng ({@code HH:mm}, không sau giờ hiện tại) và lý do bắt buộc. */
public record CorrectHandoverTimeRequest(LocalTime handoverTime, String reason) {}
