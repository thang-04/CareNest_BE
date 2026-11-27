-- NUT-25/NUT-33: nhu cầu năng lượng cả ngày thành khoảng tối thiểu–tối đa; năng lượng khuyến nghị (kcal_min/kcal_max)
-- do hệ thống tính từ khoảng này, khoảng đạm/béo/đường là giá trị cấu hình nạp theo Thông tư 28/2016/TT-BGDĐT.
ALTER TABLE age_group_meal_configs RENAME COLUMN daily_kcal_need TO daily_kcal_max;

ALTER TABLE age_group_meal_configs
    ADD COLUMN daily_kcal_min NUMERIC(8, 2);

-- Dữ liệu cũ chỉ có một số: dùng làm cả hai đầu, trường vào sửa lại khoảng thật
UPDATE age_group_meal_configs SET daily_kcal_min = daily_kcal_max;

ALTER TABLE age_group_meal_configs
    ADD CONSTRAINT ck_age_group_meal_configs_daily_kcal_min CHECK (daily_kcal_min IS NULL OR daily_kcal_min > 0),
    ADD CONSTRAINT ck_age_group_meal_configs_daily_kcal_range CHECK (
        daily_kcal_min IS NULL OR daily_kcal_max IS NULL OR daily_kcal_min <= daily_kcal_max);

-- Nhà trẻ (từ dưới 36 tháng): đạm 13–20, béo 30–40, đường 47–50; mẫu giáo: đạm 13–20, béo 25–35, đường 52–60
UPDATE age_group_meal_configs c
SET protein_pct_min = 13,
    protein_pct_max = 20,
    lipid_pct_min   = CASE WHEN g.age_from_months < 36 THEN 30 ELSE 25 END,
    lipid_pct_max   = CASE WHEN g.age_from_months < 36 THEN 40 ELSE 35 END,
    glucid_pct_min  = CASE WHEN g.age_from_months < 36 THEN 47 ELSE 52 END,
    glucid_pct_max  = CASE WHEN g.age_from_months < 36 THEN 50 ELSE 60 END
FROM age_groups g
WHERE g.id = c.age_group_id AND g.age_from_months IS NOT NULL;

-- Năng lượng khuyến nghị tại trường: nhà trẻ 60–70% nhu cầu cả ngày, mẫu giáo 50–55%
UPDATE age_group_meal_configs c
SET kcal_min = ROUND(c.daily_kcal_min * CASE WHEN g.age_from_months < 36 THEN 0.60 ELSE 0.50 END, 2),
    kcal_max = ROUND(c.daily_kcal_max * CASE WHEN g.age_from_months < 36 THEN 0.70 ELSE 0.55 END, 2)
FROM age_groups g
WHERE g.id = c.age_group_id AND g.age_from_months IS NOT NULL
  AND c.daily_kcal_min IS NOT NULL AND c.daily_kcal_max IS NOT NULL;
