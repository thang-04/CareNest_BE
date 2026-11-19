-- Dữ liệu dev: danh sách nhóm tuổi của từng năm học (V148, Cần chốt 22/10/2026). Năm đã kết thúc theo bản chụp lúc
-- đóng năm, năm còn mở dùng cả danh mục; chạy lại không nhân đôi.
INSERT INTO school_year_age_group_members (school_year_id, age_group_id)
SELECT s.school_year_id, s.age_group_id
FROM school_year_age_groups s
JOIN school_years y ON y.id = s.school_year_id AND y.status = 'CLOSED'
JOIN age_groups g ON g.id = s.age_group_id
ON CONFLICT DO NOTHING;

INSERT INTO school_year_age_group_members (school_year_id, age_group_id)
SELECT y.id, g.id
FROM school_years y
CROSS JOIN age_groups g
WHERE y.status <> 'CLOSED'
ON CONFLICT DO NOTHING;
