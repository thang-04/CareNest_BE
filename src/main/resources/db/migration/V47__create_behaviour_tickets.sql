-- Phiếu bé ngoan (30/10/2026): GV lớp phát cho trẻ đủ điều kiện sau khi kỳ kết thúc.
-- WEEKLY = Bé ngoan (tuần thứ 2–6: ≥ 4 ngày có mặt và ≥ 3 cờ), MONTHLY = Bé ngoan toàn diện (≥ 3 phiếu Bé ngoan trong tháng).
-- Phát rồi không thu hồi; mỗi trẻ một phiếu mỗi loại mỗi kỳ.
CREATE TABLE behaviour_tickets (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id       BIGINT      NOT NULL REFERENCES children (id),
    classroom_id   BIGINT      NOT NULL REFERENCES classrooms (id),
    campus_id      BIGINT      NOT NULL REFERENCES campuses (id),
    school_year_id BIGINT      NOT NULL REFERENCES school_years (id),
    type           VARCHAR(10) NOT NULL,
    period_start   DATE        NOT NULL,
    period_end     DATE        NOT NULL,
    present_days   INT,
    flag_count     INT,
    weekly_tickets INT,
    issued_by      BIGINT      NOT NULL REFERENCES user_accounts (id),
    issued_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_behaviour_tickets_child_type_period UNIQUE (child_id, type, period_start),
    CONSTRAINT ck_behaviour_tickets_type CHECK (type IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_behaviour_tickets_period CHECK (period_end >= period_start)
);

CREATE INDEX idx_behaviour_tickets_classroom_period ON behaviour_tickets (classroom_id, period_start);
