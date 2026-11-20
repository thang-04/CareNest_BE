package com.carenest.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Đồng hồ dời được cho test tích hợp: luồng bán trú chỉ ghi trong ngày (NUT-11, NUT-12, NUT-21), test dời "hôm nay"
 * sang ngày đang thử. Dùng làm bean {@code @Primary} chung cả lớp test nên đặt lại ở {@code @BeforeEach}.
 */
public final class MovableClock extends Clock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private volatile Instant now = Instant.EPOCH;

    public void set(Instant instant) {
        now = instant;
    }

    public void set(LocalDate date, LocalTime time) {
        now = date.atTime(time).atZone(ZONE).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
