-- NUT-21, Mẫu 5 QĐ 1246/QĐ-BYT: bếp ghi thêm khối lượng/thể tích, dụng cụ chứa, nhiệt độ bảo quản cho từng món lưu mẫu.
-- Mảng jsonb [{dishId, weight, container, temperature}] thay cho bảng con; dish_ids giữ nguyên, bản ghi cũ là mảng rỗng.
ALTER TABLE food_samples ADD COLUMN dish_details JSONB NOT NULL DEFAULT '[]'::jsonb;
