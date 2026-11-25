-- G94-180/CHD-03 (user chốt 2026-11-25): lịch nghỉ học. Ngày nghỉ sau hôm nay ⇒ trẻ vẫn Đang học tới ngày đó, 00:05 ngày
-- nghỉ hệ thống mới chuyển sang Đã nghỉ. ck_children_left buộc left_on/left_reason chỉ có khi LEFT nên lịch cần cột riêng.
ALTER TABLE children
    ADD COLUMN scheduled_leave_on     DATE,
    ADD COLUMN scheduled_leave_reason VARCHAR(300),
    ADD COLUMN scheduled_leave_by     BIGINT,
    ADD CONSTRAINT fk_children_scheduled_leave_by FOREIGN KEY (scheduled_leave_by) REFERENCES user_accounts (id),
    ADD CONSTRAINT ck_children_scheduled_leave CHECK (
        (scheduled_leave_on IS NULL AND scheduled_leave_reason IS NULL AND scheduled_leave_by IS NULL)
        OR (scheduled_leave_on IS NOT NULL AND scheduled_leave_reason IS NOT NULL)),
    ADD CONSTRAINT ck_children_scheduled_leave_not_left CHECK (status <> 'LEFT' OR scheduled_leave_on IS NULL);

COMMENT ON COLUMN children.scheduled_leave_on IS 'Ngày nghỉ học đã hẹn (G94-180/CHD-03); null = không có lịch nghỉ';
COMMENT ON COLUMN children.scheduled_leave_reason IS 'Lý do nghỉ học của lịch nghỉ, chép sang left_reason khi áp';
COMMENT ON COLUMN children.scheduled_leave_by IS 'Người đặt lịch nghỉ, ghi vào lịch sử WITHDRAWN khi áp';

-- Job 00:05 tìm lịch nghỉ đã tới ngày
CREATE INDEX idx_children_scheduled_leave_on ON children (scheduled_leave_on) WHERE scheduled_leave_on IS NOT NULL;

ALTER TABLE child_history DROP CONSTRAINT ck_child_history_action;
ALTER TABLE child_history
    ADD CONSTRAINT ck_child_history_action
        CHECK (action IN ('ENROLLED', 'UPDATED', 'WITHDRAWN', 'READMITTED', 'LEAVE_SCHEDULED', 'LEAVE_CANCELLED'));
