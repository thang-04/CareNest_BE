-- AUTH-18 (ADR-0024): mã kích hoạt tài khoản phụ huynh gửi qua SMS khi có nhà cung cấp thật. Một dòng mỗi tài khoản
-- (mã hiện hành; gửi lại thay mã cũ). Chỉ lưu hash; 72 giờ, tối đa 5 lần sai; activated_at khác NULL = đã dùng.
CREATE TABLE parent_activation_codes (
    user_id         BIGINT       PRIMARY KEY,
    code_hash       VARCHAR(64)  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    failed_attempts SMALLINT     NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ  NOT NULL,
    activated_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_parent_activation_codes_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_parent_activation_codes_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_parent_activation_codes_expiry CHECK (expires_at > sent_at)
);
