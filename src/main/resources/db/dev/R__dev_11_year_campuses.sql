-- Dữ liệu dev: danh sách điểm trường của từng năm học kèm thông tin riêng của năm (V151, V152, Cần chốt 22/10/2026).
-- Năm đã kết thúc theo bản chụp lúc đóng năm, năm còn mở dùng cả danh mục; chạy lại không nhân đôi.
INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone)
SELECT s.school_year_id, s.campus_id, s.code, s.name, s.short_name, s.address, s.phone
FROM school_year_campuses s
JOIN school_years y ON y.id = s.school_year_id AND y.status = 'CLOSED'
JOIN campuses c ON c.id = s.campus_id
ON CONFLICT DO NOTHING;

INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone)
SELECT y.id, c.id, c.code, c.name, c.short_name, c.address, c.phone
FROM school_years y
CROSS JOIN campuses c
WHERE y.status <> 'CLOSED'
ON CONFLICT DO NOTHING;
