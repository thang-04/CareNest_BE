-- NUT-19: biên % quanh ngân sách thực phẩm để kết luận đạt tiền, cấu hình theo nhóm tuổi và ngày hiệu lực
ALTER TABLE age_group_meal_configs
    ADD COLUMN budget_tolerance_pct NUMERIC(5, 2) NOT NULL DEFAULT 5;

ALTER TABLE age_group_meal_configs
    ADD CONSTRAINT ck_age_group_meal_configs_budget_tolerance
        CHECK (budget_tolerance_pct >= 0 AND budget_tolerance_pct <= 100);
