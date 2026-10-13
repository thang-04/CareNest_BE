CREATE TABLE web_auth_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    keycloak_user_id VARCHAR(36) NOT NULL,
    access_cipher TEXT NOT NULL,
    refresh_cipher TEXT NOT NULL,
    access_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    remember BOOLEAN NOT NULL,
    refresh_owner VARCHAR(43),
    refresh_lease_until TIMESTAMPTZ,
    CHECK (expires_at > created_at AND idle_expires_at <= expires_at),
    CHECK ((refresh_owner IS NULL) = (refresh_lease_until IS NULL))
);
CREATE INDEX idx_web_auth_sessions_user ON web_auth_sessions(user_id);
CREATE INDEX idx_web_auth_sessions_expiry ON web_auth_sessions(expires_at);

CREATE TABLE web_auth_challenges (
    token_hash VARCHAR(64) PRIMARY KEY,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('OTP','FIRST_LOGIN','RESET')),
    user_id BIGINT REFERENCES user_accounts(id) ON DELETE CASCADE,
    keycloak_user_id VARCHAR(36),
    lookup_hash VARCHAR(64),
    provider_cipher TEXT,
    otp_hmac VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    resend_available_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0 AND attempts <= 5),
    consumed_at TIMESTAMPTZ,
    processing_owner VARCHAR(43),
    CHECK (expires_at > created_at),
    CHECK ((purpose = 'OTP' AND otp_hmac IS NOT NULL AND provider_cipher IS NULL AND lookup_hash IS NOT NULL)
        OR (purpose IN ('FIRST_LOGIN','RESET') AND user_id IS NOT NULL AND keycloak_user_id IS NOT NULL
            AND provider_cipher IS NOT NULL AND otp_hmac IS NULL))
);
CREATE INDEX idx_web_auth_challenges_user ON web_auth_challenges(user_id);
CREATE INDEX idx_web_auth_challenges_lookup ON web_auth_challenges(lookup_hash);
CREATE INDEX idx_web_auth_challenges_expiry ON web_auth_challenges(expires_at);

CREATE TABLE web_auth_rate_limits (
    bucket_hash VARCHAR(64) PRIMARY KEY,
    used INTEGER NOT NULL CHECK (used >= 0),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_web_auth_rate_limits_expiry ON web_auth_rate_limits(expires_at);
