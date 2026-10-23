-- Sổ báo ăn (số suất) theo điểm trường/ngày/bữa, tổng hợp lúc khóa điểm danh (NUT-01, NUT-02, NUT-03, ATT-07).
-- Số đã xác nhận không sửa ngầm: thay đổi sau xác nhận ghi vào dòng pending và chờ xác nhận lại (ADR-0005).
CREATE TABLE meal_counts (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id            BIGINT      NOT NULL REFERENCES campuses (id),
    school_year_id       BIGINT      NOT NULL REFERENCES school_years (id),
    date                 DATE        NOT NULL,
    session              VARCHAR(20) NOT NULL,
    status               VARCHAR(30) NOT NULL,
    needs_reconfirmation BOOLEAN     NOT NULL DEFAULT FALSE,
    aggregated_at        TIMESTAMPTZ NOT NULL,
    confirmed_by         BIGINT REFERENCES user_accounts (id),
    confirmed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_meal_counts_campus_date_session UNIQUE (campus_id, date, session),
    CONSTRAINT ck_meal_counts_session CHECK (session IN ('MORNING', 'LUNCH', 'AFTERNOON')),
    CONSTRAINT ck_meal_counts_status CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED'))
);

-- Số suất từng lớp của một bữa. pending = số mới chờ xác nhận lại; bếp chỉ thấy dòng pending = false.
CREATE TABLE meal_count_lines (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    meal_count_id BIGINT  NOT NULL REFERENCES meal_counts (id) ON DELETE CASCADE,
    classroom_id  BIGINT  NOT NULL REFERENCES classrooms (id),
    pending       BOOLEAN NOT NULL,
    present       INTEGER NOT NULL,
    absent        INTEGER NOT NULL,
    missing       INTEGER NOT NULL,
    normal        INTEGER NOT NULL,
    substitute    INTEGER NOT NULL,
    CONSTRAINT uk_meal_count_lines UNIQUE (meal_count_id, classroom_id, pending),
    CONSTRAINT ck_meal_count_lines_non_negative
        CHECK (present >= 0 AND absent >= 0 AND missing >= 0 AND normal >= 0 AND substitute >= 0)
);

-- Lịch sử sổ: tổng hợp, xác nhận, điều chỉnh (kèm chênh lệch + lý do), điểm danh đổi sau khi mở lại ngày.
CREATE TABLE meal_count_history (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    meal_count_id    BIGINT      NOT NULL REFERENCES meal_counts (id) ON DELETE CASCADE,
    action           VARCHAR(30) NOT NULL,
    classroom_id     BIGINT REFERENCES classrooms (id),
    delta_normal     INTEGER     NOT NULL DEFAULT 0,
    delta_substitute INTEGER     NOT NULL DEFAULT 0,
    note             VARCHAR(200),
    user_id          BIGINT REFERENCES user_accounts (id),
    at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_meal_count_history_action CHECK (action IN (
        'AGGREGATED', 'CONFIRMED', 'ADJUSTED', 'ATTENDANCE_CHANGED'))
);

CREATE INDEX idx_meal_count_history_count ON meal_count_history (meal_count_id);

