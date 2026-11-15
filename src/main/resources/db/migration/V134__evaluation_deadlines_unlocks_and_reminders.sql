-- Hạn, khóa và mở lại đánh giá cuối chủ đề, học kỳ, cuối năm (OBS-06, OBS-27, OBS-28; Cần chốt 14/11/2026), cùng cách với
-- nhận xét tuần (OBS-29): mở từ ngày kết thúc kỳ, khóa sau 23:59:59 của ngày học thứ N sau ngày đó (N cố định:
-- chủ đề 3, học kỳ 5, cuối năm 5); quá hạn thì cả lớp – kỳ khóa, HT hoặc PHT có quyền mở lại tới hạn "Mở đến".
-- Kèm loại thông báo nhắc việc (OBS-30).

-- Mở lại theo lớp – kỳ: kind THEME (period_key = dòng gốc chủ đề), SEMESTER (1 | 2), YEAR (Y)
CREATE TABLE evaluation_unlocks (
    id            BIGSERIAL PRIMARY KEY,
    kind          VARCHAR(20)   NOT NULL,
    classroom_id  BIGINT        NOT NULL REFERENCES classrooms (id),
    campus_id     BIGINT        NOT NULL REFERENCES campuses (id),
    period_key    VARCHAR(40)   NOT NULL,
    valid_until   TIMESTAMPTZ   NOT NULL,
    reason        VARCHAR(300)  NOT NULL,
    unlocked_by   BIGINT        NOT NULL REFERENCES user_accounts (id),
    unlocked_at   TIMESTAMPTZ   NOT NULL,
    revoked_by    BIGINT REFERENCES user_accounts (id),
    revoked_at    TIMESTAMPTZ,
    CONSTRAINT ck_evaluation_unlocks_kind CHECK (kind IN ('THEME', 'SEMESTER', 'YEAR')),
    CONSTRAINT ck_evaluation_unlocks_reason CHECK (char_length(btrim(reason)) > 0)
);

CREATE INDEX idx_evaluation_unlocks_class_period ON evaluation_unlocks (classroom_id, kind, period_key);
CREATE INDEX idx_evaluation_unlocks_at ON evaluation_unlocks (unlocked_at DESC);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED',
    'CHILD_PLACED', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR',
    'WEEKLY_COMMENT_SENT',
    'ATTENDANCE_REMINDER', 'ASSESSMENT_REMINDER', 'ASSESSMENT_REOPENED', 'LEADER_REMINDER'));
