-- NUT-25: nhu cầu năng lượng cả ngày (kcal/trẻ/ngày) làm mẫu số cho tỷ lệ năng lượng từng bữa; null = chưa đặt
ALTER TABLE age_group_meal_configs
    ADD COLUMN daily_kcal_need NUMERIC(8, 2),
    ADD CONSTRAINT ck_age_group_meal_configs_daily_kcal CHECK (daily_kcal_need IS NULL OR daily_kcal_need > 0);
