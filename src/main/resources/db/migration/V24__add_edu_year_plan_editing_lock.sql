-- EDU-10: khóa "Đang sửa" cho kế hoạch năm học; người sửa, lúc bắt đầu, lần lưu cuối và trạng thái để trả về khi nhả khóa
ALTER TABLE edu_year_plans
    ADD COLUMN editing_by    BIGINT,
    ADD COLUMN editing_since TIMESTAMPTZ,
    ADD COLUMN editing_at    TIMESTAMPTZ,
    ADD COLUMN editing_from  VARCHAR(20),
    ADD CONSTRAINT fk_edu_year_plans_editing_by FOREIGN KEY (editing_by) REFERENCES user_accounts (id);

ALTER TABLE edu_year_plans DROP CONSTRAINT ck_edu_year_plans_status;
ALTER TABLE edu_year_plans
    ADD CONSTRAINT ck_edu_year_plans_status
        CHECK (status IN ('DRAFT', 'EDITING', 'PENDING_VP', 'REJECTED', 'APPROVED')),
    ADD CONSTRAINT ck_edu_year_plans_editing
        CHECK ((status = 'EDITING') = (editing_by IS NOT NULL AND editing_since IS NOT NULL AND editing_at IS NOT NULL
                                       AND editing_from IN ('DRAFT', 'REJECTED')));

-- Job nhả khóa quá hạn quét theo editing_at
CREATE INDEX idx_edu_year_plans_editing_at ON edu_year_plans (editing_at) WHERE status = 'EDITING';
