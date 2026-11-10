-- Lịch sử là snapshot độc lập: hủy đơn không được xóa mất lịch sử điểm danh của giáo viên.
ALTER TABLE attendance_record_history ADD COLUMN child_id BIGINT, ADD COLUMN classroom_id BIGINT,
    ADD COLUMN campus_id BIGINT, ADD COLUMN date DATE;
UPDATE attendance_record_history h SET child_id = r.child_id, classroom_id = r.classroom_id,
    campus_id = r.campus_id, date = r.date FROM attendance_records r WHERE h.record_id = r.id;
ALTER TABLE attendance_record_history ALTER COLUMN child_id SET NOT NULL,
    ALTER COLUMN classroom_id SET NOT NULL, ALTER COLUMN campus_id SET NOT NULL, ALTER COLUMN date SET NOT NULL;
ALTER TABLE attendance_record_history DROP CONSTRAINT attendance_record_history_record_id_fkey;
ALTER TABLE attendance_record_history DROP CONSTRAINT ck_attendance_record_history_action;
ALTER TABLE attendance_record_history ADD CONSTRAINT ck_attendance_record_history_action
    CHECK (action IN ('RECORDED', 'UPDATED', 'CANCELLED'));
CREATE INDEX idx_attendance_history_child_date ON attendance_record_history(child_id, date);
