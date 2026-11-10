-- NUT-08, NUT-13: kế hoạch tuần (thứ 2–6) theo khối, công bố cả tuần một lần cho cả trường, đã công bố không sửa.
CREATE TABLE meal_plan_weeks (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    week_start   DATE        NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    published_by BIGINT      REFERENCES user_accounts (id),
    published_at TIMESTAMPTZ,
    created_by   BIGINT      REFERENCES user_accounts (id),
    updated_by   BIGINT      REFERENCES user_accounts (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_meal_plan_weeks_week_start UNIQUE (week_start),
    CONSTRAINT ck_meal_plan_weeks_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT ck_meal_plan_weeks_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

-- off = ngày không phục vụ bữa (nghỉ lễ...): không kiểm cân đối, không trừ kho
CREATE TABLE meal_plan_days (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    week_id   BIGINT       NOT NULL REFERENCES meal_plan_weeks (id) ON DELETE CASCADE,
    menu_date DATE         NOT NULL,
    off       BOOLEAN      NOT NULL DEFAULT FALSE,
    note      VARCHAR(300) NOT NULL DEFAULT '',
    CONSTRAINT uk_meal_plan_days UNIQUE (week_id, menu_date)
);

CREATE TABLE meal_plan_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    day_id     BIGINT      NOT NULL REFERENCES meal_plan_days (id) ON DELETE CASCADE,
    session    VARCHAR(20) NOT NULL,
    menu_block VARCHAR(20) NOT NULL,
    dish_id    BIGINT      NOT NULL REFERENCES dishes (id),
    sort_order SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_plan_items UNIQUE (day_id, session, menu_block, dish_id),
    CONSTRAINT ck_meal_plan_items_session CHECK (session IN ('MORNING', 'LUNCH', 'AFTERNOON')),
    CONSTRAINT ck_meal_plan_items_block CHECK (menu_block IN ('NURSERY', 'KINDERGARTEN'))
);
CREATE INDEX idx_meal_plan_items_dish ON meal_plan_items (dish_id);

-- NUT-11, P-38: nhu cầu kho khô theo suất đã xác nhận của điểm trường × ngày; shortfall_qty = phần chưa trừ được
-- (thiếu kho), trừ tiếp khi có phiếu nhập bổ sung. Đã trừ = required_qty − shortfall_qty.
CREATE TABLE stock_daily_demands (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id     BIGINT         NOT NULL REFERENCES campuses (id),
    meal_date     DATE           NOT NULL,
    food_id       BIGINT         NOT NULL REFERENCES foods (id),
    required_qty  NUMERIC(14, 2) NOT NULL,
    shortfall_qty NUMERIC(14, 2) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_stock_daily_demands UNIQUE (campus_id, meal_date, food_id),
    CONSTRAINT ck_stock_daily_demands_qty CHECK (
        required_qty >= 0 AND shortfall_qty >= 0 AND shortfall_qty <= required_qty)
);
CREATE INDEX idx_stock_daily_demands_shortfall ON stock_daily_demands (campus_id, food_id, meal_date)
    WHERE shortfall_qty > 0;
