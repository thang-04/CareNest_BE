-- G94-377 (user chốt 2026-11-15, CHD-11): GV được HT cấp thêm CHILD_PROFILE_EDIT sửa hồ sơ, khai báo sức khỏe của trẻ
-- lớp mình thì không áp dụng ngay mà thành yêu cầu; HT, hoặc PHT điểm trường của trẻ có CHILD_PROFILE_EDIT, duyệt mới
-- ghi vào hồ sơ. Mỗi trẻ tối đa một yêu cầu đang chờ mỗi loại; gửi lại thì yêu cầu cũ bị hủy.
CREATE TABLE child_profile_change_requests (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id       BIGINT       NOT NULL REFERENCES children (id),
    kind           VARCHAR(30)  NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    requested_by   BIGINT       NOT NULL REFERENCES user_accounts (id),
    requested_at   TIMESTAMPTZ  NOT NULL,
    decided_by     BIGINT REFERENCES user_accounts (id),
    decided_at     TIMESTAMPTZ,
    decision_note  VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_profile_change_kind CHECK (kind IN ('PROFILE', 'HEALTH_DECLARATION')),
    CONSTRAINT ck_profile_change_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    -- Đã quyết định thì có người và lúc quyết định; từ chối bắt buộc lý do; hủy do yêu cầu mới thì không có người quyết
    CONSTRAINT ck_profile_change_decision CHECK (
        (status IN ('APPROVED', 'REJECTED')) = (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT ck_profile_change_reject_note CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL)
);

CREATE UNIQUE INDEX uq_profile_change_pending ON child_profile_change_requests (child_id, kind) WHERE status = 'PENDING';
CREATE INDEX idx_profile_change_status ON child_profile_change_requests (status);
CREATE INDEX idx_profile_change_requested_by ON child_profile_change_requests (requested_by);

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED', 'HEALTH_TREND_APPROVED',
    'CHILD_PLACED', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR',
    'WEEKLY_COMMENT_SENT',
    'ATTENDANCE_REMINDER', 'ASSESSMENT_REMINDER', 'ASSESSMENT_REOPENED', 'LEADER_REMINDER',
    'STAFF_ASSIGNED', 'CUTOFF_CHANGED', 'LEAVE_REQUEST_CHANGED', 'MEAL_COUNT_CONFIRMED', 'REWARD_LIST_UPDATED',
    'DAILY_HEALTH_NOTED', 'SCHOOL_YEAR_ACTIVATED', 'SCHOOL_ANNOUNCEMENT', 'MENU_PUBLISHED', 'LEAVE_REQUEST_CREATED', 'CHILD_EVALUATION_CONFIRMED', 'THEME_EVALUATION_CONFIRMED',
    'MENU_CHANGED',
    'CHILD_PROFILE_CHANGE_REQUESTED', 'CHILD_PROFILE_CHANGE_DECIDED'));
