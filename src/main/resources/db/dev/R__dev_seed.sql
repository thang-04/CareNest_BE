-- DỮ LIỆU GIẢ CHỈ CHO DEV/DEMO: chạy khi bật profile "dev" (application-dev.yml). Không chứa dữ liệu thật.
-- keycloak_user_id khớp id user trong docker/keycloak/dev/carenest-users-0.json.
-- Idempotent theo khóa tự nhiên để Flyway chạy lại an toàn khi file đổi checksum.

INSERT INTO user_accounts (keycloak_user_id, username, email, full_name) VALUES
    ('00000000-0000-4000-a000-000000000001', 'admin.dev', 'admin.dev@carenest.test', 'Quản trị Demo'),
    ('00000000-0000-4000-a000-000000000002', 'principal.dev', 'principal.dev@carenest.test', 'Hiệu trưởng Demo'),
    ('00000000-0000-4000-a000-000000000003', 'viceprincipal.a', 'viceprincipal.a@carenest.test', 'Hiệu phó Cơ sở A Demo'),
    ('00000000-0000-4000-a000-000000000004', 'teacher.a', 'teacher.a@carenest.test', 'Giáo viên A Demo'),
    ('00000000-0000-4000-a000-000000000005', 'teacher.b', 'teacher.b@carenest.test', 'Giáo viên B Demo'),
    ('00000000-0000-4000-a000-000000000006', 'parent.a', 'parent.a@carenest.test', 'Phụ huynh A Demo'),
    ('00000000-0000-4000-a000-000000000007', 'parent.b', NULL, 'Phụ huynh B Demo'),
    ('00000000-0000-4000-a000-000000000008', 'kitchen.a', 'kitchen.a@carenest.test', 'Nhà bếp Cơ sở A Demo')
ON CONFLICT (keycloak_user_id) DO NOTHING;

-- primary_role khớp realm role trong docker/keycloak/dev/carenest-users-0.json (V8)
UPDATE user_accounts u SET primary_role = v.role
FROM (VALUES ('admin.dev', 'ADMIN'), ('principal.dev', 'PRINCIPAL'), ('viceprincipal.a', 'VICE_PRINCIPAL'),
             ('teacher.a', 'TEACHER'), ('teacher.b', 'TEACHER'), ('parent.a', 'PARENT'), ('parent.b', 'PARENT'),
             ('kitchen.a', 'KITCHEN_STAFF')) AS v (username, role)
WHERE u.username = v.username AND u.primary_role IS NULL;
INSERT INTO campuses (code, name, short_name, address) VALUES
    ('CS-A', 'Cơ sở A (demo)', 'Cơ sở A', 'Địa chỉ demo A'),
    ('CS-B', 'Cơ sở B (demo)', 'Cơ sở B', 'Địa chỉ demo B')
ON CONFLICT (code) DO NOTHING;

-- Điểm trường chính (V15): CS-A, trừ khi đã có điểm chính
UPDATE campuses SET is_main = TRUE
WHERE code = 'CS-A' AND NOT EXISTS (SELECT 1 FROM campuses WHERE is_main);

INSERT INTO school_years (name, start_date, end_date) VALUES ('2026-2027', '2026-09-01', '2027-05-31')
ON CONFLICT (name) DO NOTHING;

-- Năm demo là năm đang hoạt động (V14), trừ khi đã có năm ACTIVE khác
UPDATE school_years SET status = 'ACTIVE', activated_at = now()
WHERE name = '2026-2027' AND status = 'PLANNED'
  AND NOT EXISTS (SELECT 1 FROM school_years WHERE status = 'ACTIVE');

INSERT INTO classrooms (campus_id, school_year_id, name)
SELECT c.id, y.id, v.name
FROM (VALUES ('CS-A', 'Lớp A1'), ('CS-B', 'Lớp B1')) AS v (campus_code, name)
JOIN campuses c ON c.code = v.campus_code
JOIN school_years y ON y.name = '2026-2027'
ON CONFLICT (school_year_id, campus_id, name) DO NOTHING;

-- Phân công: teacher.b dạy A1 tới 30/09 rồi chuyển sang B1 ⇒ demo phân công hết hạn không còn quyền ở A1
INSERT INTO staff_assignments (user_id, staff_role, campus_id, classroom_id, valid_from, valid_to)
SELECT u.id, v.staff_role, c.id, r.id, v.valid_from::date, v.valid_to::date
FROM (VALUES
          ('principal.dev', 'PRINCIPAL', NULL, NULL, '2026-09-01', NULL),
          ('viceprincipal.a', 'VICE_PRINCIPAL', 'CS-A', NULL, '2026-09-01', NULL),
          ('kitchen.a', 'KITCHEN_STAFF', 'CS-A', NULL, '2026-09-01', NULL),
          ('teacher.a', 'TEACHER', NULL, 'Lớp A1', '2026-09-01', NULL),
          ('teacher.b', 'TEACHER', NULL, 'Lớp A1', '2026-09-01', '2026-09-30'),
          ('teacher.b', 'TEACHER', NULL, 'Lớp B1', '2026-10-01', NULL)
     ) AS v (username, staff_role, campus_code, classroom_name, valid_from, valid_to)
JOIN user_accounts u ON u.username = v.username
LEFT JOIN campuses c ON c.code = v.campus_code
LEFT JOIN classrooms r ON r.name = v.classroom_name
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
JOIN classrooms r ON r.name = v.classroom_name
WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.classroom_id = r.id);

INSERT INTO guardian_child_links (user_id, child_id, relationship)
SELECT u.id, ch.id, v.relationship
FROM (VALUES ('parent.a', 'Trẻ Demo A1-01', 'MOTHER'),
             ('parent.b', 'Trẻ Demo B1-01', 'FATHER')) AS v (username, child_name, relationship)
JOIN user_accounts u ON u.username = v.username
JOIN children ch ON ch.full_name = v.child_name
ON CONFLICT (user_id, child_id) DO NOTHING;

-- CCCD giả = "999" + 9 số cuối SĐT demo (không có mã tỉnh 999, như BE giả của FE)
UPDATE user_accounts u SET citizen_id = v.citizen_id
FROM (VALUES ('parent.a', '999900000006'), ('parent.b', '999900000007')) AS v (username, citizen_id)
WHERE u.username = v.username AND u.citizen_id IS NULL;

-- Phụ huynh trên hồ sơ (AUTH-20): parent.a, parent.b đã kích hoạt (có liên kết), người còn lại chờ kích hoạt.
-- parent.a, parent.b có tên đăng nhập kiểu cũ (không phải SĐT) nên SĐT trên hồ sơ khác tên đăng nhập.
INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, email, user_id)
SELECT ch.id, v.position, v.full_name, v.relationship, v.phone, v.citizen_id, v.email, u.id
FROM (VALUES ('Trẻ Demo A1-01', 0, 'Phụ huynh A Demo', 'MOTHER', '0900000006', '999900000006', 'parent.a@carenest.test', 'parent.a'),
             ('Trẻ Demo A1-02', 0, 'Bố Demo A1-02', 'FATHER', '0900000102', '999900000102', NULL, NULL),
             ('Trẻ Demo B1-01', 0, 'Phụ huynh B Demo', 'FATHER', '0900000007', '999900000007', NULL, 'parent.b'),
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
JOIN user_accounts u ON u.username = 'principal.dev'
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
JOIN user_accounts u ON u.username = 'principal.dev'
WHERE ch.full_name LIKE 'Trẻ Demo %'
  AND NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = ch.id);
