-- NUT-26: 8 vi chất trên 100 g/ml ăn được theo bảng Viện Dinh dưỡng (canxi, photpho, sắt, vitamin A/B1/B2/PP/C).
-- Cột để trống = chưa có số liệu, không chặn lưu thực phẩm; đơn vị mg, riêng vitamin A là µg retinol equivalent
ALTER TABLE foods
    ADD COLUMN calcium    NUMERIC(10, 3),
    ADD COLUMN phosphorus NUMERIC(10, 3),
    ADD COLUMN iron       NUMERIC(10, 3),
    ADD COLUMN vitamin_a  NUMERIC(10, 3),
    ADD COLUMN vitamin_b1 NUMERIC(10, 3),
    ADD COLUMN vitamin_b2 NUMERIC(10, 3),
    ADD COLUMN vitamin_pp NUMERIC(10, 3),
    ADD COLUMN vitamin_c  NUMERIC(10, 3),
    ADD CONSTRAINT ck_foods_micronutrients_non_negative CHECK (
        (calcium IS NULL OR calcium >= 0)
        AND (phosphorus IS NULL OR phosphorus >= 0)
        AND (iron IS NULL OR iron >= 0)
        AND (vitamin_a IS NULL OR vitamin_a >= 0)
        AND (vitamin_b1 IS NULL OR vitamin_b1 >= 0)
        AND (vitamin_b2 IS NULL OR vitamin_b2 >= 0)
        AND (vitamin_pp IS NULL OR vitamin_pp >= 0)
        AND (vitamin_c IS NULL OR vitamin_c >= 0));

-- NUT-26: định mức vi chất mỗi trẻ mỗi ngày trên các bữa trường phục vụ; max trống = chỉ cần đạt tối thiểu
ALTER TABLE age_group_meal_configs
    ADD COLUMN calcium_min    NUMERIC(10, 3),
    ADD COLUMN calcium_max    NUMERIC(10, 3),
    ADD COLUMN phosphorus_min NUMERIC(10, 3),
    ADD COLUMN phosphorus_max NUMERIC(10, 3),
    ADD COLUMN iron_min       NUMERIC(10, 3),
    ADD COLUMN iron_max       NUMERIC(10, 3),
    ADD COLUMN vitamin_a_min  NUMERIC(10, 3),
    ADD COLUMN vitamin_a_max  NUMERIC(10, 3),
    ADD COLUMN vitamin_b1_min NUMERIC(10, 3),
    ADD COLUMN vitamin_b1_max NUMERIC(10, 3),
    ADD COLUMN vitamin_b2_min NUMERIC(10, 3),
    ADD COLUMN vitamin_b2_max NUMERIC(10, 3),
    ADD COLUMN vitamin_pp_min NUMERIC(10, 3),
    ADD COLUMN vitamin_pp_max NUMERIC(10, 3),
    ADD COLUMN vitamin_c_min  NUMERIC(10, 3),
    ADD COLUMN vitamin_c_max  NUMERIC(10, 3),
    ADD CONSTRAINT ck_age_group_meal_configs_micronutrient_ranges CHECK (
        (calcium_min IS NULL OR calcium_min >= 0) AND (calcium_max IS NULL OR calcium_max >= 0)
        AND (phosphorus_min IS NULL OR phosphorus_min >= 0) AND (phosphorus_max IS NULL OR phosphorus_max >= 0)
        AND (iron_min IS NULL OR iron_min >= 0) AND (iron_max IS NULL OR iron_max >= 0)
        AND (vitamin_a_min IS NULL OR vitamin_a_min >= 0) AND (vitamin_a_max IS NULL OR vitamin_a_max >= 0)
        AND (vitamin_b1_min IS NULL OR vitamin_b1_min >= 0) AND (vitamin_b1_max IS NULL OR vitamin_b1_max >= 0)
        AND (vitamin_b2_min IS NULL OR vitamin_b2_min >= 0) AND (vitamin_b2_max IS NULL OR vitamin_b2_max >= 0)
        AND (vitamin_pp_min IS NULL OR vitamin_pp_min >= 0) AND (vitamin_pp_max IS NULL OR vitamin_pp_max >= 0)
        AND (vitamin_c_min IS NULL OR vitamin_c_min >= 0) AND (vitamin_c_max IS NULL OR vitamin_c_max >= 0)
        AND (calcium_min IS NULL OR calcium_max IS NULL OR calcium_min <= calcium_max)
        AND (phosphorus_min IS NULL OR phosphorus_max IS NULL OR phosphorus_min <= phosphorus_max)
        AND (iron_min IS NULL OR iron_max IS NULL OR iron_min <= iron_max)
        AND (vitamin_a_min IS NULL OR vitamin_a_max IS NULL OR vitamin_a_min <= vitamin_a_max)
        AND (vitamin_b1_min IS NULL OR vitamin_b1_max IS NULL OR vitamin_b1_min <= vitamin_b1_max)
        AND (vitamin_b2_min IS NULL OR vitamin_b2_max IS NULL OR vitamin_b2_min <= vitamin_b2_max)
        AND (vitamin_pp_min IS NULL OR vitamin_pp_max IS NULL OR vitamin_pp_min <= vitamin_pp_max)
        AND (vitamin_c_min IS NULL OR vitamin_c_max IS NULL OR vitamin_c_min <= vitamin_c_max));
