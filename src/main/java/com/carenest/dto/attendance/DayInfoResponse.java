package com.carenest.dto.attendance;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Tình trạng một ngày điểm danh: baseLocked = qua giờ chốt (hoặc ngày đã qua); locked = baseLocked và lớp không có
 * lần mở lại còn hạn. now, cutoff dạng HH:mm theo giờ trường.
 */
public record DayInfoResponse(
        LocalDate date,
        LocalDate today,
        String now,
        String cutoff,
        LocalDate yearStart,
        LocalDate yearEnd,
        boolean schoolDay,
        boolean baseLocked,
        Instant serverTime,
        boolean locked,
        DayUnlockResponse unlock) {}
