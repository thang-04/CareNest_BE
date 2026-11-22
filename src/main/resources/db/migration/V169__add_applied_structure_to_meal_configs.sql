-- NUT-28: cơ cấu áp dụng P:L:G (% năng lượng, một con số mỗi chất) của phiên bản định mức; null = chưa chọn.
-- Dùng để quy đổi ra gam đạm/béo/bột đường tại hai mốc kcal của trường; không thay khoảng min–max đang dùng để chấm thực đơn.
ALTER TABLE age_group_meal_configs
    ADD COLUMN applied_protein_pct NUMERIC(5, 2),
    ADD COLUMN applied_lipid_pct   NUMERIC(5, 2),
    ADD COLUMN applied_glucid_pct  NUMERIC(5, 2),
    ADD CONSTRAINT ck_age_group_meal_configs_applied_pct CHECK (
        (applied_protein_pct IS NULL OR (applied_protein_pct BETWEEN 0 AND 100))
        AND (applied_lipid_pct IS NULL OR (applied_lipid_pct BETWEEN 0 AND 100))
        AND (applied_glucid_pct IS NULL OR (applied_glucid_pct BETWEEN 0 AND 100))
    );
