-- Đánh giá trẻ hằng ngày (EDU-19, module learning-observation): nhóm ngày 1 dòng / trẻ / ngày; kết quả theo MT của từng
-- dòng hoạt động trong giáo án tuần đã duyệt 1 dòng / trẻ / ngày / hoạt động. Không chép hoạt động / MT, chỉ tham chiếu id.
CREATE TABLE daily_observations (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id          BIGINT       NOT NULL REFERENCES children (id),
    classroom_id      BIGINT       NOT NULL REFERENCES classrooms (id),
    campus_id         BIGINT       NOT NULL REFERENCES campuses (id),
    date              DATE         NOT NULL,
    -- Sức khỏe: cô chưa chỉnh thì theo sức khỏe trong ngày (không lưu), chỉnh thì lưu biểu hiện cô chọn
    health_overridden BOOLEAN      NOT NULL DEFAULT FALSE,
    health_signs      VARCHAR(100) NOT NULL DEFAULT '',
    health_note       VARCHAR(500) NOT NULL DEFAULT '',
    meals             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    nap               VARCHAR(20),
    emotion           VARCHAR(20),
    -- Mức tham gia cả ngày (view Hoạt động chung); tham gia theo hoạt động ở daily_activity_results
    participation     VARCHAR(20),
    behaviors         VARCHAR(200) NOT NULL DEFAULT '',
    behavior_note     VARCHAR(500) NOT NULL DEFAULT '',
    flag              BOOLEAN,
    comment           VARCHAR(500) NOT NULL DEFAULT '',
    recorded_by       BIGINT       NOT NULL REFERENCES user_accounts (id),
    recorded_at       TIMESTAMPTZ  NOT NULL,
    updated_by        BIGINT REFERENCES user_accounts (id),
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uk_daily_observations_child_date UNIQUE (child_id, date),
    CONSTRAINT ck_daily_observations_meals CHECK (jsonb_typeof(meals) = 'object'),
    CONSTRAINT ck_daily_observations_nap CHECK (nap IS NULL OR nap IN ('NGU_TOT', 'NGU_IT', 'KHONG_NGU')),
    CONSTRAINT ck_daily_observations_emotion CHECK (emotion IS NULL OR emotion IN ('HAPPY', 'CALM', 'SAD', 'UPSET')),
    CONSTRAINT ck_daily_observations_participation
        CHECK (participation IS NULL OR participation IN ('TICH_CUC', 'BINH_THUONG', 'CHUA_TICH_CUC'))
);

CREATE INDEX idx_daily_observations_classroom_date ON daily_observations (classroom_id, date);

CREATE TABLE daily_activity_results (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id      BIGINT       NOT NULL REFERENCES children (id),
    classroom_id  BIGINT       NOT NULL REFERENCES classrooms (id),
    campus_id     BIGINT       NOT NULL REFERENCES campuses (id),
    date          DATE         NOT NULL,
    -- Khóa dòng hoạt động trong cấu hình bảng tuần (edu_settings.week_rows), vd. hoc, ngoai_troi
    activity_key  VARCHAR(40)  NOT NULL,
    class_doc_id  BIGINT       NOT NULL REFERENCES edu_class_docs (id),
    participation VARCHAR(20),
    goals_met     VARCHAR(500) NOT NULL DEFAULT '',
    goals_effort  VARCHAR(500) NOT NULL DEFAULT '',
    goals_not_met VARCHAR(500) NOT NULL DEFAULT '',
    recorded_by   BIGINT       NOT NULL REFERENCES user_accounts (id),
    recorded_at   TIMESTAMPTZ  NOT NULL,
    updated_by    BIGINT REFERENCES user_accounts (id),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uk_daily_activity_results UNIQUE (child_id, date, activity_key),
    CONSTRAINT ck_daily_activity_results_participation
        CHECK (participation IS NULL OR participation IN ('TICH_CUC', 'BINH_THUONG', 'CHUA_TICH_CUC'))
);

CREATE INDEX idx_daily_activity_results_classroom_date ON daily_activity_results (classroom_id, date);

-- Mở lại ngày đánh giá đã khóa (HT, PHT điểm trường): như mở lại điểm danh, giữ lịch sử, kết thúc sớm không xóa
CREATE TABLE assessment_day_unlocks (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    classroom_id BIGINT       NOT NULL REFERENCES classrooms (id),
    campus_id    BIGINT       NOT NULL REFERENCES campuses (id),
    date         DATE         NOT NULL,
    valid_until  TIMESTAMPTZ  NOT NULL,
    reason       VARCHAR(300) NOT NULL,
    unlocked_by  BIGINT       NOT NULL REFERENCES user_accounts (id),
    unlocked_at  TIMESTAMPTZ  NOT NULL,
    revoked_by   BIGINT REFERENCES user_accounts (id),
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_assessment_day_unlocks_classroom_date ON assessment_day_unlocks (classroom_id, date);
