-- Spec 017: tệp mới lưu key dạng <thư mục>/<loại>_<mã>_<yyyyMMdd-HHmmss>_<xxxx>.<đuôi> (dài hơn UUID); chỉ nới độ dài, không đổi dữ liệu.
ALTER TABLE user_accounts ALTER COLUMN photo_key TYPE VARCHAR(255);
ALTER TABLE children ALTER COLUMN photo_key TYPE VARCHAR(255);
ALTER TABLE child_guardians ALTER COLUMN photo_key TYPE VARCHAR(255);
ALTER TABLE user_signatures ALTER COLUMN storage_key TYPE VARCHAR(255);
ALTER TABLE meal_photos ALTER COLUMN storage_key TYPE VARCHAR(255);
ALTER TABLE edu_learning_resources ALTER COLUMN storage_key TYPE VARCHAR(255);
