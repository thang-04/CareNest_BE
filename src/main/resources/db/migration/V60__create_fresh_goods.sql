-- NUT-07, NUT-10, NUT-12: bản dự trù hàng tươi theo kỳ của điểm trường, hàng tươi không vào kho nên chỉ kiểm nhận
-- 2 lần/ngày (lần 1 so bản dự trù, lần 2 so nhu cầu theo suất đã chốt). Số lượng theo đơn vị gốc (g/ml).
CREATE TABLE fresh_forecasts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id   BIGINT       NOT NULL REFERENCES campuses (id),
    period_from DATE         NOT NULL,
    period_to   DATE         NOT NULL,
    note        VARCHAR(500) NOT NULL DEFAULT '',
    created_by  BIGINT       REFERENCES user_accounts (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_fresh_forecasts_period CHECK (period_from <= period_to AND period_to - period_from <= 30)
);
CREATE INDEX idx_fresh_forecasts_campus ON fresh_forecasts (campus_id, period_from, period_to);

-- Sĩ số dự kiến theo nhóm tuổi: enrolled_count = số trẻ đang học lúc lập (tham chiếu), expected_count = số PHT chốt
CREATE TABLE fresh_forecast_counts (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    forecast_id    BIGINT NOT NULL REFERENCES fresh_forecasts (id) ON DELETE CASCADE,
    age_group_id   BIGINT NOT NULL REFERENCES age_groups (id),
    enrolled_count INT    NOT NULL,
    expected_count INT    NOT NULL,
    CONSTRAINT uk_fresh_forecast_counts UNIQUE (forecast_id, age_group_id),
    CONSTRAINT ck_fresh_forecast_counts CHECK (enrolled_count >= 0 AND expected_count >= 0)
);

CREATE TABLE fresh_forecast_lines (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    forecast_id BIGINT         NOT NULL REFERENCES fresh_forecasts (id) ON DELETE CASCADE,
    menu_date   DATE           NOT NULL,
    food_id     BIGINT         NOT NULL REFERENCES foods (id),
    quantity    NUMERIC(14, 2) NOT NULL,
    CONSTRAINT uk_fresh_forecast_lines UNIQUE (forecast_id, menu_date, food_id),
    CONSTRAINT ck_fresh_forecast_lines_qty CHECK (quantity >= 0)
);

-- Một lần kiểm nhận = (điểm trường, ngày, lần 1|2). forecast_id chỉ có ở lần 1; xóa bản dự trù không xóa lịch sử kiểm.
CREATE TABLE fresh_checks (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id   BIGINT       NOT NULL REFERENCES campuses (id),
    check_date  DATE         NOT NULL,
    round       SMALLINT     NOT NULL,
    forecast_id BIGINT       REFERENCES fresh_forecasts (id) ON DELETE SET NULL,
    checked_by  BIGINT       REFERENCES user_accounts (id),
    checked_at  TIMESTAMPTZ  NOT NULL,
    note        VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uk_fresh_checks UNIQUE (campus_id, check_date, round),
    CONSTRAINT ck_fresh_checks_round CHECK (round IN (1, 2))
);
CREATE INDEX idx_fresh_checks_forecast ON fresh_checks (forecast_id);

-- expected_qty chụp lại lượng cần nhận lúc ghi; PHT chọn đủ/thiếu/thừa, số thực nhận không bắt buộc
CREATE TABLE fresh_check_lines (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    check_id     BIGINT         NOT NULL REFERENCES fresh_checks (id) ON DELETE CASCADE,
    food_id      BIGINT         NOT NULL REFERENCES foods (id),
    expected_qty NUMERIC(14, 2) NOT NULL,
    received_qty NUMERIC(14, 2),
    result       VARCHAR(10)    NOT NULL,
    note         VARCHAR(300)   NOT NULL DEFAULT '',
    CONSTRAINT uk_fresh_check_lines UNIQUE (check_id, food_id),
    CONSTRAINT ck_fresh_check_lines_result CHECK (result IN ('ENOUGH', 'SHORT', 'OVER')),
    CONSTRAINT ck_fresh_check_lines_qty CHECK (expected_qty >= 0 AND (received_qty IS NULL OR received_qty >= 0))
);
