-- AUTH-28: phiên Web bị đăng nhập mới hơn thay thế. Access token đã cấp (≤ 5 phút) mang sid này bị chặn ngay ở mọi
-- request; giữ tới khi token cũ chắc chắn hết hạn rồi dọn. Không lưu token, chỉ id phiên Keycloak.
CREATE TABLE revoked_web_sessions (
    session_id       VARCHAR(64)  PRIMARY KEY,
    keycloak_user_id VARCHAR(36)  NOT NULL,
    revoked_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_revoked_web_sessions_expiry CHECK (expires_at > revoked_at)
);

CREATE INDEX idx_revoked_web_sessions_expires_at ON revoked_web_sessions (expires_at);
