-- Kế hoạch giáo dục năm học theo nhóm tuổi (module education-plan, ADR-0020, rule EDU).
-- Theo CAN_DB trang 1 "Kế hoạch giáo dục"; các cột branch/theme/week của bảng duyệt, lịch sử thêm khi có bảng đó.

-- Lĩnh vực giáo dục: 5 lĩnh vực chuẩn + lĩnh vực trường thêm; name là tên theo Chương trình, display_label do trường đặt
CREATE TABLE edu_domains (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    display_label VARCHAR(150),
    short_label   VARCHAR(60),
    sort_order    SMALLINT     NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_edu_domains_name UNIQUE (name)
);

INSERT INTO edu_domains (name, display_label, short_label, sort_order) VALUES
    ('Phát triển thể chất', NULL, 'Thể chất', 1),
    ('Phát triển nhận thức', NULL, 'Nhận thức', 2),
    ('Phát triển ngôn ngữ', NULL, 'Ngôn ngữ', 3),
    ('Phát triển tình cảm và kỹ năng xã hội', NULL, 'Tình cảm – xã hội', 4),
    ('Phát triển thẩm mỹ', 'Phát triển thẩm mỹ (Nghệ thuật)', 'Thẩm mỹ (Nghệ thuật)', 5);

-- Danh mục kết quả mong đợi theo nhóm tuổi × lĩnh vực: gợi ý cho dòng "nhóm kết quả mong đợi"
CREATE TABLE edu_expected_results (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    age_group_id BIGINT      NOT NULL,
    domain_id    BIGINT      NOT NULL,
    sub_domain   VARCHAR(150),
    text         TEXT        NOT NULL,
    sort_order   SMALLINT    NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_edu_expected_results_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_edu_expected_results_domain_id FOREIGN KEY (domain_id) REFERENCES edu_domains (id)
);
CREATE INDEX idx_edu_expected_results_age_group_domain ON edu_expected_results (age_group_id, domain_id);

-- Thứ tự người ký theo loại tài liệu; kế hoạch năm chỉ dùng cho khối chữ ký khi in (luồng duyệt cố định TT → PHT)
CREATE TABLE edu_approval_steps (
    doc_type      VARCHAR(20) NOT NULL,
    step_order    SMALLINT    NOT NULL,
    approver_role VARCHAR(30) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_edu_approval_steps PRIMARY KEY (doc_type, step_order),
    CONSTRAINT ck_edu_approval_steps_doc_type CHECK (doc_type IN ('year', 'branch', 'theme_dossier'))
);

INSERT INTO edu_approval_steps (doc_type, step_order, approver_role) VALUES
    ('year', 1, 'AGE_GROUP_TEAM_LEADER'),
    ('year', 2, 'VICE_PRINCIPAL');

-- EDU-01: mỗi (năm học, nhóm tuổi) đúng 1 kế hoạch
CREATE TABLE edu_year_plans (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(30)  NOT NULL,
    school_year_id BIGINT       NOT NULL,
    age_group_id   BIGINT       NOT NULL,
    title          VARCHAR(150) NOT NULL,
    description    VARCHAR(500) NOT NULL DEFAULT '',
    status         VARCHAR(20)  NOT NULL,
    owner_id       BIGINT       NOT NULL,
    owner_title    VARCHAR(100),
    sent_by        BIGINT,
    sent_at        TIMESTAMPTZ,
    issued_by      BIGINT,
    issued_at      TIMESTAMPTZ,
    attachment_key VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_edu_year_plans_code UNIQUE (code),
    CONSTRAINT uk_edu_year_plans_school_year_age_group UNIQUE (school_year_id, age_group_id),
    CONSTRAINT fk_edu_year_plans_school_year_id FOREIGN KEY (school_year_id) REFERENCES school_years (id),
    CONSTRAINT fk_edu_year_plans_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_edu_year_plans_owner_id FOREIGN KEY (owner_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_year_plans_sent_by FOREIGN KEY (sent_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_year_plans_issued_by FOREIGN KEY (issued_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_edu_year_plans_status CHECK (status IN ('DRAFT', 'PENDING_VP', 'REJECTED', 'APPROVED'))
);
CREATE INDEX idx_edu_year_plans_age_group_id ON edu_year_plans (age_group_id);

-- Dòng bảng mục tiêu theo thứ tự hiển thị trong lĩnh vực: sub = lĩnh vực con, group = nhóm KQMĐ, goal = MT n
CREATE TABLE edu_goal_rows (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_plan_id BIGINT   NOT NULL,
    domain_id    BIGINT   NOT NULL,
    sort_order   SMALLINT NOT NULL,
    kind         VARCHAR(10) NOT NULL,
    text         TEXT     NOT NULL,
    goal_no      SMALLINT,
    CONSTRAINT fk_edu_goal_rows_year_plan_id FOREIGN KEY (year_plan_id) REFERENCES edu_year_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_edu_goal_rows_domain_id FOREIGN KEY (domain_id) REFERENCES edu_domains (id),
    CONSTRAINT ck_edu_goal_rows_kind CHECK (kind IN ('sub', 'group', 'goal')),
    -- EDU-09: chỉ dòng MT có số MT
    CONSTRAINT ck_edu_goal_rows_goal_no CHECK ((kind = 'goal') = (goal_no IS NOT NULL))
);
CREATE INDEX idx_edu_goal_rows_year_plan_id ON edu_goal_rows (year_plan_id);

CREATE TABLE edu_goal_contents (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goal_row_id BIGINT   NOT NULL,
    sort_order  SMALLINT NOT NULL,
    text        TEXT     NOT NULL,
    CONSTRAINT fk_edu_goal_contents_goal_row_id FOREIGN KEY (goal_row_id) REFERENCES edu_goal_rows (id) ON DELETE CASCADE
);
CREATE INDEX idx_edu_goal_contents_goal_row_id ON edu_goal_contents (goal_row_id);

CREATE TABLE edu_themes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_plan_id BIGINT       NOT NULL,
    sort_order   SMALLINT     NOT NULL,
    name         VARCHAR(150) NOT NULL,
    abbr         VARCHAR(20)  NOT NULL,
    start_date   DATE,
    end_date     DATE,
    festivals    TEXT         NOT NULL DEFAULT '',
    note         TEXT         NOT NULL DEFAULT '',
    CONSTRAINT fk_edu_themes_year_plan_id FOREIGN KEY (year_plan_id) REFERENCES edu_year_plans (id) ON DELETE CASCADE,
    CONSTRAINT ck_edu_themes_date_range CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);
-- Viết tắt chủ đề không trùng trong 1 kế hoạch (không phân biệt hoa thường)
CREATE UNIQUE INDEX uk_edu_themes_year_plan_abbr ON edu_themes (year_plan_id, lower(abbr));

CREATE TABLE edu_breaks (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_plan_id BIGINT       NOT NULL,
    sort_order   SMALLINT     NOT NULL,
    label        VARCHAR(150) NOT NULL,
    start_date   DATE,
    end_date     DATE,
    CONSTRAINT fk_edu_breaks_year_plan_id FOREIGN KEY (year_plan_id) REFERENCES edu_year_plans (id) ON DELETE CASCADE,
    CONSTRAINT ck_edu_breaks_date_range CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_edu_breaks_year_plan_id ON edu_breaks (year_plan_id);

-- Nội dung giáo dục tích vào chủ đề thực hiện; mục tiêu của chủ đề suy ra từ đây
CREATE TABLE edu_content_theme_ticks (
    content_id BIGINT NOT NULL,
    theme_id   BIGINT NOT NULL,
    CONSTRAINT pk_edu_content_theme_ticks PRIMARY KEY (content_id, theme_id),
    CONSTRAINT fk_edu_content_theme_ticks_content_id FOREIGN KEY (content_id) REFERENCES edu_goal_contents (id) ON DELETE CASCADE,
    CONSTRAINT fk_edu_content_theme_ticks_theme_id FOREIGN KEY (theme_id) REFERENCES edu_themes (id) ON DELETE CASCADE
);
CREATE INDEX idx_edu_content_theme_ticks_theme_id ON edu_content_theme_ticks (theme_id);

-- Lịch sử thao tác hiển thị trên trang chi tiết; tone theo UI: '' | ok | warn | err
CREATE TABLE edu_plan_events (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_plan_id     BIGINT       NOT NULL,
    action           VARCHAR(30)  NOT NULL,
    action_label     VARCHAR(200) NOT NULL,
    actor_id         BIGINT,
    actor_name       VARCHAR(100) NOT NULL,
    actor_role_label VARCHAR(100),
    tone             VARCHAR(5)   NOT NULL DEFAULT '',
    note             TEXT,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_edu_plan_events_year_plan_id FOREIGN KEY (year_plan_id) REFERENCES edu_year_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_edu_plan_events_actor_id FOREIGN KEY (actor_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_edu_plan_events_tone CHECK (tone IN ('', 'ok', 'warn', 'err'))
);
CREATE INDEX idx_edu_plan_events_year_plan_id ON edu_plan_events (year_plan_id);

-- Lượt duyệt/ban hành (người, vai trò, thời điểm) để in khối chữ ký
CREATE TABLE edu_plan_approvals (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year_plan_id   BIGINT       NOT NULL,
    approver_id    BIGINT       NOT NULL,
    approver_role  VARCHAR(30)  NOT NULL,
    approver_title VARCHAR(100),
    approved_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    note           TEXT         NOT NULL DEFAULT '',
    CONSTRAINT fk_edu_plan_approvals_year_plan_id FOREIGN KEY (year_plan_id) REFERENCES edu_year_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_edu_plan_approvals_approver_id FOREIGN KEY (approver_id) REFERENCES user_accounts (id)
);
CREATE INDEX idx_edu_plan_approvals_year_plan_id ON edu_plan_approvals (year_plan_id);
