-- G94-180 hồ sơ sức khỏe đầy đủ (2026-11-10).
-- 1) Thông báo: tiếp xúc người bệnh lao (CHD-07), kết quả sức khỏe cho phụ huynh (HLT-11, PAR-03), mở đợt khám/kỳ cân đo.
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED'));

-- 2) Giải thích xu hướng sức khỏe do AI viết (HLT-04, HLT-05, AI-01, AI-02): luôn là nháp tới khi nhân sự duyệt;
-- phụ huynh chỉ thấy bản đã duyệt mới nhất. Cảnh báo xu hướng tính xác định từ số đo (HLT-02), lưu kèm để xem lại.
CREATE TABLE health_interpretations (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id          BIGINT       NOT NULL REFERENCES children (id),
    status            VARCHAR(20)  NOT NULL,
    summary           TEXT         NOT NULL,
    warnings          JSONB        NOT NULL,
    measurement_ids   JSONB        NOT NULL,
    source            VARCHAR(10)  NOT NULL,
    model             VARCHAR(100),
    generated_by      BIGINT       NOT NULL REFERENCES user_accounts (id),
    generated_at      TIMESTAMPTZ  NOT NULL,
    approved_by       BIGINT REFERENCES user_accounts (id),
    approved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_health_interpretations_status CHECK (status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT ck_health_interpretations_source CHECK (source IN ('AI', 'HUMAN')),
    CONSTRAINT ck_health_interpretations_approval CHECK (
        (status = 'APPROVED') = (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);

CREATE INDEX idx_health_interpretations_child ON health_interpretations (child_id, generated_at DESC);
