package com.carenest.security;

import com.carenest.config.ClockConfig;
import com.carenest.entity.account.RevokedWebSession;
import com.carenest.repository.account.RevokedWebSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access token của phiên Web đã bị thay thế (AUTH-28) hoặc đã đăng xuất bị chặn ngay ở mọi request, không chờ hết
 * hạn. Kiểm bằng bộ nhớ (không gọi Keycloak/DB mỗi request); DB chỉ để nạp lại sau khi khởi động (API 1 instance).
 */
@Component
public class WebSessionRevocations implements OAuth2TokenValidator<Jwt> {
    public static final String ERROR_CODE = "web_session_replaced";
    public static final String ENDED_ERROR_CODE = "web_session_ended";
    // Lâu hơn thời hạn access token (5 phút, realm accessTokenLifespan) để token cũ chắc chắn đã hết hạn
    private static final Duration HOLD = Duration.ofMinutes(10);
    private static final OAuth2Error REPLACED =
            new OAuth2Error(ERROR_CODE, "Web session replaced by a newer login", null);
    private static final OAuth2Error ENDED = new OAuth2Error(ENDED_ERROR_CODE, "Web session signed out", null);

    private final RevokedWebSessionRepository repository;
    private final Clock clock;
    private final Map<String, Revoked> revoked = new ConcurrentHashMap<>();

    // So với hạn access token thật nên luôn theo giờ thật, kể cả khi Dev Mode giả lập giờ
    public WebSessionRevocations(
            RevokedWebSessionRepository repository, @Qualifier(ClockConfig.SYSTEM_CLOCK) Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** REPLACED: đăng nhập mới hơn thay thế (FE báo 1016); ENDED: người dùng đã đăng xuất (401 thường). */
    public enum Reason {
        REPLACED,
        ENDED
    }

    private record Revoked(Instant expiresAt, Reason reason) {}

    @Transactional
    public void revoke(String keycloakUserId, Collection<String> sessionIds) {
        revoke(keycloakUserId, sessionIds, Reason.REPLACED);
    }

    @Transactional
    public void end(String keycloakUserId, String sessionId) {
        revoke(keycloakUserId, List.of(sessionId), Reason.ENDED);
    }

    private void revoke(String keycloakUserId, Collection<String> sessionIds, Reason reason) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(HOLD);
        for (String sessionId : sessionIds) {
            repository.save(new RevokedWebSession(sessionId, keycloakUserId, now, expiresAt, reason.name()));
            revoked.put(sessionId, new Revoked(expiresAt, reason));
        }
    }

    public boolean isRevoked(String sessionId) {
        return active(sessionId) != null;
    }

    private Revoked active(String sessionId) {
        if (sessionId == null) return null;
        Revoked entry = revoked.get(sessionId);
        return entry != null && entry.expiresAt().isAfter(clock.instant()) ? entry : null;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Revoked entry = active(jwt.getClaimAsString("sid"));
        if (entry == null) return OAuth2TokenValidatorResult.success();
        return OAuth2TokenValidatorResult.failure(entry.reason() == Reason.ENDED ? ENDED : REPLACED);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void loadActive() {
        repository
                .findByExpiresAtAfter(clock.instant())
                .forEach(row ->
                        revoked.put(row.getSessionId(), new Revoked(row.getExpiresAt(), reasonOf(row.getReason()))));
    }

    @Transactional
    public void purgeExpired() {
        Instant now = clock.instant();
        revoked.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        repository.deleteExpired(now);
    }

    private static Reason reasonOf(String value) {
        return Reason.ENDED.name().equals(value) ? Reason.ENDED : Reason.REPLACED;
    }
}
