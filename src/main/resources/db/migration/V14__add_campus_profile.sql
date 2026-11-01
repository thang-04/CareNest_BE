-- Hồ sơ điểm trường cho màn Cấu hình trường › Điểm trường: tên ngắn, địa chỉ, điện thoại, điểm trường chính.
ALTER TABLE campuses ADD COLUMN short_name VARCHAR(50);
ALTER TABLE campuses ADD COLUMN address VARCHAR(255);
ALTER TABLE campuses ADD COLUMN phone VARCHAR(20);
ALTER TABLE campuses ADD COLUMN is_main BOOLEAN NOT NULL DEFAULT FALSE;

-- Dữ liệu cũ chưa có tên ngắn/địa chỉ: tạm lấy tên, địa chỉ rỗng để Hiệu trưởng bổ sung
UPDATE campuses SET short_name = LEFT(name, 50) WHERE short_name IS NULL;
UPDATE campuses SET address = '' WHERE address IS NULL;
ALTER TABLE campuses ALTER COLUMN short_name SET NOT NULL;
ALTER TABLE campuses ALTER COLUMN address SET NOT NULL;
-- Giữ tương thích câu INSERT cũ (seed, test) chỉ có code + name
ALTER TABLE campuses ALTER COLUMN short_name SET DEFAULT '';
ALTER TABLE campuses ALTER COLUMN address SET DEFAULT '';

-- Trường có đúng một điểm trường chính: điểm trường tạo sớm nhất làm điểm chính
UPDATE campuses SET is_main = TRUE WHERE id = (SELECT MIN(id) FROM campuses);

CREATE UNIQUE INDEX uk_campuses_main ON campuses (is_main) WHERE is_main;
CREATE UNIQUE INDEX uk_campuses_name ON campuses (LOWER(name));
