-- Điều 15 Thông tư 52/2020/TT-BGDĐT: sáu nhóm/lớp tuổi chuẩn của giáo dục mầm non.
-- Danh mục chung là nguồn để HT chọn nhóm áp dụng cho năm PLANNED; năm đang hoạt động/đã đóng giữ cấu hình đã chốt.
ALTER TABLE age_groups
    ADD COLUMN standard_group BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN regulation_ref VARCHAR(200) NOT NULL DEFAULT '';

-- Từ V154, tên nhóm tuổi chỉ cần duy nhất trong từng năm học. Bỏ unique toàn cục để
-- chuẩn hóa tên sáu nhóm mà không làm Flyway lỗi nếu dữ liệu cũ đã có tên trùng ở năm khác.
DROP INDEX IF EXISTS uk_age_groups_name;

INSERT INTO age_groups
    (code, name, short_name, sort_order, age_from_months, age_to_months, meal_sessions, max_class_size,
     nutrition_note, standard_group, regulation_ref)
VALUES
    ('ag-0', 'Nhóm trẻ 3–12 tháng',  '3–12 tháng',  1,  3, 13, 'MORNING,LUNCH,AFTERNOON', 15, '', TRUE,
     'Điều 15 Thông tư 52/2020/TT-BGDĐT'),
    ('ag-1', 'Nhóm trẻ 13–24 tháng', '13–24 tháng', 2, 13, 25, 'MORNING,LUNCH,AFTERNOON', 20, '', TRUE,
     'Điều 15 Thông tư 52/2020/TT-BGDĐT')
ON CONFLICT (code) DO NOTHING;

UPDATE age_groups
SET name = v.name,
    short_name = v.short_name,
    sort_order = v.sort_order,
    age_from_months = v.age_from,
    age_to_months = v.age_to,
    max_class_size = v.max_size,
    standard_group = TRUE,
    regulation_ref = 'Điều 15 Thông tư 52/2020/TT-BGDĐT',
    updated_at = now()
FROM (VALUES
    ('ag-0', 'Nhóm trẻ 3–12 tháng',   '3–12 tháng',       1::smallint,  3::smallint, 13::smallint, 15::smallint),
    ('ag-1', 'Nhóm trẻ 13–24 tháng',  '13–24 tháng',      2::smallint, 13::smallint, 25::smallint, 20::smallint),
    ('ag-2', 'Nhóm trẻ 25–36 tháng',  '25–36 tháng',      3::smallint, 25::smallint, 36::smallint, 25::smallint),
    ('ag-3', 'Mẫu giáo 3–4 tuổi',     'Mẫu giáo 3–4',     4::smallint, 36::smallint, 48::smallint, 25::smallint),
    ('ag-4', 'Mẫu giáo 4–5 tuổi',     'Mẫu giáo 4–5',     5::smallint, 48::smallint, 60::smallint, 30::smallint),
    ('ag-5', 'Mẫu giáo 5–6 tuổi',     'Mẫu giáo 5–6',     6::smallint, 60::smallint, 72::smallint, 35::smallint)
) AS v(code, name, short_name, sort_order, age_from, age_to, max_size)
WHERE age_groups.code = v.code;

-- Chỉ chuẩn hóa năm đang chuẩn bị. Không thay cấu trúc giữa năm đang học và không sửa lịch sử năm cũ.
UPDATE school_year_age_group_members m
SET name = g.name,
    short_name = g.short_name,
    age_from_months = g.age_from_months,
    age_to_months = g.age_to_months,
    max_class_size = LEAST(COALESCE(m.max_class_size, g.max_class_size), g.max_class_size)
FROM age_groups g, school_years y
WHERE m.age_group_id = g.id
  AND m.school_year_id = y.id
  AND y.status = 'PLANNED'
  AND g.standard_group = TRUE;

ALTER TABLE age_groups
    ADD CONSTRAINT ck_age_groups_standard_reference
        CHECK (NOT standard_group OR (regulation_ref <> '' AND max_class_size IS NOT NULL));

CREATE INDEX idx_age_groups_standard_order
    ON age_groups (standard_group, sort_order);
