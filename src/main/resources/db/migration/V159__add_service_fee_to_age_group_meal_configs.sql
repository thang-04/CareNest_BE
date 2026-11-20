-- NUT-19: phí dịch vụ/trẻ/ngày đi cùng phiên bản giá suất; ngân sách thực phẩm = giá suất − phí dịch vụ.
-- Cấu hình cũ mặc định phí 0 (user chốt 20/11/2026) nên ngân sách thực phẩm giữ bằng giá suất cho tới khi HT nhập phí.
ALTER TABLE age_group_meal_configs ADD COLUMN service_fee NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE age_group_meal_configs ADD CONSTRAINT ck_age_group_meal_configs_service_fee
    CHECK (service_fee >= 0 AND service_fee <= meal_price);
