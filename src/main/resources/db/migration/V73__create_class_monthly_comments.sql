-- Nhận xét chung cả lớp theo tháng (OBS-26, user chốt 03/11/2026): hệ thống viết nháp từ số liệu tháng, GV lớp sửa
-- và xác nhận; BGH xem bản đã xác nhận; phụ huynh không xem.
CREATE TABLE class_monthly_comments (
    id              BIGSERIAL PRIMARY KEY,
    classroom_id    BIGINT        NOT NULL REFERENCES classrooms (id),
    campus_id       BIGINT        NOT NULL REFERENCES campuses (id),
    school_year_id  BIGINT        NOT NULL REFERENCES school_years (id),
    month           DATE          NOT NULL,
    draft_content   TEXT,
    content         VARCHAR(3000),
    confirmed_by    BIGINT REFERENCES user_accounts (id),
    confirmed_at    TIMESTAMPTZ,
    updated_by      BIGINT REFERENCES user_accounts (id),
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_class_monthly_comments UNIQUE (classroom_id, month),
    CONSTRAINT ck_class_monthly_comments_month CHECK (EXTRACT(DAY FROM month) = 1)
);
