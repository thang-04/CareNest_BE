-- Đơn nghỉ của phụ huynh (ATT-04, ATT-05, ATT-05b): thông báo, không duyệt. Các ngày học còn trước giờ chốt được ghi
-- Vắng có phép, không suất. Hủy: các ngày chưa qua giờ chốt từ cancelled_from bỏ ghi; hiệu lực tới cancelled_from - 1.
CREATE TABLE leave_requests (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id       BIGINT       NOT NULL REFERENCES children (id),
    from_date      DATE         NOT NULL,
    to_date        DATE         NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    created_by     BIGINT       NOT NULL REFERENCES user_accounts (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancelled_from DATE,
    cancelled_by   BIGINT REFERENCES user_accounts (id),
    cancelled_at   TIMESTAMPTZ,
    CONSTRAINT ck_leave_requests_range CHECK (to_date >= from_date),
    CONSTRAINT ck_leave_requests_cancel
        CHECK (cancelled_from IS NULL OR cancelled_from BETWEEN from_date AND to_date + 1)
);
CREATE INDEX idx_leave_requests_child_dates ON leave_requests (child_id, from_date, to_date);
