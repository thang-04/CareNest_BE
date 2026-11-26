-- Tranh nền giao diện chung của cả trường: một dòng duy nhất (id = 1), chưa có dòng = dùng cấu hình mặc định.
-- mode: FIXED | AUTO | DAILY_RANDOM; scene id là mã tranh của Web (T0..T16, S1, S3, S8).
CREATE TABLE school_appearance (
    id                SMALLINT     PRIMARY KEY,
    mode              VARCHAR(20)  NOT NULL,
    pinned_scene_id   VARCHAR(8)   NOT NULL,
    enabled_scene_ids text[]       NOT NULL,
    updated_by        BIGINT,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_school_appearance_single CHECK (id = 1),
    CONSTRAINT ck_school_appearance_mode CHECK (mode IN ('FIXED', 'AUTO', 'DAILY_RANDOM')),
    CONSTRAINT ck_school_appearance_enabled CHECK (cardinality(enabled_scene_ids) > 0),
    CONSTRAINT fk_school_appearance_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts (id)
);
