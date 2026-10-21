-- Tên bảng theo việc bảng làm: token dùng 1 lần cho luồng mật khẩu, và giới hạn số lần gửi OTP. Dữ liệu giữ nguyên.
ALTER TABLE web_auth_challenges RENAME TO password_tokens;
ALTER TABLE password_tokens RENAME CONSTRAINT web_auth_challenges_pkey TO password_tokens_pkey;
ALTER TABLE password_tokens RENAME CONSTRAINT web_auth_challenges_user_id_fkey TO password_tokens_user_id_fkey;
ALTER TABLE password_tokens RENAME CONSTRAINT web_auth_challenges_purpose_check TO password_tokens_purpose_check;
ALTER TABLE password_tokens RENAME CONSTRAINT web_auth_challenges_attempts_check TO password_tokens_attempts_check;
ALTER TABLE password_tokens RENAME CONSTRAINT web_auth_challenges_check TO password_tokens_expiry_check;
ALTER TABLE password_tokens RENAME CONSTRAINT web_auth_challenges_check1 TO password_tokens_purpose_fields_check;
ALTER INDEX idx_web_auth_challenges_user RENAME TO idx_password_tokens_user;
ALTER INDEX idx_web_auth_challenges_expiry RENAME TO idx_password_tokens_expiry;

ALTER TABLE web_auth_rate_limits RENAME TO otp_send_limits;
ALTER TABLE otp_send_limits RENAME CONSTRAINT web_auth_rate_limits_pkey TO otp_send_limits_pkey;
ALTER TABLE otp_send_limits RENAME CONSTRAINT web_auth_rate_limits_used_check TO otp_send_limits_used_check;
ALTER INDEX idx_web_auth_rate_limits_expiry RENAME TO idx_otp_send_limits_expiry;
