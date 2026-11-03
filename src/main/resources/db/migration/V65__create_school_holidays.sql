-- Ngày nghỉ (lễ, Tết…) theo năm học: không phải ngày học dù rơi vào thứ 2–6 (SCH-05, user chốt 03/11/2026).
CREATE TABLE school_holidays (
    id              BIGSERIAL PRIMARY KEY,
    school_year_id  BIGINT       NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    date            DATE         NOT NULL,
    name            VARCHAR(100) NOT NULL,
    CONSTRAINT uk_school_holidays_date UNIQUE (date)
);

CREATE INDEX idx_school_holidays_year ON school_holidays (school_year_id);
