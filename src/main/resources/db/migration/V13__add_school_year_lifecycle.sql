-- Vòng đời năm học: PLANNED (sắp diễn ra) → ACTIVE (đang hoạt động) → CLOSED (đã kết thúc, chỉ đọc).
-- Năm cũ: đã qua end_date ⇒ CLOSED; năm mới nhất đang chứa ngày hiện tại ⇒ ACTIVE; còn lại PLANNED.
ALTER TABLE school_years ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PLANNED';
ALTER TABLE school_years ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE school_years ADD COLUMN activated_by BIGINT;
ALTER TABLE school_years ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE school_years ADD COLUMN closed_by BIGINT;

ALTER TABLE school_years
    ADD CONSTRAINT ck_school_years_status CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED'));
ALTER TABLE school_years
    ADD CONSTRAINT fk_school_years_activated_by FOREIGN KEY (activated_by) REFERENCES user_accounts (id);
ALTER TABLE school_years
    ADD CONSTRAINT fk_school_years_closed_by FOREIGN KEY (closed_by) REFERENCES user_accounts (id);

UPDATE school_years SET status = 'CLOSED', closed_at = now() WHERE end_date < CURRENT_DATE;

UPDATE school_years SET status = 'ACTIVE', activated_at = now()
WHERE id = (
    SELECT id FROM school_years
    WHERE start_date <= CURRENT_DATE AND end_date >= CURRENT_DATE
    ORDER BY start_date DESC
    LIMIT 1
);

-- Toàn trường chỉ có tối đa một năm học ACTIVE
CREATE UNIQUE INDEX uk_school_years_active ON school_years (status) WHERE status = 'ACTIVE';
