-- NUT-13: bộ thực đơn mẫu 4 tuần (thứ 2–6) theo khối, có nhãn mùa; kế hoạch tuần sao một tuần của bộ để xoay vòng.
CREATE TABLE menu_template_sets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    season_label VARCHAR(50)  NOT NULL DEFAULT '',
    menu_block   VARCHAR(20)  NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_by   BIGINT       REFERENCES user_accounts (id),
    updated_by   BIGINT       REFERENCES user_accounts (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_menu_template_sets_block CHECK (menu_block IN ('NURSERY', 'KINDERGARTEN')),
    CONSTRAINT ck_menu_template_sets_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE menu_template_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    set_id     BIGINT      NOT NULL REFERENCES menu_template_sets (id) ON DELETE CASCADE,
    week_no    SMALLINT    NOT NULL,
    weekday    SMALLINT    NOT NULL,
    session    VARCHAR(20) NOT NULL,
    dish_id    BIGINT      NOT NULL REFERENCES dishes (id),
    sort_order SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_menu_template_items UNIQUE (set_id, week_no, weekday, session, dish_id),
    CONSTRAINT ck_menu_template_items_week CHECK (week_no BETWEEN 1 AND 4),
    CONSTRAINT ck_menu_template_items_weekday CHECK (weekday BETWEEN 1 AND 5),
    CONSTRAINT ck_menu_template_items_session CHECK (session IN ('MORNING', 'LUNCH', 'AFTERNOON'))
);
CREATE INDEX idx_menu_template_items_dish ON menu_template_items (dish_id);
