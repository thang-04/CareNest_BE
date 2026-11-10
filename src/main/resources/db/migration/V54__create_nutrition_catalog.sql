-- Danh mục bán trú dùng chung hai điểm trường (SCH-06): chất dị ứng, thực phẩm, món, cấu hình nhóm tuổi, nhà cung cấp.
-- Xóa = ngừng dùng (status INACTIVE) vì thực đơn, kho tham chiếu các dòng này.

-- HLT-06: danh mục chất dị ứng cố định; mục do trường thêm có system_defined = FALSE (module child làm sau)
CREATE TABLE allergens (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(30)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    system_defined BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     SMALLINT     NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_allergens_code UNIQUE (code)
);

INSERT INTO allergens (code, name, sort_order) VALUES
    ('SHRIMP', 'Tôm', 1),
    ('CRAB', 'Cua', 2),
    ('FISH', 'Cá', 3),
    ('EGG', 'Trứng', 4),
    ('COW_MILK', 'Sữa bò', 5),
    ('PEANUT', 'Đậu phộng', 6),
    ('SOY', 'Đậu nành', 7),
    ('WHEAT', 'Lúa mì', 8),
    ('SESAME', 'Mè (vừng)', 9);

-- NUT-05: dinh dưỡng phải có nguồn; giá trị tính trên 100 đơn vị (g/ml). NUT-10: FRESH giao hằng ngày, DRY vào kho khô
CREATE TABLE foods (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                    VARCHAR(20)   NOT NULL,
    name                    VARCHAR(150)  NOT NULL,
    food_group              VARCHAR(20)   NOT NULL,
    unit                    VARCHAR(5)    NOT NULL,
    purchase_unit           VARCHAR(30)   NOT NULL,
    purchase_unit_size      NUMERIC(10, 2) NOT NULL,
    unit_price              NUMERIC(12, 2) NOT NULL,
    kcal                    NUMERIC(8, 2) NOT NULL,
    protein                 NUMERIC(8, 2) NOT NULL,
    lipid                   NUMERIC(8, 2) NOT NULL,
    glucid                  NUMERIC(8, 2) NOT NULL,
    nutrient_source         VARCHAR(200)  NOT NULL,
    nutrient_source_version VARCHAR(50),
    waste_pct               NUMERIC(5, 2) NOT NULL DEFAULT 0,
    storage                 VARCHAR(10)   NOT NULL,
    note                    VARCHAR(500)  NOT NULL DEFAULT '',
    status                  VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    created_by              BIGINT        REFERENCES user_accounts (id),
    updated_by              BIGINT        REFERENCES user_accounts (id),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_foods_code UNIQUE (code),
    CONSTRAINT ck_foods_group CHECK (food_group IN (
        'GRAIN', 'MEAT', 'SEAFOOD', 'EGG_DAIRY', 'LEGUME', 'VEGETABLE', 'FRUIT', 'FAT_SUGAR', 'OTHER')),
    CONSTRAINT ck_foods_unit CHECK (unit IN ('g', 'ml')),
    CONSTRAINT ck_foods_purchase_unit_size CHECK (purchase_unit_size > 0),
    CONSTRAINT ck_foods_non_negative CHECK (
        unit_price >= 0 AND kcal >= 0 AND protein >= 0 AND lipid >= 0 AND glucid >= 0),
    CONSTRAINT ck_foods_waste_pct CHECK (waste_pct >= 0 AND waste_pct < 100),
    CONSTRAINT ck_foods_storage CHECK (storage IN ('FRESH', 'DRY')),
    CONSTRAINT ck_foods_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE food_allergens (
    food_id     BIGINT NOT NULL REFERENCES foods (id) ON DELETE CASCADE,
    allergen_id BIGINT NOT NULL REFERENCES allergens (id),
    PRIMARY KEY (food_id, allergen_id)
);

-- NUT-18: món = công thức + gam/trẻ theo nhóm tuổi; món tham khảo (reference) chỉ đọc, sao chép mới sửa
CREATE TABLE dishes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    dish_type   VARCHAR(20)  NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    reference   BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_by  BIGINT       REFERENCES user_accounts (id),
    updated_by  BIGINT       REFERENCES user_accounts (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_dishes_code UNIQUE (code),
    CONSTRAINT ck_dishes_type CHECK (dish_type IN ('CARB', 'MAIN', 'SOUP', 'VEGETABLE', 'SNACK', 'DESSERT')),
    CONSTRAINT ck_dishes_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE dish_ingredients (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dish_id         BIGINT        NOT NULL REFERENCES dishes (id) ON DELETE CASCADE,
    food_id         BIGINT        NOT NULL REFERENCES foods (id),
    age_group_id    BIGINT        NOT NULL REFERENCES age_groups (id),
    grams_per_child NUMERIC(8, 2) NOT NULL,
    CONSTRAINT uk_dish_ingredients UNIQUE (dish_id, food_id, age_group_id),
    CONSTRAINT ck_dish_ingredients_grams CHECK (grams_per_child > 0)
);
CREATE INDEX idx_dish_ingredients_food ON dish_ingredients (food_id);

-- NUT-17, NUT-19: giá suất và chuẩn dinh dưỡng theo nhóm tuổi, có ngày hiệu lực. Bữa trong ngày ở age_groups.meal_sessions.
-- Chuẩn để trống được khi trường chưa chốt nguồn; đã có thì min <= max.
CREATE TABLE age_group_meal_configs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    age_group_id     BIGINT         NOT NULL REFERENCES age_groups (id),
    effective_from   DATE           NOT NULL,
    meal_price       NUMERIC(12, 2) NOT NULL,
    kcal_min         NUMERIC(8, 2),
    kcal_max         NUMERIC(8, 2),
    protein_pct_min  NUMERIC(5, 2),
    protein_pct_max  NUMERIC(5, 2),
    lipid_pct_min    NUMERIC(5, 2),
    lipid_pct_max    NUMERIC(5, 2),
    glucid_pct_min   NUMERIC(5, 2),
    glucid_pct_max   NUMERIC(5, 2),
    norm_basis       VARCHAR(300)   NOT NULL DEFAULT '',
    created_by       BIGINT         REFERENCES user_accounts (id),
    updated_by       BIGINT         REFERENCES user_accounts (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_age_group_meal_configs UNIQUE (age_group_id, effective_from),
    CONSTRAINT ck_age_group_meal_configs_price CHECK (meal_price >= 0),
    CONSTRAINT ck_age_group_meal_configs_ranges CHECK (
        (kcal_min IS NULL OR kcal_max IS NULL OR kcal_min <= kcal_max)
        AND (protein_pct_min IS NULL OR protein_pct_max IS NULL OR protein_pct_min <= protein_pct_max)
        AND (lipid_pct_min IS NULL OR lipid_pct_max IS NULL OR lipid_pct_min <= lipid_pct_max)
        AND (glucid_pct_min IS NULL OR glucid_pct_max IS NULL OR glucid_pct_min <= glucid_pct_max))
);

-- NUT-12: NCC dùng chung hai điểm trường; id_card là dữ liệu cá nhân
CREATE TABLE suppliers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    address         VARCHAR(300) NOT NULL DEFAULT '',
    id_card         VARCHAR(20),
    delivery_person VARCHAR(100) NOT NULL DEFAULT '',
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_by      BIGINT       REFERENCES user_accounts (id),
    updated_by      BIGINT       REFERENCES user_accounts (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
