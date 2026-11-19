-- AI-01, AI-07, NUT-08: nháp gợi ý thực đơn (tuần theo khối, mẫu ngày, thay một món) chờ PHT dùng hoặc bỏ; chưa phải
-- thực đơn. Chỉ lưu lưới món đã map về dishId, điểm, issue và metadata; không lưu prompt/response thô (AI-05).
-- Không xóa nháp: lịch sử dùng để đo giá trị AI (tỉ lệ source=AI, APPLIED/DISMISSED).
CREATE TABLE menu_ai_drafts (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version           BIGINT      NOT NULL DEFAULT 0,
    kind              VARCHAR(20) NOT NULL,
    menu_block        VARCHAR(20) NOT NULL,
    week_start        DATE,
    target_date       DATE,
    target_session    VARCHAR(20),
    from_dish_id      BIGINT,
    status            VARCHAR(20) NOT NULL,
    source            VARCHAR(20),
    fallback_reason   VARCHAR(30),
    provider          VARCHAR(50),
    model             VARCHAR(100),
    prompt_version    VARCHAR(50),
    repair_rounds     INT         NOT NULL DEFAULT 0,
    latency_ms        INT         NOT NULL DEFAULT 0,
    request_json      JSONB       NOT NULL,
    output_json       JSONB       NOT NULL,
    score_json        JSONB       NOT NULL,
    issues_json       JSONB       NOT NULL,
    input_fingerprint VARCHAR(64) NOT NULL,
    created_by        BIGINT      NOT NULL REFERENCES user_accounts (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by        BIGINT      REFERENCES user_accounts (id),
    decided_at        TIMESTAMPTZ,
    CONSTRAINT ck_menu_ai_drafts_kind CHECK (kind IN ('MENU_WEEKLY', 'MENU_DAILY', 'MENU_DAY_FIX')),
    CONSTRAINT ck_menu_ai_drafts_block CHECK (menu_block IN ('NURSERY', 'KINDERGARTEN')),
    CONSTRAINT ck_menu_ai_drafts_status CHECK (status IN ('PENDING_REVIEW', 'APPLIED', 'DISMISSED', 'NO_FEASIBLE')),
    CONSTRAINT ck_menu_ai_drafts_source CHECK (source IN ('AI', 'RULE_BASED')),
    CONSTRAINT ck_menu_ai_drafts_fallback CHECK (fallback_reason IN
        ('AI_DISABLED', 'AI_UNAVAILABLE', 'AI_TIMEOUT', 'AI_INVALID', 'AI_NOT_BETTER')),
    CONSTRAINT ck_menu_ai_drafts_session CHECK (target_session IN ('MORNING', 'LUNCH', 'AFTERNOON')),
    -- Không có phương án dùng được thì không có nguồn; có phương án thì luôn ghi nguồn
    CONSTRAINT ck_menu_ai_drafts_source_status CHECK ((status = 'NO_FEASIBLE') = (source IS NULL)),
    -- source=AI chỉ khi qua cổng giá trị nên không có lý do dự phòng
    CONSTRAINT ck_menu_ai_drafts_ai_no_fallback CHECK (source IS DISTINCT FROM 'AI' OR fallback_reason IS NULL),
    CONSTRAINT ck_menu_ai_drafts_week CHECK (kind = 'MENU_DAILY'
        OR (week_start IS NOT NULL AND EXTRACT(ISODOW FROM week_start) = 1)),
    CONSTRAINT ck_menu_ai_drafts_day_fix CHECK (kind <> 'MENU_DAY_FIX'
        OR (target_date IS NOT NULL AND target_session IS NOT NULL AND from_dish_id IS NOT NULL)),
    CONSTRAINT ck_menu_ai_drafts_decided CHECK ((status IN ('APPLIED', 'DISMISSED')) = (decided_at IS NOT NULL))
);
CREATE INDEX idx_menu_ai_drafts_target ON menu_ai_drafts (kind, menu_block, week_start, created_at DESC);
