-- EDU-54: Migration expand cho cấu hình người tạo/duyệt kế hoạch giáo dục.
-- Thêm columns/FK/index/policy seed/creator baseline và snapshot nguồn print cũ;
-- giữ nguyên legacy states cùng REVIEW để các caller hiện tại tiếp tục hoạt động.

-- 1. EduSettings: workflow policy & revision
ALTER TABLE edu_settings
    ADD COLUMN legacy_signers TEXT,
    ADD COLUMN workflow_policy JSONB NOT NULL DEFAULT '{"year":{"creators":["TEAM_LEADER"],"reviewers":["VICE_PRINCIPAL"]},"branch":{"creators":["TEACHER"],"reviewers":["TEAM_LEADER","VICE_PRINCIPAL"]},"theme":{"creators":["TEACHER"],"reviewers":["TEAM_LEADER","VICE_PRINCIPAL"]}}'::jsonb,
    ADD COLUMN workflow_revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN workflow_updated_by BIGINT,
    ADD COLUMN workflow_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_edu_settings_workflow_updated_by FOREIGN KEY (workflow_updated_by) REFERENCES user_accounts (id),
    ADD CONSTRAINT ck_edu_settings_workflow_revision CHECK (workflow_revision > 0),
    ADD CONSTRAINT ck_edu_settings_workflow_policy CHECK (
        jsonb_typeof(workflow_policy) = 'object'
        AND workflow_policy ? 'year'
        AND workflow_policy ? 'branch'
        AND workflow_policy ? 'theme'
    );

UPDATE edu_settings SET legacy_signers = signers WHERE legacy_signers IS NULL;

-- 2. Metadata cho edu_year_plans
ALTER TABLE edu_year_plans
    ADD COLUMN review_round INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN approvals JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN applied_policy_revision BIGINT,
    ADD COLUMN completion_policy JSONB,
    ADD COLUMN print_snapshot JSONB,
    ADD COLUMN published_by BIGINT,
    ADD COLUMN completion_mode VARCHAR(50),
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN author_tracking_since TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN author_coverage VARCHAR(50) NOT NULL DEFAULT 'COMPLETE',
    ADD CONSTRAINT fk_edu_year_plans_published_by FOREIGN KEY (published_by) REFERENCES user_accounts (id);

-- Nới CHECK cho phép REVIEW cùng giá trị legacy
ALTER TABLE edu_year_plans DROP CONSTRAINT ck_edu_year_plans_status;
ALTER TABLE edu_year_plans
    ADD CONSTRAINT ck_edu_year_plans_status
        CHECK (status IN ('DRAFT', 'EDITING', 'PENDING_VP', 'REJECTED', 'APPROVED', 'REVIEW'));

-- 3. Metadata cho edu_class_docs
ALTER TABLE edu_class_docs
    ADD COLUMN review_round INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN applied_policy_revision BIGINT,
    ADD COLUMN completion_policy JSONB,
    ADD COLUMN print_snapshot JSONB,
    ADD COLUMN completion_mode VARCHAR(50),
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN author_tracking_since TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN author_coverage VARCHAR(50) NOT NULL DEFAULT 'COMPLETE';

ALTER TABLE edu_class_docs DROP CONSTRAINT ck_edu_class_docs_status;
ALTER TABLE edu_class_docs
    ADD CONSTRAINT ck_edu_class_docs_status
        CHECK (status IN ('DRAFT', 'PENDING_TL', 'PENDING_VP', 'PENDING_HT', 'REJECTED', 'APPROVED', 'REVIEW'));

-- 4. Bảng sổ người soạn edu_document_authors
CREATE TABLE edu_document_authors (
    id                BIGSERIAL    PRIMARY KEY,
    year_plan_id      BIGINT,
    class_doc_id      BIGINT,
    user_id           BIGINT       NOT NULL,
    origin            VARCHAR(30)  NOT NULL,
    first_authored_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_authored_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_edu_document_authors_year_plan FOREIGN KEY (year_plan_id) REFERENCES edu_year_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_edu_document_authors_class_doc FOREIGN KEY (class_doc_id) REFERENCES edu_class_docs (id) ON DELETE CASCADE,
    CONSTRAINT fk_edu_document_authors_user FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_edu_document_authors_target CHECK (
        (year_plan_id IS NOT NULL AND class_doc_id IS NULL)
        OR (year_plan_id IS NULL AND class_doc_id IS NOT NULL)
    ),
    CONSTRAINT ck_edu_document_authors_origin CHECK (
        origin IN ('CREATOR', 'CONTENT_WRITE', 'LEGACY_CREATOR')
    )
);

CREATE UNIQUE INDEX uq_edu_document_authors_year_plan
    ON edu_document_authors (year_plan_id, user_id) WHERE year_plan_id IS NOT NULL;

CREATE UNIQUE INDEX uq_edu_document_authors_class_doc
    ON edu_document_authors (class_doc_id, user_id) WHERE class_doc_id IS NOT NULL;

CREATE INDEX idx_edu_document_authors_user_id ON edu_document_authors (user_id);

-- 5. Backfill dữ liệu legacy
UPDATE edu_year_plans SET author_coverage = 'LEGACY_BASELINE';

INSERT INTO edu_document_authors (year_plan_id, user_id, origin, first_authored_at, last_authored_at)
SELECT id, owner_id, 'LEGACY_CREATOR', created_at, created_at
FROM edu_year_plans
ON CONFLICT DO NOTHING;

UPDATE edu_year_plans
SET print_snapshot = jsonb_build_object(
        'schemaVersion', 1,
        'capturedAt', COALESCE(issued_at, updated_at, now()),
        'origin', 'LEGACY_BASELINE',
        'documentType', 'year',
        'policyRevision', 1,
        'slots', '[]'::jsonb
    ),
    published_by = COALESCE(published_by, issued_by),
    completed_at = COALESCE(completed_at, issued_at, updated_at),
    completion_mode = COALESCE(completion_mode, 'LEGACY')
WHERE status = 'APPROVED' AND print_snapshot IS NULL;

UPDATE edu_class_docs SET author_coverage = 'LEGACY_BASELINE';

INSERT INTO edu_document_authors (class_doc_id, user_id, origin, first_authored_at, last_authored_at)
SELECT id, created_by, 'LEGACY_CREATOR', created_at, created_at
FROM edu_class_docs
ON CONFLICT DO NOTHING;

UPDATE edu_class_docs
SET print_snapshot = jsonb_build_object(
        'schemaVersion', 1,
        'capturedAt', updated_at,
        'origin', 'LEGACY_BASELINE',
        'documentType', kind,
        'policyRevision', 1,
        'slots', '[]'::jsonb
    ),
    completed_at = COALESCE(completed_at, updated_at),
    completion_mode = COALESCE(completion_mode, 'LEGACY')
WHERE status = 'APPROVED' AND print_snapshot IS NULL;
