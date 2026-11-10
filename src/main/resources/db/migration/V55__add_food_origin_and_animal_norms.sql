-- NUT-24: nguồn gốc thực phẩm để tách đạm, béo động vật / thực vật (bảng dinh dưỡng PMS).
-- Dữ liệu sẵn có gán theo nhóm: thịt, thủy sản, trứng sữa là động vật; còn lại thực vật — PHT sửa được.
ALTER TABLE foods ADD COLUMN origin VARCHAR(10);
UPDATE foods SET origin = CASE WHEN food_group IN ('MEAT', 'SEAFOOD', 'EGG_DAIRY') THEN 'ANIMAL' ELSE 'PLANT' END;
ALTER TABLE foods ALTER COLUMN origin SET NOT NULL;
ALTER TABLE foods ADD CONSTRAINT ck_foods_origin CHECK (origin IN ('ANIMAL', 'PLANT'));

-- Định mức tỷ lệ đạm, béo động vật trên tổng (%); để trống khi trường chưa chốt
ALTER TABLE age_group_meal_configs ADD COLUMN animal_protein_pct_min NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN animal_protein_pct_max NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN animal_lipid_pct_min NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD COLUMN animal_lipid_pct_max NUMERIC(5, 2);
ALTER TABLE age_group_meal_configs ADD CONSTRAINT ck_age_group_meal_configs_animal_ranges CHECK (
    (animal_protein_pct_min IS NULL OR animal_protein_pct_max IS NULL OR animal_protein_pct_min <= animal_protein_pct_max)
    AND (animal_lipid_pct_min IS NULL OR animal_lipid_pct_max IS NULL OR animal_lipid_pct_min <= animal_lipid_pct_max));
