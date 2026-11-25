-- Cần chốt 24/11/2026: lần đầu giáo viên của lớp (dạy chính hoặc dạy thay ngày đó) mở đơn nghỉ.
-- Ghi một lần, không ghi đè; BGH mở không tính. Phụ huynh thấy nhãn "Cô đã xem".
ALTER TABLE leave_requests
    ADD COLUMN teacher_seen_at TIMESTAMPTZ NULL,
    ADD COLUMN teacher_seen_by BIGINT NULL REFERENCES user_accounts (id);
