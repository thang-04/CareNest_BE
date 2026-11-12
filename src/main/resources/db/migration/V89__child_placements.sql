-- G94-180 xếp lớp, chuyển lớp (2026-11-10, plan docs/plans/active/2026-11-10-child-placement-list.md).
-- 1) Lịch sử xếp/chuyển lớp của trẻ, chỉ thêm (CHD-10, G94-180/CHD-02): ghi khi tiếp nhận có lớp và khi xếp/chuyển lớp;
--    hồ sơ trẻ hiện ở tab xếp lớp. Chuyển năm học không ghi. Lớp tại một ngày vẫn đọc từ enrollments.
CREATE TABLE child_placements (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id          BIGINT       NOT NULL REFERENCES children (id),
    from_classroom_id BIGINT       REFERENCES classrooms (id),
    to_classroom_id   BIGINT       NOT NULL REFERENCES classrooms (id),
    placed_by         BIGINT       NOT NULL REFERENCES user_accounts (id),
    reason            VARCHAR(300),
    placed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_child_placements_classes CHECK (from_classroom_id IS NULL OR from_classroom_id <> to_classroom_id)
);

CREATE INDEX idx_child_placements_child ON child_placements (child_id, placed_at DESC);

-- 2) Thông báo giáo viên lớp mới khi trẻ được xếp vào lớp (plan tiếp nhận mục 6.2)
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED',
    'CHILD_PLACED'));
