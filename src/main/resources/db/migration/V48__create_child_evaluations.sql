-- Đánh giá tháng / cuối năm (30/10/2026): hệ thống tạo bản nháp từ đánh giá hằng ngày khi tháng kết thúc
-- (cuối năm: từ tháng cuối của năm học); GV lớp sửa, xác nhận. PHT / HT chỉ xem bản đã xác nhận.
CREATE TABLE child_evaluations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id        BIGINT      NOT NULL REFERENCES children (id),
    classroom_id    BIGINT      NOT NULL REFERENCES classrooms (id),
    campus_id       BIGINT      NOT NULL REFERENCES campuses (id),
    school_year_id  BIGINT      NOT NULL REFERENCES school_years (id),
    period_type     VARCHAR(10) NOT NULL,
    period_start    DATE        NOT NULL,
    period_end      DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    draft_content   TEXT,
    draft_at        TIMESTAMPTZ,
    final_content   VARCHAR(3000),
    -- Trẻ 5 tuổi hoàn thành chương trình GDMN (chỉ đánh giá cuối năm), null = chưa đánh dấu
    program_completed BOOLEAN,
    confirmed_by    BIGINT REFERENCES user_accounts (id),
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_child_evaluations_child_period UNIQUE (child_id, period_type, period_start),
    CONSTRAINT ck_child_evaluations_period_type CHECK (period_type IN ('MONTH', 'YEAR')),
    CONSTRAINT ck_child_evaluations_status CHECK (status IN ('AWAITING_REVIEW', 'CONFIRMED')),
    CONSTRAINT ck_child_evaluations_period CHECK (period_end >= period_start),
    CONSTRAINT ck_child_evaluations_confirmed CHECK (
        status <> 'CONFIRMED' OR (final_content IS NOT NULL AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))
);

CREATE INDEX idx_child_evaluations_classroom_type ON child_evaluations (classroom_id, period_type, period_start);

-- Vết xử lý từng đánh giá: tạo bản nháp, tạo lại, lưu sửa, xác nhận
CREATE TABLE child_evaluation_events (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evaluation_id BIGINT      NOT NULL REFERENCES child_evaluations (id) ON DELETE CASCADE,
    action        VARCHAR(20) NOT NULL,
    user_id       BIGINT REFERENCES user_accounts (id),
    at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_child_evaluation_events_action CHECK (action IN ('AI_DRAFTED', 'AI_REDRAFTED', 'EDITED', 'CONFIRMED'))
);

CREATE INDEX idx_child_evaluation_events_evaluation ON child_evaluation_events (evaluation_id);
