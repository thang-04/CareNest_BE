-- NUT-26: Kẽm trên 100 g/ml phần ăn được; null = chưa có số liệu.
ALTER TABLE foods
    ADD COLUMN zinc NUMERIC(10, 3),
    ADD CONSTRAINT ck_foods_zinc_non_negative CHECK (zinc IS NULL OR zinc >= 0);

-- Định mức Kẽm theo nhóm tuổi; max trống = chỉ cần đạt tối thiểu.
ALTER TABLE age_group_meal_configs
    ADD COLUMN zinc_min NUMERIC(10, 3),
    ADD COLUMN zinc_max NUMERIC(10, 3),
    ADD CONSTRAINT ck_age_group_meal_configs_zinc_range CHECK (
        (zinc_min IS NULL OR zinc_min >= 0)
        AND (zinc_max IS NULL OR zinc_max >= 0)
        AND (zinc_min IS NULL OR zinc_max IS NULL OR zinc_min <= zinc_max));
