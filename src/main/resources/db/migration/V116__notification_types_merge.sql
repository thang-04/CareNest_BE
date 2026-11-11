-- Gộp nhánh kế hoạch giáo dục (V104) và nhánh điểm danh (V114): cả hai đặt lại CHECK loại thông báo ⇒ đặt danh sách đủ
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR'));
