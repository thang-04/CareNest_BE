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
-- Lớp demo của năm 2026-2027: năm khác có lớp trùng tên (A1, B1) thì không gán nhầm
LEFT JOIN classrooms r ON r.name = v.classroom_name
    AND r.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027')
WHERE NOT EXISTS (
    SELECT 1 FROM staff_assignments a
    WHERE a.user_id = u.id AND a.staff_role = v.staff_role AND a.valid_from = v.valid_from::date
      AND a.campus_id IS NOT DISTINCT FROM c.id AND a.classroom_id IS NOT DISTINCT FROM r.id
);

-- Trẻ demo nhập đủ thông tin (CHD-08): giới tính, hành chính, khai báo sức khỏe, phụ huynh có CCCD.
-- Có 1 trẻ chờ xếp lớp ở CS-A (CHD-02) để demo PHT thấy trẻ chưa có lớp.
INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on)
SELECT v.code, v.full_name, v.date_of_birth::date, v.gender, v.status, c.id, v.enrolled_on::date
FROM (VALUES ('HS26001', 'Trẻ Demo A1-01', '2022-03-15', 'FEMALE', 'ACTIVE', NULL, '2026-09-01'),
             ('HS26002', 'Trẻ Demo A1-02', '2022-07-02', 'MALE', 'ACTIVE', NULL, '2026-09-01'),
             ('HS26003', 'Trẻ Demo B1-01', '2021-11-20', 'FEMALE', 'ACTIVE', NULL, '2026-09-01'),
             ('HS26004', 'Trẻ Demo chờ xếp lớp A', '2023-01-10', 'MALE', 'PENDING_PLACEMENT', 'CS-A', '2026-10-20'))
     AS v (code, full_name, date_of_birth, gender, status, campus_code, enrolled_on)
LEFT JOIN campuses c ON c.code = v.campus_code
WHERE NOT EXISTS (SELECT 1 FROM children ch WHERE ch.full_name = v.full_name)
ON CONFLICT (code) DO NOTHING;

-- DB dev tạo trước V10: trẻ demo đã có mã, trạng thái do V10 sinh, còn thiếu giới tính
UPDATE children ch SET gender = v.gender
FROM (VALUES ('Trẻ Demo A1-01', 'FEMALE'), ('Trẻ Demo A1-02', 'MALE'), ('Trẻ Demo B1-01', 'FEMALE')) AS v (full_name, gender)
WHERE ch.full_name = v.full_name AND ch.gender IS NULL;

INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT left(code, 4), max(substring(code FROM 5)::integer) FROM children GROUP BY left(code, 4)
ON CONFLICT (code_prefix) DO UPDATE SET last_number = GREATEST(child_code_sequences.last_number, EXCLUDED.last_number);

INSERT INTO enrollments (child_id, classroom_id, valid_from)
SELECT ch.id, r.id, '2026-09-01'::date
FROM (VALUES ('Trẻ Demo A1-01', 'Lớp A1'),
             ('Trẻ Demo A1-02', 'Lớp A1'),
             ('Trẻ Demo B1-01', 'Lớp B1')) AS v (child_name, classroom_name)
JOIN children ch ON ch.full_name = v.child_name
-- Chỉ lớp năm 2026-2027 và trẻ chưa có lớp đang học (V63: mỗi trẻ một lớp đang học)
JOIN classrooms r ON r.name = v.classroom_name
    AND r.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027')
WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.classroom_id = r.id)
  AND NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.valid_to IS NULL);

INSERT INTO guardian_child_links (user_id, child_id, relationship)
SELECT u.id, ch.id, v.relationship
FROM (VALUES ('parent1', 'Trẻ Demo A1-01', 'MOTHER'),
             ('parent2', 'Trẻ Demo B1-01', 'FATHER')) AS v (username, child_name, relationship)
JOIN user_accounts u ON u.username = v.username
JOIN children ch ON ch.full_name = v.child_name
ON CONFLICT (user_id, child_id) DO NOTHING;

-- CCCD giả = "999" + 9 số cuối SĐT demo (không có mã tỉnh 999, như BE giả của FE)
UPDATE user_accounts u SET citizen_id = v.citizen_id
FROM (VALUES ('parent1', '999900000006'), ('parent2', '999900000007')) AS v (username, citizen_id)
WHERE u.username = v.username AND u.citizen_id IS NULL;

-- Phụ huynh trên hồ sơ (AUTH-20): parent1, parent2 đã kích hoạt (có liên kết), người còn lại chờ kích hoạt.
-- parent1, parent2 có tên đăng nhập kiểu cũ (không phải SĐT) nên SĐT trên hồ sơ khác tên đăng nhập.
INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, email, user_id)
SELECT ch.id, v.position, v.full_name, v.relationship, v.phone, v.citizen_id, v.email, u.id
FROM (VALUES ('Trẻ Demo A1-01', 0, 'Phụ huynh A Demo', 'MOTHER', '0900000006', '999900000006', 'parent1@gmail.com', 'parent1'),
             ('Trẻ Demo A1-02', 0, 'Bố Demo A1-02', 'FATHER', '0900000102', '999900000102', NULL, NULL),
             ('Trẻ Demo B1-01', 0, 'Phụ huynh B Demo', 'FATHER', '0900000007', '999900000007', NULL, 'parent2'),
             ('Trẻ Demo B1-01', 1, 'Mẹ Demo B1-01', 'MOTHER', '0900000103', '999900000103', 'me.b1@carenest.test', NULL),
             ('Trẻ Demo chờ xếp lớp A', 0, 'Mẹ Demo chờ xếp lớp', 'MOTHER', '0900000104', '999900000104', NULL, NULL))
     AS v (child_name, position, full_name, relationship, phone, citizen_id, email, username)
JOIN children ch ON ch.full_name = v.child_name
LEFT JOIN user_accounts u ON u.username = v.username
WHERE NOT EXISTS (SELECT 1 FROM child_guardians g WHERE g.child_id = ch.id AND g.position = v.position);

INSERT INTO child_profiles (child_id, premature, gestation_weeks, ethnicity, beneficiary, blood_type,
                            address_province, address_ward, address_street)
SELECT ch.id, v.premature, v.gestation_weeks, 'Kinh', 'Không', v.blood_type, 'Thành phố Hải Phòng', v.ward, v.street
FROM (VALUES ('Trẻ Demo A1-01', 'NO', NULL, NULL, 'Phường Demo A', 'Số 1 đường Demo'),
             ('Trẻ Demo A1-02', 'YES', 35, 'O', 'Phường Demo A', 'Số 2 đường Demo'),
             ('Trẻ Demo B1-01', 'NO', NULL, 'A', 'Phường Demo B', 'Số 3 đường Demo'),
             ('Trẻ Demo chờ xếp lớp A', 'NO', NULL, NULL, 'Phường Demo A', 'Số 4 đường Demo'))
     AS v (child_name, premature, gestation_weeks, blood_type, ward, street)
JOIN children ch ON ch.full_name = v.child_name
ON CONFLICT (child_id) DO NOTHING;

-- Khai báo sức khỏe khi tiếp nhận (CHD-06): đủ các trường hợp Có / Không có / Chưa cung cấp, có 1 trẻ khai tiếp xúc lao
INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, diet_text, other_notes_state,
                                       self_history_state, self_history_text, family_history_state,
                                       tb_contact_state, tb_contact_date, declared_by, declared_at)
SELECT ch.id, v.allergies_state, v.diet_state, v.diet_text, 'NOT_PROVIDED', v.self_state, v.self_text, 'NONE_REPORTED',
       v.tb_state, v.tb_date::date, u.id, (ch.enrolled_on + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM (VALUES ('Trẻ Demo A1-01', 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL),
             ('Trẻ Demo A1-02', 'REPORTED', 'REPORTED', 'Không dùng sữa bò, thay bằng sữa đậu nành', 'NONE_REPORTED', NULL,
              'NONE_REPORTED', NULL),
             ('Trẻ Demo B1-01', 'NOT_PROVIDED', 'NONE_REPORTED', NULL, 'REPORTED', 'Viêm phế quản (demo)', 'REPORTED',
              '2026-08-15'),
             ('Trẻ Demo chờ xếp lớp A', 'NONE_REPORTED', 'NOT_PROVIDED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED',
              NULL))
     AS v (child_name, allergies_state, diet_state, diet_text, self_state, self_text, tb_state, tb_date)
JOIN children ch ON ch.full_name = v.child_name
JOIN user_accounts u ON u.username = 'principal'
ON CONFLICT (child_id) DO NOTHING;

INSERT INTO child_allergies (child_id, name)
SELECT ch.id, v.name
FROM (VALUES ('Trẻ Demo A1-02', 'Tôm'), ('Trẻ Demo A1-02', 'Sữa bò')) AS v (child_name, name)
JOIN children ch ON ch.full_name = v.child_name
JOIN child_health_declarations d ON d.child_id = ch.id
WHERE NOT EXISTS (SELECT 1 FROM child_allergies a WHERE a.child_id = ch.id AND a.name = v.name);

INSERT INTO child_health_declaration_history (child_id, action, user_id, created_at)
SELECT d.child_id, 'DECLARED', d.declared_by, d.declared_at
FROM child_health_declarations d
JOIN children ch ON ch.id = d.child_id
WHERE ch.full_name LIKE 'Trẻ Demo %'
  AND NOT EXISTS (SELECT 1 FROM child_health_declaration_history h WHERE h.child_id = d.child_id);

INSERT INTO child_history (child_id, action, user_id, created_at)
SELECT ch.id, 'ENROLLED', u.id, (ch.enrolled_on + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM children ch
JOIN user_accounts u ON u.username = 'principal'
WHERE ch.full_name LIKE 'Trẻ Demo %'
  AND NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = ch.id);

-- Danh mục bán trú mẫu (NUT-05: nguồn ghi rõ là số liệu demo, không phải số liệu Viện Dinh dưỡng)
INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,
                   kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin)
VALUES ('TP001', 'Gạo tẻ', 'GRAIN', 'g', 'kg', 1000, 18000, 344, 7.9, 1.0, 76.2, 'DEV_SAMPLE', 0, 'DRY', 'PLANT'),
       ('TP002', 'Thịt lợn nạc', 'MEAT', 'g', 'kg', 1000, 140000, 139, 19.0, 7.0, 0, 'DEV_SAMPLE', 5, 'FRESH', 'ANIMAL'),
       ('TP003', 'Cà rốt', 'VEGETABLE', 'g', 'kg', 1000, 20000, 39, 1.5, 0, 7.8, 'DEV_SAMPLE', 15, 'FRESH', 'PLANT'),
       ('TP004', 'Sữa bò tươi', 'EGG_DAIRY', 'ml', 'hộp 180 ml', 180, 8000, 74, 3.9, 4.4, 4.8, 'DEV_SAMPLE', 0, 'DRY', 'ANIMAL')
ON CONFLICT (code) DO NOTHING;

UPDATE foods SET allergen_codes = 'COW_MILK' WHERE code = 'TP004' AND allergen_codes = '';

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

UPDATE foods SET allergen_codes = 'EGG' WHERE code = 'TP008' AND allergen_codes = '';

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

-- Định mức demo theo nhóm tuổi cho các bữa ở trường (số minh họa, trường thay bằng chuẩn của Bộ — NUT-17, NUT-24,
-- NUT-25): kcal, cơ cấu P:L:G %, gam/trẻ/ngày (giữa khoảng kcal × % ÷ 4 hoặc 9, ĐV ≈ một nửa), nhu cầu năng lượng cả
-- ngày và % của nó cho mỗi bữa
INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,
                                    protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max,
                                    glucid_pct_min, glucid_pct_max,
                                    animal_protein_g_min, animal_protein_g_max, plant_protein_g_min, plant_protein_g_max,
                                    animal_lipid_g_min, animal_lipid_g_max, plant_lipid_g_min, plant_lipid_g_max,
                                    glucid_g_min, glucid_g_max,
                                    morning_kcal_pct_min, morning_kcal_pct_max, lunch_kcal_pct_min, lunch_kcal_pct_max,
                                    afternoon_kcal_pct_min, afternoon_kcal_pct_max, daily_kcal_need, norm_basis)
SELECT ag.id, '2026-09-01'::date, v.price, v.kcal_min, v.kcal_max, 13, 20, 25, 35, 47, 60,
       v.protein_min, v.protein_max, v.protein_min, v.protein_max, v.lipid_min, v.lipid_max, v.lipid_min, v.lipid_max,
       v.glucid_min, v.glucid_max, 5, 15, 30, 40, 15, 25, v.daily_need, 'Số liệu demo'
FROM (VALUES ('ag-2', 25000, 600, 800, 11, 18, 10, 14, 82, 105, 1000),
             ('ag-3', 30000, 500, 750, 10, 16, 9, 12, 73, 94, 1300),
             ('ag-4', 30000, 500, 750, 10, 16, 9, 12, 73, 94, 1300),
             ('ag-5', 30000, 500, 750, 10, 16, 9, 12, 73, 94, 1300))
         AS v (code, price, kcal_min, kcal_max, protein_min, protein_max, lipid_min, lipid_max, glucid_min, glucid_max,
               daily_need)
JOIN age_groups ag ON ag.code = v.code
ON CONFLICT (age_group_id, effective_from) DO NOTHING;

-- NUT-26: vi chất mẫu của thực phẩm demo, trên 100 g/ml ăn được (mg; vitamin A µg retinol equivalent).
-- Số liệu do AI ước lượng theo Bảng thành phần thực phẩm Việt Nam (Viện Dinh dưỡng), CHỈ để demo — cần người có chuyên môn soát lại.
-- Chỉ điền khi thực phẩm chưa có vi chất nào, không đè số liệu PHT đã nhập
UPDATE foods f
SET calcium = v.calcium, phosphorus = v.phosphorus, iron = v.iron, vitamin_a = v.vitamin_a,
    vitamin_b1 = v.vitamin_b1, vitamin_b2 = v.vitamin_b2, vitamin_pp = v.vitamin_pp, vitamin_c = v.vitamin_c
FROM (VALUES ('Gạo tẻ', 30, 104, 1.3, 0, 0.10, 0.03, 1.6, 0),
             ('Thịt lợn nạc', 7, 190, 0.96, 2, 0.90, 0.18, 4.4, 1),
             ('Cà rốt', 43, 39, 0.8, 840, 0.06, 0.02, 0.4, 8),
             ('Sữa bò tươi', 120, 95, 0.1, 50, 0.05, 0.19, 0.1, 1),
             ('Dầu đậu nành', 0, 0, 0, 0, 0, 0, 0, 0),
             ('Bí xanh', 26, 23, 0.3, 0, 0.01, 0.02, 0.3, 16),
             ('Đường kính', 0, 0, 0, 0, 0, 0, 0, 0),
             ('Trứng gà', 55, 210, 2.7, 700, 0.16, 0.31, 0.2, 0))
         AS v (name, calcium, phosphorus, iron, vitamin_a, vitamin_b1, vitamin_b2, vitamin_pp, vitamin_c)
WHERE f.name = v.name
  AND num_nonnulls(f.calcium, f.phosphorus, f.iron, f.vitamin_a, f.vitamin_b1, f.vitamin_b2, f.vitamin_pp, f.vitamin_c) = 0;

-- NUT-26: định mức vi chất demo mỗi trẻ mỗi ngày cho các bữa ở trường (khoảng 60–70% nhu cầu khuyến nghị của Bộ Y tế).
-- Nhà trẻ (ag-2) theo nhóm 1–3 tuổi, mẫu giáo (ag-3 đến ag-5) theo nhóm 3–6 tuổi. Số minh họa do AI ước lượng, cần soát lại;
-- chỉ điền cho cấu hình demo chưa có định mức vi chất
UPDATE age_group_meal_configs c
SET calcium_min = v.calcium_min, calcium_max = v.calcium_max,
    phosphorus_min = v.phosphorus_min, phosphorus_max = v.phosphorus_max,
    iron_min = v.iron_min, iron_max = v.iron_max,
    vitamin_a_min = v.vitamin_a_min, vitamin_a_max = v.vitamin_a_max,
    vitamin_b1_min = v.vitamin_b1_min, vitamin_b1_max = v.vitamin_b1_max,
    vitamin_b2_min = v.vitamin_b2_min, vitamin_b2_max = v.vitamin_b2_max,
    vitamin_pp_min = v.vitamin_pp_min, vitamin_pp_max = v.vitamin_pp_max,
    vitamin_c_min = v.vitamin_c_min, vitamin_c_max = v.vitamin_c_max
FROM (VALUES ('ag-2', 300, 500, 250, 400, 5, 8, 250, 400, 0.3, 0.6, 0.3, 0.6, 3, 6, 20, 40),
             ('ag-3', 350, 600, 300, 500, 6, 10, 270, 450, 0.4, 0.7, 0.4, 0.7, 4, 8, 25, 45),
             ('ag-4', 350, 600, 300, 500, 6, 10, 270, 450, 0.4, 0.7, 0.4, 0.7, 4, 8, 25, 45),
             ('ag-5', 350, 600, 300, 500, 6, 10, 270, 450, 0.4, 0.7, 0.4, 0.7, 4, 8, 25, 45))
         AS v (code, calcium_min, calcium_max, phosphorus_min, phosphorus_max, iron_min, iron_max,
               vitamin_a_min, vitamin_a_max, vitamin_b1_min, vitamin_b1_max, vitamin_b2_min, vitamin_b2_max,
               vitamin_pp_min, vitamin_pp_max, vitamin_c_min, vitamin_c_max),
     age_groups ag
WHERE ag.code = v.code
  AND c.age_group_id = ag.id
  AND c.effective_from = DATE '2026-09-01'
  AND c.norm_basis = 'Số liệu demo'
  AND num_nonnulls(c.calcium_min, c.calcium_max, c.phosphorus_min, c.phosphorus_max, c.iron_min, c.iron_max,
                   c.vitamin_a_min, c.vitamin_a_max, c.vitamin_b1_min, c.vitamin_b1_max, c.vitamin_b2_min,
                   c.vitamin_b2_max, c.vitamin_pp_min, c.vitamin_pp_max, c.vitamin_c_min, c.vitamin_c_max) = 0;

-- Người nhận tài khoản (AUTH-18): mỗi trẻ có phụ huynh đúng một người nhận, như tiếp nhận/nhập Excel. Ưu tiên người
-- đã liên kết tài khoản (như V81), không ai thì người đầu trên hồ sơ. DB mới: seed chạy sau V81 nên V81 không chọn được.
-- Chỉ đụng trẻ chưa có người nhận nên chạy lại không đổi gì.
UPDATE child_guardians g SET account_recipient = TRUE
FROM (SELECT DISTINCT ON (x.child_id) x.id
      FROM child_guardians x
      WHERE NOT EXISTS (SELECT 1 FROM child_guardians r WHERE r.child_id = x.child_id AND r.account_recipient)
      ORDER BY x.child_id,
               EXISTS (SELECT 1 FROM guardian_child_links l
                       WHERE l.child_id = x.child_id AND l.user_id = x.user_id) DESC,
               x.position) c
WHERE g.id = c.id;
