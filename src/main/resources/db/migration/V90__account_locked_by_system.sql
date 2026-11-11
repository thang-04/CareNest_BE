-- G94-180 (2026-11-11, user chốt): tài khoản phụ huynh do hệ thống khóa vì không còn con đang học (CHD-04) thì tự mở
-- lại khi một con được mở lại hồ sơ; HT/PHT khóa hoặc mở tay ⇒ FALSE, mở lại hồ sơ không đụng tới.
-- Tài khoản đã khóa trước migration này nhận FALSE: không biết lý do khóa nên phải mở tay.
ALTER TABLE user_accounts ADD COLUMN locked_by_system BOOLEAN NOT NULL DEFAULT FALSE;
