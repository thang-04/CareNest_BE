-- Dữ liệu dev: danh sách nhóm tuổi của từng năm học (V148, Cần chốt 22/10/2026). Năm đã kết thúc theo bản chụp lúc
-- đóng năm, năm còn mở dùng cả danh mục; chạy lại không nhân đôi.
INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months,
                                           age_to_months, meal_sessions, max_class_size, nutrition_note)
SELECT s.school_year_id, s.age_group_id, s.name, s.short_name, s.age_from_months, s.age_to_months,
       s.meal_sessions, s.max_class_size, s.nutrition_note
FROM school_year_age_groups s
JOIN school_years y ON y.id = s.school_year_id AND y.status = 'CLOSED'
JOIN age_groups g ON g.id = s.age_group_id
ON CONFLICT DO NOTHING;

INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months,
                                           age_to_months, meal_sessions, max_class_size, nutrition_note)
SELECT y.id, g.id, g.name, g.short_name, g.age_from_months, g.age_to_months, g.meal_sessions,
       g.max_class_size, g.nutrition_note
FROM school_years y
CROSS JOIN age_groups g
WHERE y.status <> 'CLOSED'
ON CONFLICT DO NOTHING;
