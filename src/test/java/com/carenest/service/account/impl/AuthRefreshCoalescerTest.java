package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.account.AuthResponses;
import com.carenest.exception.GlobalException;
import com.carenest.service.account.AuthService.RefreshResult;
import com.carenest.service.account.AuthService.SessionCookie;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthRefreshCoalescerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");

    private static RefreshResult result(String access) {
        return new RefreshResult(
                new AuthResponses.AccessToken(access, NOW.plusSeconds(300)), new SessionCookie("cookie-" + access, -1));
    }

    @Test
    @DisplayName("AUTH-07: hai refresh đồng thời cùng cookie chỉ gọi Keycloak một lần và nhận cùng kết quả")
    void concurrentCallersShareOneRefresh() throws Exception {
        var coalescer = new AuthRefreshCoalescer(Clock.fixed(NOW, ZoneOffset.UTC));
        var calls = new AtomicInteger();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RefreshResult> first = pool.submit(() -> coalescer.run("cookie-hash", () -> {
                calls.incrementAndGet();
                started.countDown();
                await(release);
                return result("rotated");
            }));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Future<RefreshResult> second = pool.submit(() -> coalescer.run("cookie-hash", () -> {
                calls.incrementAndGet();
                return result("duplicate");
            }));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(second.get(5, TimeUnit.SECONDS));
            assertThat(calls).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AUTH-07: kết quả refresh chỉ giữ 30 giây; sau đó cookie cũ gọi Keycloak lại")
    void completedResultExpiresAfterTtl() {
        var clock = new MutableClock(NOW);
        var coalescer = new AuthRefreshCoalescer(clock);
        var calls = new AtomicInteger();
        coalescer.run("cookie-hash", () -> result("first-" + calls.incrementAndGet()));
        assertThat(coalescer
                        .run("cookie-hash", () -> result("again-" + calls.incrementAndGet()))
                        .data()
                        .accessToken())
                .isEqualTo("first-1");
        clock.now = NOW.plus(AuthRefreshCoalescer.RESULT_TTL);
        assertThat(coalescer
                        .run("cookie-hash", () -> result("after-" + calls.incrementAndGet()))
                        .data()
                        .accessToken())
                .isEqualTo("after-2");
    }

    @Test
    @DisplayName("AUTH-07: refresh lỗi không được giữ lại để retry gọi Keycloak lần nữa")
    void failureIsNotCached() {
        var coalescer = new AuthRefreshCoalescer(Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> coalescer.run("cookie-hash", () -> {
                    throw new GlobalException(ApiCode.SERVICE_UNAVAILABLE, "Authentication service unavailable");
                }))
                .isInstanceOf(GlobalException.class);
        assertThat(coalescer.run("cookie-hash", () -> result("retried")).data().accessToken())
                .isEqualTo("retried");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
