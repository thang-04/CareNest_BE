-- DỮ LIỆU GIẢ CHỈ CHO DEV/DEMO: chạy khi bật profile "dev" (application-dev.yml). Không chứa dữ liệu thật.
-- keycloak_user_id khớp id user trong docker/keycloak/dev/carenest-users-0.json.
-- Idempotent theo khóa tự nhiên để Flyway chạy lại an toàn khi file đổi checksum.

INSERT INTO user_accounts (keycloak_user_id, username, email, full_name) VALUES
    ('00000000-0000-4000-a000-000000000001', 'admin', 'admin@gmail.com', 'Quản trị Demo'),
    ('00000000-0000-4000-a000-000000000002', 'principal', 'principal@gmail.com', 'Hiệu trưởng Demo'),
    ('00000000-0000-4000-a000-000000000003', 'viceprincipal', 'viceprincipal@gmail.com', 'Hiệu phó Cơ sở A Demo'),
    ('00000000-0000-4000-a000-000000000004', 'teacher1', 'teacher1@gmail.com', 'Giáo viên A Demo'),
    ('00000000-0000-4000-a000-000000000005', 'teacher2', 'teacher2@gmail.com', 'Giáo viên B Demo'),
    ('00000000-0000-4000-a000-000000000006', 'parent1', 'parent1@gmail.com', 'Phụ huynh A Demo'),
    ('00000000-0000-4000-a000-000000000007', 'parent2', 'parent2@gmail.com', 'Phụ huynh B Demo'),
    ('00000000-0000-4000-a000-000000000008', 'kitchen', 'kitchen@gmail.com', 'Nhà bếp Cơ sở A Demo')
-- Cập nhật username/email để DB dev cũ (user *.dev, *@carenest.test) khớp tên mới; các join theo username bên dưới cần điều này
ON CONFLICT (keycloak_user_id) DO UPDATE SET username = EXCLUDED.username, email = EXCLUDED.email;

-- primary_role khớp realm role trong docker/keycloak/dev/carenest-users-0.json (V8)
UPDATE user_accounts u SET primary_role = v.role
FROM (VALUES ('admin', 'ADMIN'), ('principal', 'PRINCIPAL'), ('viceprincipal', 'VICE_PRINCIPAL'),
             ('teacher1', 'TEACHER'), ('teacher2', 'TEACHER'), ('parent1', 'PARENT'), ('parent2', 'PARENT'),
             ('kitchen', 'KITCHEN_STAFF')) AS v (username, role)
WHERE u.username = v.username AND u.primary_role IS NULL;

INSERT INTO campuses (code, name, short_name, address) VALUES
    ('CS-A', 'Cơ sở A (demo)', 'Cơ sở A', 'Địa chỉ demo A'),
    ('CS-B', 'Cơ sở B (demo)', 'Cơ sở B', 'Địa chỉ demo B')
ON CONFLICT (code) DO NOTHING;

-- Điểm trường chính (V14): CS-A, trừ khi đã có điểm chính
UPDATE campuses SET is_main = TRUE
WHERE code = 'CS-A' AND NOT EXISTS (SELECT 1 FROM campuses WHERE is_main);

INSERT INTO school_years (name, start_date, end_date) VALUES ('2026-2027', '2026-09-01', '2027-05-31')
ON CONFLICT (name) DO NOTHING;

-- Năm demo là năm đang hoạt động (V13), trừ khi đã có năm ACTIVE khác
UPDATE school_years SET status = 'ACTIVE', activated_at = now()
WHERE name = '2026-2027' AND status = 'PLANNED'
  AND NOT EXISTS (SELECT 1 FROM school_years WHERE status = 'ACTIVE');

INSERT INTO classrooms (campus_id, school_year_id, name)
SELECT c.id, y.id, v.name
FROM (VALUES ('CS-A', 'Lớp A1'), ('CS-B', 'Lớp B1')) AS v (campus_code, name)
JOIN campuses c ON c.code = v.campus_code
JOIN school_years y ON y.name = '2026-2027'
ON CONFLICT (school_year_id, campus_id, name) DO NOTHING;

-- Lớp phải có nhóm tuổi (bữa ăn — NUT-17, chuỗi ký hồ sơ lớp) và phòng cố định (SCH-03); thiếu thì không có suất ăn.
-- Nhóm tuổi theo năm sinh trẻ demo: 2022 ⇒ Mẫu giáo nhỡ (ag-4), 2021 ⇒ Mẫu giáo lớn (ag-5).
INSERT INTO rooms (campus_id, code, name, room_type, capacity)
SELECT c.id, v.code, v.name, 'CLASS', v.capacity
FROM (VALUES ('CS-A', 'DEMO-A1', 'Phòng demo A1', 30),
             ('CS-B', 'DEMO-B1', 'Phòng demo B1', 35)) AS v (campus_code, code, name, capacity)
JOIN campuses c ON c.code = v.campus_code
WHERE NOT EXISTS (
    SELECT 1 FROM rooms r
    WHERE r.campus_id = c.id AND r.status <> 'DELETED'
      AND (LOWER(r.code) = LOWER(v.code) OR LOWER(r.name) = LOWER(v.name))
);

-- UPDATE thay vì dựa vào INSERT ở trên: DB dev cũ (cả VPS) đã có A1/B1 không nhóm tuổi/phòng, ON CONFLICT bỏ qua.
-- Chỉ điền ô đang trống; UI cấm đổi nhóm tuổi khi lớp đã có trẻ nên seed là cách vá duy nhất.
UPDATE classrooms cl
SET age_group_id = COALESCE(cl.age_group_id, g.id),
    room_id = COALESCE(cl.room_id, r.id),
    capacity = COALESCE(cl.capacity, g.max_class_size)
FROM (VALUES ('CS-A', 'Lớp A1', 'ag-4', 'DEMO-A1'),
             ('CS-B', 'Lớp B1', 'ag-5', 'DEMO-B1')) AS v (campus_code, class_name, age_group_code, room_code)
JOIN campuses c ON c.code = v.campus_code
JOIN school_years y ON y.name = '2026-2027'
JOIN age_groups g ON g.code = v.age_group_code
LEFT JOIN rooms r ON r.campus_id = c.id AND LOWER(r.code) = LOWER(v.room_code) AND r.status <> 'DELETED'
    AND NOT EXISTS (SELECT 1 FROM classrooms o WHERE o.school_year_id = y.id AND o.room_id = r.id)
WHERE cl.campus_id = c.id AND cl.school_year_id = y.id AND cl.name = v.class_name
  AND (cl.age_group_id IS NULL OR cl.room_id IS NULL OR cl.capacity IS NULL);

-- Tổ trưởng mỗi nhóm tuổi có lớp demo (AUTH-23), để hồ sơ lớp đi hết chuỗi ký; không chèn nếu nhóm tuổi đã có tổ trưởng năm này
INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from)
SELECT u.id, 'AGE_GROUP_TEAM_LEADER', g.id, y.id, '2026-09-01'::date
FROM (VALUES ('teacher1', 'ag-4'), ('teacher2', 'ag-5')) AS v (username, age_group_code)
JOIN user_accounts u ON u.username = v.username
JOIN age_groups g ON g.code = v.age_group_code
JOIN school_years y ON y.name = '2026-2027'
WHERE NOT EXISTS (
    SELECT 1 FROM staff_assignments a
    WHERE a.staff_role = 'AGE_GROUP_TEAM_LEADER' AND a.age_group_id = g.id AND a.school_year_id = y.id
);

-- Phân công: teacher2 dạy A1 tới 30/09 rồi chuyển sang B1 ⇒ demo phân công hết hạn không còn quyền ở A1
INSERT INTO staff_assignments (user_id, staff_role, campus_id, classroom_id, valid_from, valid_to)
SELECT u.id, v.staff_role, c.id, r.id, v.valid_from::date, v.valid_to::date
FROM (VALUES
          ('principal', 'PRINCIPAL', NULL, NULL, '2026-09-01', NULL),
          ('viceprincipal', 'VICE_PRINCIPAL', 'CS-A', NULL, '2026-09-01', NULL),
          ('kitchen', 'KITCHEN_STAFF', 'CS-A', NULL, '2026-09-01', NULL),
          ('teacher1', 'TEACHER', NULL, 'Lớp A1', '2026-09-01', NULL),
          ('teacher2', 'TEACHER', NULL, 'Lớp A1', '2026-09-01', '2026-09-30'),
          ('teacher2', 'TEACHER', NULL, 'Lớp B1', '2026-10-01', NULL)
     ) AS v (username, staff_role, campus_code, classroom_name, valid_from, valid_to)
JOIN user_accounts u ON u.username = v.username
LEFT JOIN campuses c ON c.code = v.campus_code
LEFT JOIN classrooms r ON r.name = v.classroom_name
WHERE NOT EXISTS (
    SELECT 1 FROM staff_assignments a
    WHERE a.user_id = u.id AND a.staff_role = v.staff_role AND a.valid_from = v.valid_from::date
      AND a.campus_id IS NOT DISTINCT FROM c.id AND a.classroom_id IS NOT DISTINCT FROM r.id
);

INSERT INTO children (full_name, date_of_birth)
SELECT v.full_name, v.date_of_birth::date
FROM (VALUES ('Trẻ Demo A1-01', '2022-03-15'),
             ('Trẻ Demo A1-02', '2022-07-02'),
             ('Trẻ Demo B1-01', '2021-11-20')) AS v (full_name, date_of_birth)
WHERE NOT EXISTS (SELECT 1 FROM children ch WHERE ch.full_name = v.full_name);

INSERT INTO enrollments (child_id, classroom_id, valid_from)
SELECT ch.id, r.id, '2026-09-01'::date
FROM (VALUES ('Trẻ Demo A1-01', 'Lớp A1'),
             ('Trẻ Demo A1-02', 'Lớp A1'),
             ('Trẻ Demo B1-01', 'Lớp B1')) AS v (child_name, classroom_name)
JOIN children ch ON ch.full_name = v.child_name
JOIN classrooms r ON r.name = v.classroom_name
WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.classroom_id = r.id);

INSERT INTO guardian_child_links (user_id, child_id, relationship)
SELECT u.id, ch.id, v.relationship
FROM (VALUES ('parent1', 'Trẻ Demo A1-01', 'MOTHER'),
             ('parent2', 'Trẻ Demo B1-01', 'FATHER')) AS v (username, child_name, relationship)
JOIN user_accounts u ON u.username = v.username
JOIN children ch ON ch.full_name = v.child_name
ON CONFLICT (user_id, child_id) DO NOTHING;

-- Danh mục bán trú mẫu (NUT-05: nguồn ghi rõ là số liệu demo, không phải số liệu Viện Dinh dưỡng)
INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,
                   kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin)
VALUES ('TP001', 'Gạo tẻ', 'GRAIN', 'g', 'kg', 1000, 18000, 344, 7.9, 1.0, 76.2, 'DEV_SAMPLE', 0, 'DRY', 'PLANT'),
       ('TP002', 'Thịt lợn nạc', 'MEAT', 'g', 'kg', 1000, 140000, 139, 19.0, 7.0, 0, 'DEV_SAMPLE', 5, 'FRESH', 'ANIMAL'),
       ('TP003', 'Cà rốt', 'VEGETABLE', 'g', 'kg', 1000, 20000, 39, 1.5, 0, 7.8, 'DEV_SAMPLE', 15, 'FRESH', 'PLANT'),
       ('TP004', 'Sữa bò tươi', 'EGG_DAIRY', 'ml', 'hộp 180 ml', 180, 8000, 74, 3.9, 4.4, 4.8, 'DEV_SAMPLE', 0, 'DRY', 'ANIMAL')
ON CONFLICT (code) DO NOTHING;

INSERT INTO food_allergens (food_id, allergen_id)
SELECT f.id, a.id FROM foods f JOIN allergens a ON a.code = 'COW_MILK' WHERE f.code = 'TP004'
ON CONFLICT DO NOTHING;

INSERT INTO dishes (code, name, dish_type, description)
VALUES ('M001', 'Cháo thịt bằm cà rốt', 'CARB', 'Món demo')
ON CONFLICT (code) DO NOTHING;

INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child)
SELECT d.id, f.id, ag.id, v.grams
FROM (VALUES ('TP001', 'ag-2', 21), ('TP001', 'ag-5', 33),
             ('TP002', 'ag-2', 14), ('TP002', 'ag-5', 22),
             ('TP003', 'ag-2', 7), ('TP003', 'ag-5', 11)) AS v (food_code, age_group_code, grams)
JOIN dishes d ON d.code = 'M001'
JOIN foods f ON f.code = v.food_code
JOIN age_groups ag ON ag.code = v.age_group_code
ON CONFLICT (dish_id, food_id, age_group_id) DO NOTHING;

-- Lập thực đơn tuần demo (L3): thêm thực phẩm, món đủ định lượng cho mẫu giáo nhỡ/lớn (ag-4, ag-5 có lớp demo)
INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,
                   kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin)
VALUES ('TP005', 'Dầu đậu nành', 'FAT_SUGAR', 'g', 'chai 1 kg', 1000, 45000, 900, 0, 100, 0, 'DEV_SAMPLE', 0, 'DRY', 'PLANT'),
       ('TP006', 'Bí xanh', 'VEGETABLE', 'g', 'kg', 1000, 15000, 14, 0.6, 0, 2.4, 'DEV_SAMPLE', 10, 'FRESH', 'PLANT'),
       ('TP007', 'Đường kính', 'FAT_SUGAR', 'g', 'kg', 1000, 25000, 397, 0, 0, 99.3, 'DEV_SAMPLE', 0, 'DRY', 'PLANT'),
       ('TP008', 'Trứng gà', 'EGG_DAIRY', 'g', 'kg', 1000, 60000, 166, 14.8, 11.6, 0.5, 'DEV_SAMPLE', 12, 'FRESH', 'ANIMAL')
ON CONFLICT (code) DO NOTHING;

INSERT INTO food_allergens (food_id, allergen_id)
SELECT f.id, a.id FROM foods f JOIN allergens a ON a.code = 'EGG' WHERE f.code = 'TP008'
ON CONFLICT DO NOTHING;

INSERT INTO dishes (code, name, dish_type, description)
VALUES ('M002', 'Cơm trắng', 'CARB', 'Món demo'),
       ('M003', 'Thịt lợn rim', 'MAIN', 'Món demo'),
       ('M004', 'Canh bí xanh', 'SOUP', 'Món demo'),
       ('M005', 'Sữa tươi', 'SNACK', 'Món demo'),
       ('M006', 'Trứng đúc thịt', 'MAIN', 'Món demo')
ON CONFLICT (code) DO NOTHING;

INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child)
SELECT d.id, f.id, ag.id, v.grams
FROM (VALUES ('M001', 'TP001', 'ag-4', 28), ('M001', 'TP002', 'ag-4', 18), ('M001', 'TP003', 'ag-4', 9),
             ('M002', 'TP001', 'ag-4', 55), ('M002', 'TP001', 'ag-5', 65),
             ('M003', 'TP002', 'ag-4', 35), ('M003', 'TP002', 'ag-5', 40),
             ('M003', 'TP005', 'ag-4', 4), ('M003', 'TP005', 'ag-5', 5),
             ('M003', 'TP007', 'ag-4', 2), ('M003', 'TP007', 'ag-5', 2),
             ('M004', 'TP006', 'ag-4', 40), ('M004', 'TP006', 'ag-5', 50),
             ('M004', 'TP005', 'ag-4', 2), ('M004', 'TP005', 'ag-5', 2),
             ('M005', 'TP004', 'ag-4', 180), ('M005', 'TP004', 'ag-5', 180),
             ('M006', 'TP008', 'ag-4', 30), ('M006', 'TP008', 'ag-5', 35),
             ('M006', 'TP002', 'ag-4', 10), ('M006', 'TP002', 'ag-5', 12),
             ('M006', 'TP005', 'ag-4', 3), ('M006', 'TP005', 'ag-5', 3))
         AS v (dish_code, food_code, age_group_code, grams)
JOIN dishes d ON d.code = v.dish_code
JOIN foods f ON f.code = v.food_code
JOIN age_groups ag ON ag.code = v.age_group_code
ON CONFLICT (dish_id, food_id, age_group_id) DO NOTHING;

-- Định mức demo theo nhóm tuổi cho các bữa ở trường (số minh họa, trường thay bằng chuẩn của Bộ — NUT-17, NUT-24)
INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,
                                    protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max,
                                    glucid_pct_min, glucid_pct_max, norm_basis)
SELECT ag.id, '2026-09-01'::date, v.price, v.kcal_min, v.kcal_max, 13, 20, 25, 35, 47, 60, 'Số liệu demo'
FROM (VALUES ('ag-2', 25000, 600, 800), ('ag-3', 30000, 500, 750),
             ('ag-4', 30000, 500, 750), ('ag-5', 30000, 500, 750)) AS v (code, price, kcal_min, kcal_max)
JOIN age_groups ag ON ag.code = v.code
ON CONFLICT (age_group_id, effective_from) DO NOTHING;
