-- INS-05: yêu cầu và quyền mở ghi biên bản kiểm thực của ngày đã qua. Một dòng = một mẫu + một ngày + một điểm trường.
-- PENDING/APPROVED là dòng "đang mở": mỗi phạm vi chỉ một dòng; hết hạn (EXPIRED) tính lười khi có yêu cầu mới.
CREATE TABLE inspection_day_grants (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id         BIGINT       NOT NULL REFERENCES campuses (id),
    sheet_date        DATE         NOT NULL,
    tab               VARCHAR(10)  NOT NULL,
    status            VARCHAR(10)  NOT NULL,
    self_opened       BOOLEAN      NOT NULL DEFAULT FALSE,
    reason            VARCHAR(500) NOT NULL,
    requested_by      BIGINT       NOT NULL REFERENCES user_accounts (id),
    requested_by_name VARCHAR(255) NOT NULL,
    requested_at      TIMESTAMPTZ  NOT NULL,
    decided_by        BIGINT REFERENCES user_accounts (id),
    decided_by_name   VARCHAR(255),
    decided_at        TIMESTAMPTZ,
    decision_note     VARCHAR(500),
    expires_at        TIMESTAMPTZ,
    consumed_at       TIMESTAMPTZ,
    CONSTRAINT ck_inspection_day_grants_tab CHECK (tab IN ('FRESH', 'DRY', 'PROCESS', 'SERVE', 'SAMPLE')),
    CONSTRAINT ck_inspection_day_grants_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED', 'CONSUMED', 'EXPIRED')),
    CONSTRAINT ck_inspection_day_grants_decided CHECK (status = 'PENDING' OR decided_at IS NOT NULL),
    CONSTRAINT ck_inspection_day_grants_expiry CHECK (status <> 'APPROVED' OR expires_at IS NOT NULL)
);
CREATE UNIQUE INDEX uk_inspection_day_grants_open
    ON inspection_day_grants (campus_id, sheet_date, tab)
    WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX idx_inspection_day_grants_day ON inspection_day_grants (campus_id, sheet_date);
CREATE INDEX idx_inspection_day_grants_queue ON inspection_day_grants (status, requested_at);
