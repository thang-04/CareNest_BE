-- SIG-01: ảnh chữ ký của tài khoản nhân sự Web, dùng chung cho mọi chứng từ cần ký ảnh (INS-01..04).
-- Ảnh nằm trong kho tệp (storage_key = UUID tệp); mỗi tài khoản tối đa 5 ảnh (kiểm ở service), một ảnh mặc định.
CREATE TABLE user_signatures (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES user_accounts (id),
    storage_key  VARCHAR(36)  NOT NULL,
    content_type VARCHAR(30)  NOT NULL,
    size_bytes   INT          NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_signatures_key UNIQUE (storage_key),
    CONSTRAINT ck_user_signatures_type CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_user_signatures_size CHECK (size_bytes > 0 AND size_bytes <= 2097152)
);
CREATE INDEX idx_user_signatures_user ON user_signatures (user_id);
-- Mỗi tài khoản tối đa một chữ ký mặc định
CREATE UNIQUE INDEX uk_user_signatures_default ON user_signatures (user_id) WHERE is_default;
