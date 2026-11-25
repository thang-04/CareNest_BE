-- Nutrition-only ON/OFF, independent of effective-dated config versions.
CREATE TABLE nutrition_group_settings (
    age_group_id BIGINT PRIMARY KEY REFERENCES age_groups(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
