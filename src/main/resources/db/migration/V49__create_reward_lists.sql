-- Đề xuất khen thưởng cuối năm theo danh sách lớp (Cần chốt 30/10/2026): GV lớp chọn danh hiệu cho từng trẻ
-- (trẻ đã có đánh giá cuối năm xác nhận), gửi cả danh sách; PHT điểm trường chuyển HT hoặc trả lại; HT phê duyệt
-- hoặc trả lại. Mỗi lớp (của một năm học) một danh sách.
CREATE TABLE reward_lists (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    classroom_id     BIGINT      NOT NULL REFERENCES classrooms (id),
    campus_id        BIGINT      NOT NULL REFERENCES campuses (id),
    school_year_id   BIGINT      NOT NULL REFERENCES school_years (id),
    status           VARCHAR(20) NOT NULL,
    created_by       BIGINT      NOT NULL REFERENCES user_accounts (id),
    updated_by       BIGINT      NOT NULL REFERENCES user_accounts (id),
    submitted_at     TIMESTAMPTZ,
    review_by        BIGINT REFERENCES user_accounts (id),
    review_at        TIMESTAMPTZ,
    review_decision  VARCHAR(10),
    review_comment   VARCHAR(500),
    decision_by      BIGINT REFERENCES user_accounts (id),
    decision_at      TIMESTAMPTZ,
    decision         VARCHAR(10),
    decision_comment VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_reward_lists_classroom UNIQUE (classroom_id),
    CONSTRAINT ck_reward_lists_status CHECK (
        status IN ('DRAFT', 'PENDING_VP', 'PENDING_PRINCIPAL', 'APPROVED', 'RETURNED')),
    CONSTRAINT ck_reward_lists_review_decision CHECK (review_decision IS NULL OR review_decision IN ('FORWARD', 'RETURN')),
    CONSTRAINT ck_reward_lists_decision CHECK (decision IS NULL OR decision IN ('APPROVE', 'RETURN'))
);

-- Danh hiệu của một trẻ, nhiều danh hiệu nối bằng '|'
CREATE TABLE reward_list_items (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    list_id  BIGINT       NOT NULL REFERENCES reward_lists (id) ON DELETE CASCADE,
    child_id BIGINT       NOT NULL REFERENCES children (id),
    titles   VARCHAR(500) NOT NULL,
    note     VARCHAR(300),
    CONSTRAINT uk_reward_list_items_child UNIQUE (list_id, child_id)
);

CREATE TABLE reward_list_events (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    list_id BIGINT      NOT NULL REFERENCES reward_lists (id) ON DELETE CASCADE,
    action  VARCHAR(20) NOT NULL,
    user_id BIGINT      NOT NULL REFERENCES user_accounts (id),
    at      TIMESTAMPTZ NOT NULL,
    note    VARCHAR(500),
    CONSTRAINT ck_reward_list_events_action CHECK (
        action IN ('CREATED', 'UPDATED', 'SUBMITTED', 'RESUBMITTED', 'FORWARDED', 'RETURNED', 'APPROVED'))
);

CREATE INDEX idx_reward_list_events_list ON reward_list_events (list_id);
