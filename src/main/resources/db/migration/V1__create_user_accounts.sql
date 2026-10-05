-- Hồ sơ nghiệp vụ của người dùng CareNest. Danh tính, mật khẩu, role do Keycloak quản lý (ADR-0011):
-- không có cột mật khẩu/role ở đây; keycloak_user_id = claim "sub" của access token.
CREATE TABLE user_accounts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keycloak_user_id VARCHAR(36)  NOT NULL,
    username         VARCHAR(100) NOT NULL,
    email            VARCHAR(255),
    full_name        VARCHAR(150) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_accounts_keycloak_user_id UNIQUE (keycloak_user_id),
    CONSTRAINT uk_user_accounts_username UNIQUE (username),
    CONSTRAINT ck_user_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
