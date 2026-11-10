-- Nhận xét tổng hợp theo tuần/cuối kỳ (OBS-05/06): DRAFT → GV sửa → GV APPROVED → BGH xem.
-- ai_draft giữ nguyên bản AI soạn (JSON, không bao giờ sửa); content là bản GV sửa, thành bản chính thức khi APPROVED.
-- source = TEMPLATE khi AI tắt/lỗi (OBS-04) — khi đó ai_draft rỗng.
CREATE TABLE child_summaries (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id          BIGINT       NOT NULL,
    period_type       VARCHAR(10)  NOT NULL,
    period_from       DATE         NOT NULL,
    period_to         DATE         NOT NULL,
    source            VARCHAR(10)  NOT NULL,
    status            VARCHAR(10)  NOT NULL,
    ai_draft          TEXT,
    content           TEXT         NOT NULL,
    ai_provider       VARCHAR(50),
    ai_model          VARCHAR(100),
    ai_prompt_version VARCHAR(50),
    generated_at      TIMESTAMPTZ  NOT NULL,
    created_by        BIGINT       NOT NULL,
    approved_by       BIGINT,
    approved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_child_summaries_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_child_summaries_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_child_summaries_approved_by FOREIGN KEY (approved_by) REFERENCES user_accounts (id),
    CONSTRAINT uk_child_summaries_child_period UNIQUE (child_id, period_type, period_from),
    CONSTRAINT ck_child_summaries_period_type CHECK (period_type IN ('WEEK', 'TERM')),
    CONSTRAINT ck_child_summaries_source CHECK (source IN ('AI', 'TEMPLATE')),
    CONSTRAINT ck_child_summaries_status CHECK (status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT ck_child_summaries_period_range CHECK (period_to >= period_from),
    CONSTRAINT ck_child_summaries_approval CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE INDEX idx_child_summaries_child_id ON child_summaries (child_id);
