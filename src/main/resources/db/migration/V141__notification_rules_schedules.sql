-- Spec 019 (G94-291): quy tắc thông báo tự động (PAR-09) và lịch gửi lặp (PAR-10).
CREATE TABLE notification_rules (
    type VARCHAR(40) PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    title_template VARCHAR(150),
    body_template VARCHAR(500),
    updated_by BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notification_rules_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts (id)
);

CREATE TABLE announcement_schedules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id BIGINT NOT NULL,
    scope_type VARCHAR(10) NOT NULL,
    scope_campus_id BIGINT,
    scope_age_group_id BIGINT,
    scope_classroom_id BIGINT,
    roles VARCHAR(60) NOT NULL,
    frequency VARCHAR(10) NOT NULL,
    day_of_week SMALLINT,
    day_of_month SMALLINT,
    send_time TIME NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_announcement_schedules_template FOREIGN KEY (template_id) REFERENCES announcement_templates (id),
    CONSTRAINT fk_announcement_schedules_campus FOREIGN KEY (scope_campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_announcement_schedules_age_group FOREIGN KEY (scope_age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_announcement_schedules_classroom FOREIGN KEY (scope_classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_announcement_schedules_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_announcement_schedules_scope CHECK (scope_type IN ('SCHOOL', 'CAMPUS', 'AGE_GROUP', 'CLASSROOM')),
    CONSTRAINT ck_announcement_schedules_frequency CHECK (
        (frequency = 'WEEKLY' AND day_of_week BETWEEN 1 AND 7)
        OR (frequency = 'MONTHLY' AND day_of_month BETWEEN 1 AND 31))
);

CREATE INDEX idx_announcement_schedules_due ON announcement_schedules (next_run_at) WHERE active;

ALTER TABLE school_announcements ADD CONSTRAINT fk_school_announcements_schedule
    FOREIGN KEY (schedule_id) REFERENCES announcement_schedules (id) ON DELETE SET NULL;

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
    'DAILY_HEALTH_NOTED', 'SCHOOL_YEAR_ACTIVATED', 'SCHOOL_ANNOUNCEMENT', 'MENU_PUBLISHED', 'LEAVE_REQUEST_CREATED', 'CHILD_EVALUATION_CONFIRMED', 'THEME_EVALUATION_CONFIRMED'));
