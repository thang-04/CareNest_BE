-- Spec 019 (G94-289): bảng notifications làm hàng chờ đẩy (outbox), worker đẩy rồi ghi trạng thái.
-- Dòng có sẵn trước khi triển khai không đẩy (SKIPPED); dòng mới mặc định PENDING.
ALTER TABLE notifications
    ADD COLUMN push_state VARCHAR(10) NOT NULL DEFAULT 'SKIPPED',
    ADD COLUMN push_attempts SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN push_next_at TIMESTAMPTZ;

ALTER TABLE notifications ALTER COLUMN push_state SET DEFAULT 'PENDING';

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_push_state
    CHECK (push_state IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'));

CREATE INDEX idx_notifications_push_pending ON notifications (id)
    WHERE push_state = 'PENDING';

-- Dọn thiết bị không cập nhật quá 270 ngày
CREATE INDEX idx_device_tokens_updated_at ON device_tokens (updated_at);
