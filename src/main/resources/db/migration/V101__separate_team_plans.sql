-- V89: tách bảng kế hoạch giáo dục tổ (spec 015, D1 + D2).
-- Additive, an toàn khi apply: chưa đặt FK mới, giữ cột year_plan_id cho giai đoạn chuyển.
-- Cutover (chuyển TEAM rows, đặt FK, drop plan_level/year_plan_id) nằm ở V90 (T009, [DUYET]).
-- DDL theo specs/015-separate-year-team-plans/data-model.md §1/§2/§3.

-- ============================================================
-- 1. Bảng chính: edu_team_plans (mirror cột edu_year_plans tại V88 + cột nguồn tổ)
-- ============================================================
CREATE TABLE edu_team_plans (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(30)  NOT NULL,
    school_year_id BIGINT       NOT NULL,
    age_group_id   BIGINT       NOT NULL,
    title          VARCHAR(150) NOT NULL,                 -- tên riêng tổ (FR-005)
    description    VARCHAR(500) NOT NULL DEFAULT '',
    status         VARCHAR(20)  NOT NULL,                 -- DRAFT/REVIEW/APPROVED (sau V81)
    -- Nguồn năm (FR-003): bản năm APPROVED hiện hành, cùng năm/tuổi; NULL khi legacy không nguồn
    curriculum_source_plan_id BIGINT,
    previous_source_plan_id BIGINT REFERENCES edu_year_plans(id),
    legacy_team_without_source BOOLEAN NOT NULL DEFAULT false,
    duration_policy_snapshot   JSONB NOT NULL DEFAULT '{"mode":"LEGACY_UNBOUNDED"}',
    -- Chuỗi phiên bản nội bộ tổ (source_plan_id là bản team trước, self-FK — data-model §1.2)
    version_no     INTEGER NOT NULL DEFAULT 1,
    source_plan_id BIGINT,
    -- Workflow (EDU-54: Nháp → Duyệt → Ban hành)
    owner_id       BIGINT NOT NULL,
    owner_title    VARCHAR(100),
    sent_by        BIGINT,
    sent_at        TIMESTAMPTZ,
    issued_by      BIGINT,
    issued_at      TIMESTAMPTZ,
    review_round   INTEGER NOT NULL DEFAULT 0,
    approvals      JSONB NOT NULL DEFAULT '[]',
    applied_policy_revision BIGINT,
    completion_policy JSONB,
    print_snapshot JSONB,
    published_by   BIGINT,
    completion_mode VARCHAR(50),
    completed_at   TIMESTAMPTZ,
    author_tracking_since TIMESTAMPTZ NOT NULL DEFAULT now(),
    author_coverage VARCHAR(50) NOT NULL DEFAULT 'COMPLETE',
    -- Khóa phiên sửa (EDU-10: lease)
    editing_by     BIGINT,
    editing_since  TIMESTAMPTZ,
    editing_at     TIMESTAMPTZ,
    editing_from   VARCHAR(20),
    edit_session_id UUID,
    content_revision BIGINT NOT NULL DEFAULT 0,
    last_save_request_id UUID,
    created_request_id UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_edu_team_plans_school_year_id FOREIGN KEY (school_year_id) REFERENCES school_years (id),
    CONSTRAINT fk_edu_team_plans_age_group_id   FOREIGN KEY (age_group_id)   REFERENCES age_groups (id),
    CONSTRAINT fk_edu_team_plans_owner_id       FOREIGN KEY (owner_id)       REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_team_plans_sent_by        FOREIGN KEY (sent_by)        REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_team_plans_issued_by      FOREIGN KEY (issued_by)      REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_team_plans_published_by   FOREIGN KEY (published_by)   REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_team_plans_editing_by     FOREIGN KEY (editing_by)     REFERENCES user_accounts (id),
    CONSTRAINT fk_edu_team_plans_source_plan_id FOREIGN KEY (source_plan_id) REFERENCES edu_team_plans (id),
    -- Nguồn năm bắt buộc trỏ vào bảng năm (V89 chưa NOT NULL: TEAM legacy không nguồn; V90 siết)
    CONSTRAINT fk_edu_team_plans_curriculum_source FOREIGN KEY (curriculum_source_plan_id) REFERENCES edu_year_plans (id),
    CONSTRAINT uq_edu_team_plans_code UNIQUE (code),
    CONSTRAINT uq_edu_team_plan_create_request UNIQUE (owner_id, created_request_id),
    CONSTRAINT uq_edu_team_plan_version UNIQUE (school_year_id, age_group_id, version_no),
    CONSTRAINT ck_edu_team_plans_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_edu_team_plans_source CHECK ((version_no = 1) = (source_plan_id IS NULL)),
    CONSTRAINT ck_edu_team_plans_status CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED')),
    CONSTRAINT ck_edu_team_plans_edit_session CHECK (
        (edit_session_id IS NULL AND editing_by IS NULL AND editing_since IS NULL AND editing_at IS NULL AND editing_from IS NULL)
        OR (edit_session_id IS NOT NULL AND editing_by IS NOT NULL AND editing_since IS NOT NULL AND editing_at IS NOT NULL
            AND editing_from IS NOT NULL AND editing_from = 'DRAFT' AND status = 'DRAFT')),
    CONSTRAINT ck_edu_team_plan_source CHECK (
        (legacy_team_without_source = false AND curriculum_source_plan_id IS NOT NULL)
        OR (legacy_team_without_source = true  AND curriculum_source_plan_id IS NULL))
);
-- Tối đa một bản team chưa ban hành cho mỗi (năm học, nhóm tuổi) — giữ unique hiện có của TEAM (EDU-01)
CREATE UNIQUE INDEX uq_edu_team_plan_open ON edu_team_plans (school_year_id, age_group_id) WHERE status <> 'APPROVED';
CREATE INDEX idx_edu_team_plans_source_plan_id   ON edu_team_plans (source_plan_id);
CREATE INDEX idx_edu_team_plans_curriculum_source ON edu_team_plans (curriculum_source_plan_id);

-- ============================================================
-- 2. Cột sở hữu trên 9 bảng con có year_plan_id (D1): 2 cột NULL, chưa FK
--    goal_contents / content_theme_ticks suy sở hữu qua bảng cha (không thêm cột).
-- ============================================================
ALTER TABLE edu_plan_goals
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_plan_sub_domains
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_plan_expected_results
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_themes
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_breaks
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_plan_events
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_document_authors
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_plan_branch_templates
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;
ALTER TABLE edu_class_docs
    ADD COLUMN school_year_plan_id BIGINT,
    ADD COLUMN team_plan_id        BIGINT;

-- ============================================================
-- 3. Backfill cột sở hữu theo plan_level hiện tại (giữ nguyên ID số)
--    V90 sẽ chuyển TEAM rows sang edu_team_plans giữ ID ⇒ team_plan_id vẫn đúng.
-- ============================================================
DO $$
DECLARE item RECORD;
BEGIN
FOR item IN
    SELECT unnest(ARRAY[
        'edu_plan_goals','edu_plan_sub_domains','edu_plan_expected_results',
        'edu_themes','edu_breaks','edu_plan_events',
        'edu_document_authors','edu_plan_branch_templates','edu_class_docs'
    ]) AS tbl
LOOP
    EXECUTE format(
        'UPDATE %I c SET team_plan_id = c.year_plan_id
           FROM edu_year_plans p WHERE p.id = c.year_plan_id AND p.plan_level = ''TEAM''',
        item.tbl);
    EXECUTE format(
        'UPDATE %I c SET school_year_plan_id = c.year_plan_id
           FROM edu_year_plans p WHERE p.id = c.year_plan_id AND p.plan_level = ''SCHOOL_YEAR''',
        item.tbl);
END LOOP;
END $$;

-- Index để query theo cột sở hữu (chưa dùng trong V89, chuẩn bị V90)
CREATE INDEX idx_edu_plan_goals_owner             ON edu_plan_goals (team_plan_id);
CREATE INDEX idx_edu_plan_sub_domains_owner      ON edu_plan_sub_domains (team_plan_id);
CREATE INDEX idx_edu_plan_expected_results_owner ON edu_plan_expected_results (team_plan_id);
CREATE INDEX idx_edu_themes_owner                 ON edu_themes (team_plan_id);
CREATE INDEX idx_edu_breaks_owner                 ON edu_breaks (team_plan_id);
CREATE INDEX idx_edu_plan_events_owner            ON edu_plan_events (team_plan_id);
CREATE INDEX idx_edu_document_authors_owner       ON edu_document_authors (team_plan_id);
CREATE INDEX idx_edu_plan_branch_templates_owner  ON edu_plan_branch_templates (team_plan_id);
CREATE INDEX idx_edu_class_docs_owner             ON edu_class_docs (team_plan_id);

-- ============================================================
-- 4. Bảng tiến độ rà soát bản team update (D2 + FR-009/014, data-model §3)
-- ============================================================
CREATE TABLE edu_team_review_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_plan_id   BIGINT NOT NULL REFERENCES edu_team_plans (id) ON DELETE CASCADE,
    source_plan_id BIGINT NOT NULL REFERENCES edu_year_plans(id),
    item_kind      VARCHAR(20) NOT NULL CHECK (item_kind IN ('CONTENT','THEME','GOAL')),   -- CONTENT | THEME | GOAL
    item_id        BIGINT NOT NULL,        -- nội dung/chủ đề/mục tiêu trong bảng con tổ
    reason         VARCHAR(30) NOT NULL CHECK (reason IN ('SOURCE_CHANGED','SOURCE_REMOVED','REF_LOST')),   -- SOURCE_CHANGED | SOURCE_REMOVED | REF_LOST
    resolved       BOOLEAN NOT NULL DEFAULT false,
    resolved_at    TIMESTAMPTZ,
    resolved_by    BIGINT REFERENCES user_accounts (id),
    UNIQUE (team_plan_id, source_plan_id, item_kind, item_id),
    CHECK ((resolved AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
        OR (NOT resolved AND resolved_at IS NULL AND resolved_by IS NULL))
);
CREATE INDEX idx_edu_team_review_items_unresolved ON edu_team_review_items (team_plan_id) WHERE resolved = false;
