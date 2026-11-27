package com.carenest.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Clock nghiệp vụ: trả giờ giả lập của request hiện tại nếu {@link DevClockFilter} đã gắn (chỉ bật ở môi trường dev),
 * ngược lại trả giờ thật. Mọi service inject {@code Clock} nên các hạn mở/khóa tự tính theo giờ giả lập.
 */
public final class RequestAwareClock extends Clock {

    private static final ThreadLocal<Instant> SIMULATED_NOW = new ThreadLocal<>();

    private final Clock delegate;

    public RequestAwareClock(Clock delegate) {
        this.delegate = delegate;
    }

    static void simulate(Instant instant) {
        SIMULATED_NOW.set(instant);
    }

    static void clearSimulation() {
        SIMULATED_NOW.remove();
    }

    public static Optional<Instant> simulatedInstant() {
        return Optional.ofNullable(SIMULATED_NOW.get());
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new RequestAwareClock(delegate.withZone(zone));
    }

    @Override
    public Instant instant() {
        Instant simulated = SIMULATED_NOW.get();
        return simulated != null ? simulated : delegate.instant();
    }
}
