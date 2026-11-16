package com.carenest.entity.account;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AUTH-28: phiên Web đã bị thay thế; access token còn hạn mang sid này bị từ chối tới expiresAt. */
@Getter
@Entity
@Table(name = "revoked_web_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevokedWebSession {
    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "keycloak_user_id", length = 36, nullable = false)
    private String keycloakUserId;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public RevokedWebSession(String sessionId, String keycloakUserId, Instant revokedAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.keycloakUserId = keycloakUserId;
        this.revokedAt = revokedAt;
        this.expiresAt = expiresAt;
    }
}
