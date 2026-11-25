-- CHD-08: seed dev nhập đủ hồ sơ, form sửa bắt buộc tuần thai nên không để NULL (chỉ điền chỗ trống, không ghi đè)
UPDATE child_profiles
SET gestation_weeks = CASE WHEN premature = 'YES' THEN 35 ELSE 39 END
WHERE gestation_weeks IS NULL;
