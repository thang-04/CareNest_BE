-- Đổi/đặt lại mật khẩu: mốc để từ chối access token cấp trước đó và hiện "Đổi mật khẩu gần nhất" ở hồ sơ.
-- Tài khoản cũ để trống (chưa có dữ liệu lần đổi trước).
ALTER TABLE user_accounts ADD COLUMN password_changed_at TIMESTAMPTZ;

-- Phiên Web bị chặn vì đăng nhập mới hơn thay thế (REPLACED, báo 1016) hoặc vì đã đăng xuất (ENDED, 401 thường).
ALTER TABLE revoked_web_sessions
    ADD COLUMN reason VARCHAR(16) NOT NULL DEFAULT 'REPLACED',
    ADD CONSTRAINT ck_revoked_web_sessions_reason CHECK (reason IN ('REPLACED', 'ENDED'));
