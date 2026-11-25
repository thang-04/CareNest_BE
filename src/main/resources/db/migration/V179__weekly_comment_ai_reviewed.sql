-- Cần chốt 24/11/2026: nháp nhận xét tuần do AI viết phải được giáo viên mở xem hoặc sửa trước khi "Gửi tất cả".
-- ai_reviewed_at: lần đầu cô xem / sửa bản nháp (null = chưa xem); viết lại nháp thì đặt lại null.
ALTER TABLE weekly_comments
    ADD COLUMN ai_reviewed_at TIMESTAMPTZ NULL;
