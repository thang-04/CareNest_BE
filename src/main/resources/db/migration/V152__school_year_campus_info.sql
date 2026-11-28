-- Cần chốt 22/10/2026: sửa thông tin điểm trường ở năm nào chỉ năm đó đổi. Mỗi năm giữ mã, tên, tên ngắn, địa chỉ, SĐT
-- riêng trên dòng của năm (V151); campuses giữ thông tin của năm đang hoạt động cho các module khác đọc.
ALTER TABLE school_year_campus_members
    ADD COLUMN code       VARCHAR(30),
    ADD COLUMN name       VARCHAR(150),
    ADD COLUMN short_name VARCHAR(50),
    ADD COLUMN address    VARCHAR(255),
    ADD COLUMN phone      VARCHAR(20);

-- Năm đã kết thúc: theo bản chụp lúc đóng năm
UPDATE school_year_campus_members m
SET code = s.code, name = s.name, short_name = s.short_name, address = s.address, phone = s.phone
FROM school_year_campuses s
WHERE s.school_year_id = m.school_year_id AND s.campus_id = m.campus_id;

-- Năm còn mở (và năm đóng thiếu bản chụp): thông tin hiện tại
UPDATE school_year_campus_members m
SET code = c.code, name = c.name, short_name = c.short_name, address = c.address, phone = c.phone
FROM campuses c
WHERE c.id = m.campus_id AND m.name IS NULL;
