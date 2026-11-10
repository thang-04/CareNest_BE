-- Danh mục Chương trình GDMN của Bộ (EDU-28): văn bản nguồn, mục tiêu theo nhóm tuổi (mục D "Kết quả mong đợi"),
-- nội dung của mục tiêu, gợi ý nội dung (mục C "Nội dung giáo dục theo độ tuổi"). Chỉ đọc; nạp bằng migration.
CREATE TABLE edu_curriculum_sources (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code               VARCHAR(40)  NOT NULL,
    title              VARCHAR(300) NOT NULL,
    document_no        VARCHAR(50)  NOT NULL,
    issued_date        DATE         NOT NULL,
    issuer             VARCHAR(150) NOT NULL,
    official_url       VARCHAR(500) NOT NULL,
    retrieved_from_url VARCHAR(500) NOT NULL,
    retrieved_at       DATE         NOT NULL,
    scope_note         TEXT         NOT NULL DEFAULT '',
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_edu_curriculum_sources_code UNIQUE (code)
);

CREATE TABLE edu_curriculum_goals (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id    BIGINT       NOT NULL,
    age_group_id BIGINT       NOT NULL,
    domain_id    BIGINT       NOT NULL,
    sub_domain   VARCHAR(150),
    group_no     VARCHAR(10),
    group_text   TEXT         NOT NULL,
    goal_no      VARCHAR(10),
    goal_text    TEXT         NOT NULL,
    section_ref  VARCHAR(150) NOT NULL,
    sort_order   INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_edu_curriculum_goals_source_id FOREIGN KEY (source_id) REFERENCES edu_curriculum_sources (id),
    CONSTRAINT fk_edu_curriculum_goals_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_edu_curriculum_goals_domain_id FOREIGN KEY (domain_id) REFERENCES edu_domains (id),
    CONSTRAINT uk_edu_curriculum_goals_order UNIQUE (source_id, age_group_id, sort_order)
);
CREATE INDEX idx_edu_curriculum_goals_age_group_id ON edu_curriculum_goals (age_group_id);

CREATE TABLE edu_curriculum_goal_contents (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    curriculum_goal_id BIGINT   NOT NULL,
    text               TEXT     NOT NULL,
    sort_order         SMALLINT NOT NULL,
    CONSTRAINT fk_edu_curriculum_goal_contents_goal_id
        FOREIGN KEY (curriculum_goal_id) REFERENCES edu_curriculum_goals (id) ON DELETE CASCADE,
    CONSTRAINT uk_edu_curriculum_goal_contents_order UNIQUE (curriculum_goal_id, sort_order)
);

CREATE TABLE edu_curriculum_contents (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id    BIGINT       NOT NULL,
    age_group_id BIGINT       NOT NULL,
    domain_id    BIGINT       NOT NULL,
    sub_domain   VARCHAR(150),
    category_no  VARCHAR(10),
    category     TEXT         NOT NULL,
    label        VARCHAR(300),
    text         TEXT         NOT NULL,
    section_ref  VARCHAR(150) NOT NULL,
    sort_order   INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_edu_curriculum_contents_source_id FOREIGN KEY (source_id) REFERENCES edu_curriculum_sources (id),
    CONSTRAINT fk_edu_curriculum_contents_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_edu_curriculum_contents_domain_id FOREIGN KEY (domain_id) REFERENCES edu_domains (id),
    CONSTRAINT uk_edu_curriculum_contents_order UNIQUE (source_id, age_group_id, sort_order)
);
CREATE INDEX idx_edu_curriculum_contents_age_group_id ON edu_curriculum_contents (age_group_id);

-- Mục tiêu trong kế hoạch năm lấy từ danh mục của Bộ: giữ liên kết; câu trong kế hoạch là bản chép độc lập
ALTER TABLE edu_goal_rows ADD COLUMN curriculum_goal_id BIGINT;
ALTER TABLE edu_goal_rows
    ADD CONSTRAINT fk_edu_goal_rows_curriculum_goal_id
        FOREIGN KEY (curriculum_goal_id) REFERENCES edu_curriculum_goals (id) ON DELETE SET NULL;
CREATE INDEX idx_edu_goal_rows_curriculum_goal_id ON edu_goal_rows (curriculum_goal_id);
