-- NUT-24 (user chốt 03/11/2026): định mức nhóm tuổi nhập và lưu theo gam/trẻ/ngày cho đạm, béo động vật / thực vật và
-- bột đường; protein/lipid/glucid_pct_* giữ làm cơ cấu P:L:G áp dụng. NUT-25: tỷ lệ năng lượng từng bữa (% kcal ngày).
ALTER TABLE age_group_meal_configs ADD COLUMN animal_protein_g_min NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN animal_protein_g_max NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN plant_protein_g_min NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN plant_protein_g_max NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN animal_lipid_g_min NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN animal_lipid_g_max NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN plant_lipid_g_min NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN plant_lipid_g_max NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN glucid_g_min NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN glucid_g_max NUMERIC(6, 1);
ALTER TABLE age_group_meal_configs ADD COLUMN morning_kcal_pct_min NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN morning_kcal_pct_max NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN lunch_kcal_pct_min NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN lunch_kcal_pct_max NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN afternoon_kcal_pct_min NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN afternoon_kcal_pct_max NUMERIC(5, 2);

-- Đổi định mức cũ (% năng lượng) sang gam: năng lượng giữa khoảng × % ÷ 4 (đạm, bột đường) hoặc ÷ 9 (béo).
-- Phần động vật = tổng × % động vật; thực vật = phần còn lại. Chưa có % động vật thì không tách được ⇒ để trống.
WITH grams AS (
    SELECT id,
           (kcal_min + kcal_max) / 2 * protein_pct_min / 400 AS protein_min,
           (kcal_min + kcal_max) / 2 * protein_pct_max / 400 AS protein_max,
           (kcal_min + kcal_max) / 2 * lipid_pct_min / 900   AS lipid_min,
           (kcal_min + kcal_max) / 2 * lipid_pct_max / 900   AS lipid_max,
           (kcal_min + kcal_max) / 2 * glucid_pct_min / 400  AS glucid_min,
           (kcal_min + kcal_max) / 2 * glucid_pct_max / 400  AS glucid_max,
           animal_protein_pct_min, animal_protein_pct_max, animal_lipid_pct_min, animal_lipid_pct_max
    FROM age_group_meal_configs
    WHERE kcal_min IS NOT NULL AND kcal_max IS NOT NULL
)
UPDATE age_group_meal_configs c
SET animal_protein_g_min = ROUND(g.protein_min * g.animal_protein_pct_min / 100, 1),
    animal_protein_g_max = ROUND(g.protein_max * g.animal_protein_pct_max / 100, 1),
    plant_protein_g_min  = ROUND(g.protein_min * (100 - g.animal_protein_pct_max) / 100, 1),
    plant_protein_g_max  = ROUND(g.protein_max * (100 - g.animal_protein_pct_min) / 100, 1),
    animal_lipid_g_min   = ROUND(g.lipid_min * g.animal_lipid_pct_min / 100, 1),
    animal_lipid_g_max   = ROUND(g.lipid_max * g.animal_lipid_pct_max / 100, 1),
    plant_lipid_g_min    = ROUND(g.lipid_min * (100 - g.animal_lipid_pct_max) / 100, 1),
    plant_lipid_g_max    = ROUND(g.lipid_max * (100 - g.animal_lipid_pct_min) / 100, 1),
    glucid_g_min         = ROUND(g.glucid_min, 1),
    glucid_g_max         = ROUND(g.glucid_max, 1)
FROM grams g
WHERE c.id = g.id;

-- Cặp min/max phải cùng có hoặc cùng trống; bản thiếu một nửa thì bỏ cả cặp
UPDATE age_group_meal_configs SET animal_protein_g_min = NULL, animal_protein_g_max = NULL,
                                  plant_protein_g_min = NULL, plant_protein_g_max = NULL
WHERE animal_protein_g_min IS NULL OR animal_protein_g_max IS NULL
   OR plant_protein_g_min IS NULL OR plant_protein_g_max IS NULL;
UPDATE age_group_meal_configs SET animal_lipid_g_min = NULL, animal_lipid_g_max = NULL,
                                  plant_lipid_g_min = NULL, plant_lipid_g_max = NULL
WHERE animal_lipid_g_min IS NULL OR animal_lipid_g_max IS NULL
   OR plant_lipid_g_min IS NULL OR plant_lipid_g_max IS NULL;
UPDATE age_group_meal_configs SET glucid_g_min = NULL, glucid_g_max = NULL
WHERE glucid_g_min IS NULL OR glucid_g_max IS NULL;

ALTER TABLE age_group_meal_configs DROP CONSTRAINT ck_age_group_meal_configs_animal_ranges;
ALTER TABLE age_group_meal_configs DROP COLUMN animal_protein_pct_min;
ALTER TABLE age_group_meal_configs DROP COLUMN animal_protein_pct_max;
ALTER TABLE age_group_meal_configs DROP COLUMN animal_lipid_pct_min;
ALTER TABLE age_group_meal_configs DROP COLUMN animal_lipid_pct_max;

ALTER TABLE age_group_meal_configs ADD CONSTRAINT ck_age_group_meal_configs_gram_ranges CHECK (
    (animal_protein_g_min IS NULL OR animal_protein_g_max IS NULL OR animal_protein_g_min <= animal_protein_g_max)
    AND (plant_protein_g_min IS NULL OR plant_protein_g_max IS NULL OR plant_protein_g_min <= plant_protein_g_max)
    AND (animal_lipid_g_min IS NULL OR animal_lipid_g_max IS NULL OR animal_lipid_g_min <= animal_lipid_g_max)
    AND (plant_lipid_g_min IS NULL OR plant_lipid_g_max IS NULL OR plant_lipid_g_min <= plant_lipid_g_max)
    AND (glucid_g_min IS NULL OR glucid_g_max IS NULL OR glucid_g_min <= glucid_g_max));
ALTER TABLE age_group_meal_configs ADD CONSTRAINT ck_age_group_meal_configs_session_ranges CHECK (
    (morning_kcal_pct_min IS NULL OR morning_kcal_pct_max IS NULL OR morning_kcal_pct_min <= morning_kcal_pct_max)
    AND (lunch_kcal_pct_min IS NULL OR lunch_kcal_pct_max IS NULL OR lunch_kcal_pct_min <= lunch_kcal_pct_max)
    AND (afternoon_kcal_pct_min IS NULL OR afternoon_kcal_pct_max IS NULL
         OR afternoon_kcal_pct_min <= afternoon_kcal_pct_max));
