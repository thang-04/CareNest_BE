-- Hạn, khóa và mở lại nhận xét tuần (OBS-29, Cần chốt 14/11/2026): mở từ ngày học cuối của tuần giáo án, khóa sau
-- 23:59:59 của ngày học thứ N sau ngày học cuối của tuần (N do HT đặt theo năm học, mặc định 1). Quá hạn thì cả lớp – tuần
-- khóa; HT hoặc PHT có quyền mở lại theo lớp – tuần tới hạn "Mở đến", như mở lại ngày đánh giá (OBS-19).

-- Mở lại theo lớp – tuần (thay cho mở lại từng bản ở V127)
CREATE TABLE weekly_comment_unlocks (
    id            BIGSERIAL PRIMARY KEY,
    classroom_id  BIGINT        NOT NULL REFERENCES classrooms (id),
    campus_id     BIGINT        NOT NULL REFERENCES campuses (id),
    week_start    DATE          NOT NULL,
    valid_until   TIMESTAMPTZ   NOT NULL,
    reason        VARCHAR(300)  NOT NULL,
    unlocked_by   BIGINT        NOT NULL REFERENCES user_accounts (id),
    unlocked_at   TIMESTAMPTZ   NOT NULL,
    revoked_by    BIGINT REFERENCES user_accounts (id),
    revoked_at    TIMESTAMPTZ,
    -- ISO: thứ Hai = 1
    CONSTRAINT ck_weekly_comment_unlocks_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT ck_weekly_comment_unlocks_reason CHECK (char_length(btrim(reason)) > 0)
);

CREATE INDEX idx_weekly_comment_unlocks_class_week ON weekly_comment_unlocks (classroom_id, week_start);
CREATE INDEX idx_weekly_comment_unlocks_at ON weekly_comment_unlocks (unlocked_at DESC);

-- Lịch sử nội dung: GV sửa bản đã gửi (trong hạn hoặc khi được mở lại) ⇒ lưu nội dung, lúc gửi, người gửi trước đó.
-- Bảng mở lại từng bản (V127) chỉ có dữ liệu thử ở máy dev, bỏ.
DROP TABLE weekly_comment_reopens;

CREATE TABLE weekly_comment_revisions (
    id                BIGSERIAL PRIMARY KEY,
    weekly_comment_id BIGINT        NOT NULL REFERENCES weekly_comments (id),
    previous_content  TEXT          NOT NULL,
    previous_sent_at  TIMESTAMPTZ   NOT NULL,
    previous_sent_by  BIGINT        NOT NULL REFERENCES user_accounts (id),
    changed_by        BIGINT        NOT NULL REFERENCES user_accounts (id),
    changed_at        TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_weekly_comment_revisions_comment ON weekly_comment_revisions (weekly_comment_id);

-- Hạn gửi nhận xét tuần theo năm học: số ngày học sau ngày học cuối của tuần (0 = khóa cuối ngày học cuối)
ALTER TABLE school_years ADD COLUMN weekly_comment_deadline_days SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE school_years ADD CONSTRAINT ck_school_years_weekly_comment_deadline_days
    CHECK (weekly_comment_deadline_days BETWEEN 0 AND 5);
