-- Đợt nghỉ (lễ, Tết…) theo năm học: các ngày trong đợt không phải ngày học dù rơi vào thứ 2–6 (SCH-05,
-- user chốt 03/11/2026). Các đợt không chồng nhau (kiểm ở service).
CREATE TABLE school_holidays (
    id              BIGSERIAL PRIMARY KEY,
    school_year_id  BIGINT       NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    name            VARCHAR(100) NOT NULL,
    CONSTRAINT ck_school_holidays_range CHECK (end_date >= start_date)
);

CREATE INDEX idx_school_holidays_year ON school_holidays (school_year_id, start_date);
