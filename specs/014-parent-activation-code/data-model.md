# Data model — 014

Bảng mới `parent_activation_codes` (V123), một dòng mỗi tài khoản phụ huynh (mã hiện hành):
- `user_id` BIGINT PK, FK `user_accounts(id)`.
- `code_hash` VARCHAR(64) NOT NULL — SHA-256 hex của `userId:code`; không lưu mã gốc.
- `expires_at` TIMESTAMPTZ NOT NULL — `sent_at` + 72 giờ.
- `failed_attempts` SMALLINT NOT NULL DEFAULT 0, CHECK 0..5; =5 ⇒ hết lượt.
- `sent_at` TIMESTAMPTZ NOT NULL — lần cấp mã gần nhất (cooldown 60 giây).
- `activated_at` TIMESTAMPTZ NULL — đã kích hoạt; khác NULL ⇒ không gửi lại.
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT now().
- CHECK `expires_at > sent_at`.

Không dùng `password_tokens`: ràng buộc hạn ngắn và job dọn của gateway (ADR-0013) xóa dòng hết hạn sau 1 giờ. Gửi lại = cập nhật dòng (hash mới, reset lần sai, hạn mới). Lịch sử gửi vẫn ở `parent_sms_logs` (purpose `ACTIVATION`). Tài khoản cũ (mật khẩu tạm) không có dòng ⇒ không backfill.
