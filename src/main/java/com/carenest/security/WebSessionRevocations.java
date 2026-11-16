package com.carenest.security;

import com.carenest.entity.account.RevokedWebSession;
import com.carenest.repository.account.RevokedWebSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-28: phiên Web bị đăng nhập mới hơn thay thế bị chặn ngay ở mọi request, không chờ access token hết hạn.
 * Kiểm bằng bộ nhớ (không gọi Keycloak/DB mỗi request); DB chỉ để nạp lại sau khi khởi động (API 1 instance).
 */
@Component
@RequiredArgsConstructor
public class WebSessionRevocations implements OAuth2TokenValidator<Jwt> {
    public static final String ERROR_CODE = "web_session_replaced";
    // Lâu hơn thời hạn access token (5 phút, realm accessTokenLifespan) để token cũ chắc chắn đã hết hạn
    private static final Duration HOLD = Duration.ofMinutes(10);
    private static final OAuth2Error REPLACED =
            new OAuth2Error(ERROR_CODE, "Web session replaced by a newer login", null);

    private final RevokedWebSessionRepository repository;
    private final Clock clock;
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    @Transactional
    public void revoke(String keycloakUserId, Collection<String> sessionIds) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(HOLD);
        for (String sessionId : sessionIds) {
            repository.save(new RevokedWebSession(sessionId, keycloakUserId, now, expiresAt));
            revoked.put(sessionId, expiresAt);
        }
    }

    public boolean isRevoked(String sessionId) {
        if (sessionId == null) return false;
        Instant expiresAt = revoked.get(sessionId);
        return expiresAt != null && expiresAt.isAfter(clock.instant());
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return isRevoked(jwt.getClaimAsString("sid"))
                ? OAuth2TokenValidatorResult.failure(REPLACED)
                : OAuth2TokenValidatorResult.success();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void loadActive() {
        repository
                .findByExpiresAtAfter(clock.instant())
                .forEach(row -> revoked.put(row.getSessionId(), row.getExpiresAt()));
    }

    @Transactional
    public void purgeExpired() {
        Instant now = clock.instant();
        revoked.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
        repository.deleteExpired(now);
    }
}
