ALTER TABLE edu_year_plans DROP CONSTRAINT ck_edu_year_plans_editing;

-- Khóa cũ không có định danh phiên; nhả khi nâng cấp, giữ nguyên nội dung.
UPDATE edu_year_plans SET status = editing_from WHERE status = 'EDITING';
UPDATE edu_year_plans SET editing_by = NULL, editing_since = NULL, editing_at = NULL, editing_from = NULL;

ALTER TABLE edu_year_plans
    ADD COLUMN edit_session_id UUID,
    ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_save_request_id UUID,
    ADD COLUMN created_request_id UUID,
    ADD CONSTRAINT uq_edu_year_plan_create_request UNIQUE (owner_id, created_request_id),
    ADD CONSTRAINT ck_edu_year_plan_edit_session CHECK (
        (edit_session_id IS NULL AND editing_by IS NULL AND editing_since IS NULL AND editing_at IS NULL AND editing_from IS NULL AND status <> 'EDITING')
        OR (edit_session_id IS NOT NULL AND editing_by IS NOT NULL AND editing_since IS NOT NULL AND editing_at IS NOT NULL AND editing_from IS NOT NULL
            AND ((status = 'EDITING' AND editing_from IN ('DRAFT', 'REJECTED'))
                OR (status = 'PENDING_VP' AND editing_from = 'PENDING_VP')))
    );

CREATE INDEX idx_edu_year_plan_active_session ON edu_year_plans (editing_at) WHERE edit_session_id IS NOT NULL;
