-- Lịch sử sửa ngoại lệ một ngày của thực đơn tuần đã công bố (NUT-08, P-39): chỉ thêm, không sửa, không xóa
CREATE TABLE meal_plan_day_revisions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    week_id         BIGINT       NOT NULL REFERENCES meal_plan_weeks (id),
    menu_date       DATE         NOT NULL,
    before_note     VARCHAR(300) NOT NULL DEFAULT '',
    before_items    JSONB        NOT NULL,
    after_note      VARCHAR(300) NOT NULL DEFAULT '',
    after_items     JSONB        NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    issues          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    changed_by      BIGINT       NOT NULL REFERENCES user_accounts (id),
    changed_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_meal_plan_day_revisions_reason CHECK (char_length(btrim(reason)) >= 10)
);

CREATE INDEX idx_meal_plan_day_revisions_week_date ON meal_plan_day_revisions (week_id, menu_date);

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
    'MENU_CHANGED'));
