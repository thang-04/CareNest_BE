-- Spec 003 (Tiền duyệt DB-B 10/11/2026): thông báo kế hoạch giáo dục có đường dẫn mở thẳng hồ sơ.
ALTER TABLE notifications ADD COLUMN link VARCHAR(255);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR'));

-- Nhắc "Gần đến hạn" một lần cho mỗi người + hồ sơ (FR-013)
CREATE INDEX idx_notifications_user_link ON notifications (user_id, type, link) WHERE link IS NOT NULL;
