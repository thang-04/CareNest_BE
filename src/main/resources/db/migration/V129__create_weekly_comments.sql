-- Nhận xét tuần theo trẻ (OBS-29, Cần chốt 13/11/2026 theo góp ý của cô): GV lớp viết 1–2 đoạn văn cho từng trẻ mỗi
-- tuần (thứ Hai–thứ Sáu), hệ thống viết nháp từ số liệu tuần, GV sửa rồi gửi phụ huynh; đã gửi thì khóa.
CREATE TABLE weekly_comments (
    id              BIGSERIAL PRIMARY KEY,
    child_id        BIGINT        NOT NULL REFERENCES children (id),
    classroom_id    BIGINT        NOT NULL REFERENCES classrooms (id),
    campus_id       BIGINT        NOT NULL REFERENCES campuses (id),
    school_year_id  BIGINT        NOT NULL REFERENCES school_years (id),
    week_start      DATE          NOT NULL,
    content         TEXT          NOT NULL DEFAULT '',
    status          VARCHAR(10)   NOT NULL DEFAULT 'DRAFT',
    draft_source    VARCHAR(10)   NOT NULL DEFAULT 'MANUAL',
    sent_at         TIMESTAMPTZ,
    sent_by         BIGINT REFERENCES user_accounts (id),
    created_by      BIGINT        NOT NULL REFERENCES user_accounts (id),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_by      BIGINT REFERENCES user_accounts (id),
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_weekly_comments_child_week UNIQUE (child_id, week_start),
    -- ISO: thứ Hai = 1
    CONSTRAINT ck_weekly_comments_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT ck_weekly_comments_content_length CHECK (char_length(content) <= 2000),
    CONSTRAINT ck_weekly_comments_status CHECK (status IN ('DRAFT', 'SENT')),
    CONSTRAINT ck_weekly_comments_source CHECK (draft_source IN ('TEMPLATE', 'AI', 'MANUAL')),
    CONSTRAINT ck_weekly_comments_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL AND sent_by IS NOT NULL))
);

CREATE INDEX idx_weekly_comments_class_week ON weekly_comments (classroom_id, week_start);

-- Thông báo phụ huynh khi cô gửi nhận xét tuần
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED',
    'CHILD_PLACED', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR',
    'WEEKLY_COMMENT_SENT'));
