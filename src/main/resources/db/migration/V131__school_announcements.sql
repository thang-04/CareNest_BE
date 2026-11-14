-- Spec 019 (G94-290): thông báo của trường do BGH gửi (PAR-05), tệp đính kèm, mẫu soạn sẵn.
CREATE TABLE school_announcements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    scope_type VARCHAR(10) NOT NULL,
    scope_campus_id BIGINT,
    scope_age_group_id BIGINT,
    scope_classroom_id BIGINT,
    roles VARCHAR(60) NOT NULL,
    status VARCHAR(10) NOT NULL,
    send_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    recipient_count INT NOT NULL DEFAULT 0,
    schedule_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_school_announcements_sender FOREIGN KEY (sender_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_school_announcements_campus FOREIGN KEY (scope_campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_school_announcements_age_group FOREIGN KEY (scope_age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_school_announcements_classroom FOREIGN KEY (scope_classroom_id) REFERENCES classrooms (id),
    CONSTRAINT ck_school_announcements_scope CHECK (scope_type IN ('SCHOOL', 'CAMPUS', 'AGE_GROUP', 'CLASSROOM')),
    CONSTRAINT ck_school_announcements_status CHECK (status IN ('SCHEDULED', 'SENDING', 'SENT', 'CANCELLED')),
    CONSTRAINT ck_school_announcements_scope_ids CHECK (
        (scope_type <> 'CAMPUS' OR scope_campus_id IS NOT NULL)
        AND (scope_type <> 'AGE_GROUP' OR scope_age_group_id IS NOT NULL)
        AND (scope_type <> 'CLASSROOM' OR scope_classroom_id IS NOT NULL))
);

CREATE INDEX idx_school_announcements_due ON school_announcements (send_at) WHERE status IN ('SCHEDULED', 'SENDING');
CREATE INDEX idx_school_announcements_created ON school_announcements (created_at DESC, id DESC);

CREATE TABLE announcement_attachments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    announcement_id BIGINT NOT NULL,
    file_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_announcement_attachments_announcement FOREIGN KEY (announcement_id)
        REFERENCES school_announcements (id) ON DELETE CASCADE
);

CREATE INDEX idx_announcement_attachments_announcement ON announcement_attachments (announcement_id);

CREATE TABLE announcement_templates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    title VARCHAR(150) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    campus_id BIGINT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_announcement_templates_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_announcement_templates_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id)
);

-- Mỗi người nhận một dòng cho một thông báo trường: tạo lại sau khi máy chết không trùng
ALTER TABLE notifications ADD COLUMN announcement_id BIGINT;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_announcement
    FOREIGN KEY (announcement_id) REFERENCES school_announcements (id);
CREATE UNIQUE INDEX uk_notifications_announcement_user ON notifications (announcement_id, user_id)
    WHERE announcement_id IS NOT NULL;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED',
    'CHILD_PLACED', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR',
    'SCHOOL_ANNOUNCEMENT'));
