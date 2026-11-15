-- NUT-02: suất thay thế PHT/HT đã điều chỉnh tay không bị điểm danh ghi đè; điểm danh đổi sau đó thì đánh dấu để kiểm tra lại
ALTER TABLE meal_count_lines
    ADD COLUMN substitute_adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN attendance_changed BOOLEAN NOT NULL DEFAULT FALSE;

-- ATT-08: mở lại điểm danh (kể cả "Trả lại GV kiểm tra" từ sổ suất ăn) báo giáo viên lớp
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type
    CHECK (type IN ('CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'ATTENDANCE_DAY_UNLOCKED'));
