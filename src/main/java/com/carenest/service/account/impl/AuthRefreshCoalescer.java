package com.carenest.service.account.impl;

import com.carenest.exception.GlobalException;
import com.carenest.service.account.AuthService.RefreshResult;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Gộp các refresh đồng thời cùng cookie (ADR-0015): Keycloak xoay refresh token một lần dùng, nên chỉ request đầu
 * gọi Keycloak, request sau nhận cùng kết quả. Trạng thái trong bộ nhớ, chỉ đúng khi BE chạy 1 instance.
 */
@Component
public class AuthRefreshCoalescer {
    static final Duration RESULT_TTL = Duration.ofSeconds(30);
    static final Duration WAIT_TIMEOUT = Duration.ofSeconds(15);

    private record Entry(CompletableFuture<RefreshResult> result, Instant createdAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthRefreshCoalescer(Clock clock) {
        this.clock = clock;
    }

    public RefreshResult run(String key, Supplier<RefreshResult> refresh) {
        Instant now = clock.instant();
        entries.values()
                .removeIf(entry -> entry.result().isDone()
                        && !entry.createdAt().plus(RESULT_TTL).isAfter(now));
        Entry mine = new Entry(new CompletableFuture<>(), now);
        Entry existing = entries.putIfAbsent(key, mine);
        if (existing != null) return await(existing.result());
        try {
            RefreshResult result = refresh.get();
            mine.result().complete(result);
            return result;
        } catch (RuntimeException ex) {
            // Lỗi không được giữ lại: retry sau 503 phải gọi Keycloak lần nữa.
            entries.remove(key, mine);
            mine.result().completeExceptionally(ex);
            throw ex;
        }
    }

    private static RefreshResult await(CompletableFuture<RefreshResult> result) {
        try {
            return result.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException cause) throw cause;
            throw unavailable();
        } catch (TimeoutException ex) {
            throw unavailable();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static GlobalException unavailable() {
        return new GlobalException(ApiCode.SERVICE_UNAVAILABLE, "Authentication service unavailable");
    }
}
