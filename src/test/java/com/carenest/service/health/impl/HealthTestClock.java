package com.carenest.service.health.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

final class HealthTestClock extends Clock {

    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    HealthTestClock(Instant initial) {
        this(new AtomicReference<>(initial), ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private HealthTestClock(AtomicReference<Instant> current, ZoneId zone) {
        this.current = current;
        this.zone = zone;
    }

    void setInstant(Instant instant) {
        current.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new HealthTestClock(current, newZone);
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
