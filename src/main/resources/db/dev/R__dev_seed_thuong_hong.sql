-- DỮ LIỆU GIẢ CHỈ CHO DEV/DEMO (profile "dev"): cơ cấu test của MN Thượng Hồng — 2 điểm trường, 10 lớp, ~215 trẻ,
-- 20 tài khoản nhân sự + ~105 tài khoản phụ huynh (một nửa số trẻ). Chạy sau R__dev_seed.sql (Flyway sắp repeatable theo mô tả). Không chứa dữ liệu thật:
--   - tên điểm trường là TÊN TẠM, sửa ở Cấu hình trường khi có tên/địa chỉ thật (chỉ đổi khi tên còn là bản demo cũ);
--   - nhân sự/trẻ/phụ huynh là tên giả, email @carenest.test, SĐT 0901xxxxxx, CCCD 9990xxxxxxxx (không có mã tỉnh 999);
--   - tài khoản mới chỉ có hồ sơ DB (không có user Keycloak) ⇒ hiện trong danh sách/phân công, không đăng nhập được;
--     đăng nhập dùng 8 tài khoản demo của R__dev_seed.sql.
-- Idempotent theo khóa tự nhiên (mã, tên, username) để Flyway chạy lại an toàn khi file đổi checksum.

-- 1. Điểm trường: tên tạm thay "(demo)"; code CS-A/CS-B giữ nguyên vì seed khác join theo code
UPDATE campuses
SET name = 'Cơ sở 1 – MN Thượng Hồng', short_name = 'Cơ sở 1', address = 'Thượng Hồng, Hải Phòng (chi tiết cập nhật sau)'
WHERE code = 'CS-A' AND name = 'Cơ sở A (demo)';
UPDATE campuses
SET name = 'Cơ sở 2 – MN Thượng Hồng', short_name = 'Cơ sở 2', address = 'Thượng Hồng, Hải Phòng (chi tiết cập nhật sau)'
WHERE code = 'CS-B' AND name = 'Cơ sở B (demo)';

UPDATE user_accounts SET full_name = 'Hiệu phó Cơ sở 1' WHERE username = 'viceprincipal' AND full_name = 'Hiệu phó Cơ sở A Demo';
UPDATE user_accounts SET full_name = 'Nhà bếp Cơ sở 1' WHERE username = 'kitchen' AND full_name = 'Nhà bếp Cơ sở A Demo';

-- 2. Phòng: đổi tên 2 phòng demo, thêm 8 phòng học + 2 bếp (mã/tên không trùng trong điểm trường)
UPDATE rooms r SET name = 'Phòng học A1'
FROM campuses c WHERE c.id = r.campus_id AND c.code = 'CS-A' AND r.code = 'DEMO-A1' AND r.name = 'Phòng demo A1'
  AND NOT EXISTS (SELECT 1 FROM rooms o WHERE o.campus_id = r.campus_id AND LOWER(o.name) = 'phòng học a1' AND o.status <> 'DELETED');
UPDATE rooms r SET name = 'Phòng học B1'
FROM campuses c WHERE c.id = r.campus_id AND c.code = 'CS-B' AND r.code = 'DEMO-B1' AND r.name = 'Phòng demo B1'
  AND NOT EXISTS (SELECT 1 FROM rooms o WHERE o.campus_id = r.campus_id AND LOWER(o.name) = 'phòng học b1' AND o.status <> 'DELETED');

INSERT INTO rooms (campus_id, code, name, room_type, capacity)
SELECT c.id, v.code, v.name, v.room_type, v.capacity
FROM (VALUES ('CS-A', 'PH-A2', 'Phòng học A2', 'CLASS', 25),
             ('CS-A', 'PH-A3', 'Phòng học A3', 'CLASS', 25),
             ('CS-A', 'PH-A4', 'Phòng học A4', 'CLASS', 35),
             ('CS-A', 'PH-A5', 'Phòng học A5', 'CLASS', 35),
             ('CS-B', 'PH-B2', 'Phòng học B2', 'CLASS', 25),
             ('CS-B', 'PH-B3', 'Phòng học B3', 'CLASS', 25),
             ('CS-B', 'PH-B4', 'Phòng học B4', 'CLASS', 30),
             ('CS-B', 'PH-B5', 'Phòng học B5', 'CLASS', 35),
             ('CS-A', 'BEP-A', 'Bếp Cơ sở 1', 'KITCHEN', NULL),
             ('CS-B', 'BEP-B', 'Bếp Cơ sở 2', 'KITCHEN', NULL)) AS v (campus_code, code, name, room_type, capacity)
JOIN campuses c ON c.code = v.campus_code
WHERE NOT EXISTS (
    SELECT 1 FROM rooms r
    WHERE r.campus_id = c.id AND r.status <> 'DELETED'
      AND (LOWER(r.code) = LOWER(v.code) OR LOWER(r.name) = LOWER(v.name))
);

-- 3. Lớp: mỗi cơ sở 5 lớp (nhà trẻ 1, mẫu giáo bé 1, nhỡ 1, lớn 2); Lớp A1 (nhỡ) và B1 (lớn) đã có từ seed demo
INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity)
SELECT c.id, y.id, v.name, g.id, r.id, g.max_class_size
FROM (VALUES ('CS-A', 'Lớp Nhà trẻ A1', 'ag-2', 'PH-A2'),
             ('CS-A', 'Lớp Mẫu giáo bé A1', 'ag-3', 'PH-A3'),
             ('CS-A', 'Lớp Mẫu giáo lớn A1', 'ag-5', 'PH-A4'),
             ('CS-A', 'Lớp Mẫu giáo lớn A2', 'ag-5', 'PH-A5'),
             ('CS-B', 'Lớp Nhà trẻ B1', 'ag-2', 'PH-B2'),
             ('CS-B', 'Lớp Mẫu giáo bé B1', 'ag-3', 'PH-B3'),
             ('CS-B', 'Lớp Mẫu giáo nhỡ B1', 'ag-4', 'PH-B4'),
             ('CS-B', 'Lớp Mẫu giáo lớn B2', 'ag-5', 'PH-B5')) AS v (campus_code, name, age_group_code, room_code)
JOIN campuses c ON c.code = v.campus_code
JOIN school_years y ON y.name = '2026-2027'
JOIN age_groups g ON g.code = v.age_group_code
JOIN rooms r ON r.campus_id = c.id AND LOWER(r.code) = LOWER(v.room_code) AND r.status <> 'DELETED'
-- Phòng đã có lớp khác trong năm thì bỏ qua (uk_classrooms_school_year_room)
WHERE NOT EXISTS (SELECT 1 FROM classrooms o WHERE o.school_year_id = y.id AND o.room_id = r.id)
ON CONFLICT (school_year_id, campus_id, name) DO NOTHING;

-- 4. Nhân sự: 18 giáo viên, 1 Phó hiệu trưởng và 1 bếp cho Cơ sở 2 (Cơ sở 1 dùng viceprincipal, kitchen của seed demo)
INSERT INTO user_accounts (keycloak_user_id, username, email, full_name, primary_role)
SELECT '00000000-0000-4000-c000-' || lpad(v.n::text, 12, '0'), v.username, v.username || '@carenest.test', v.full_name, v.role
FROM (VALUES (1, 'th.gv.a01', 'Nguyễn Thị Lan Anh', 'TEACHER'),
             (2, 'th.gv.a02', 'Trần Thu Hà', 'TEACHER'),
             (3, 'th.gv.a03', 'Lê Minh Châu', 'TEACHER'),
             (4, 'th.gv.a04', 'Phạm Thu Hương', 'TEACHER'),
             (5, 'th.gv.a05', 'Đỗ Thị Mai', 'TEACHER'),
             (6, 'th.gv.a06', 'Vũ Ngọc Ánh', 'TEACHER'),
             (7, 'th.gv.a07', 'Hoàng Thị Thảo', 'TEACHER'),
             (8, 'th.gv.a08', 'Bùi Thanh Tâm', 'TEACHER'),
             (9, 'th.gv.a09', 'Đặng Hồng Nhung', 'TEACHER'),
             (10, 'th.gv.b01', 'Ngô Thị Hạnh', 'TEACHER'),
             (11, 'th.gv.b02', 'Dương Thu Trang', 'TEACHER'),
             (12, 'th.gv.b03', 'Lý Thị Huyền', 'TEACHER'),
             (13, 'th.gv.b04', 'Phan Thùy Linh', 'TEACHER'),
             (14, 'th.gv.b05', 'Đinh Thị Hoa', 'TEACHER'),
             (15, 'th.gv.b06', 'Mai Phương Thảo', 'TEACHER'),
             (16, 'th.gv.b07', 'Trịnh Thị Loan', 'TEACHER'),
             (17, 'th.gv.b08', 'Lưu Hải Yến', 'TEACHER'),
             (18, 'th.gv.b09', 'Tạ Thị Kim Oanh', 'TEACHER'),
             (19, 'th.pht.b', 'Cao Thị Bích Ngọc', 'VICE_PRINCIPAL'),
             (20, 'th.bep.b', 'Nhà bếp Cơ sở 2', 'KITCHEN_STAFF')) AS v (n, username, full_name, role)
ON CONFLICT (keycloak_user_id) DO NOTHING;

-- Mỗi lớp 2 GV cố định, 1 chủ nhiệm (SCH-04, AUTH-24); A1/B1 đã có teacher1/teacher2 nên chỉ thêm 1 GV chủ nhiệm
INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom)
SELECT u.id, 'TEACHER', cl.id, DATE '2026-09-01', v.homeroom
FROM (VALUES ('th.gv.a01', 'CS-A', 'Lớp A1', TRUE),
             ('th.gv.a02', 'CS-A', 'Lớp Nhà trẻ A1', TRUE), ('th.gv.a03', 'CS-A', 'Lớp Nhà trẻ A1', FALSE),
             ('th.gv.a04', 'CS-A', 'Lớp Mẫu giáo bé A1', TRUE), ('th.gv.a05', 'CS-A', 'Lớp Mẫu giáo bé A1', FALSE),
             ('th.gv.a06', 'CS-A', 'Lớp Mẫu giáo lớn A1', TRUE), ('th.gv.a07', 'CS-A', 'Lớp Mẫu giáo lớn A1', FALSE),
             ('th.gv.a08', 'CS-A', 'Lớp Mẫu giáo lớn A2', TRUE), ('th.gv.a09', 'CS-A', 'Lớp Mẫu giáo lớn A2', FALSE),
             ('th.gv.b01', 'CS-B', 'Lớp B1', TRUE),
             ('th.gv.b02', 'CS-B', 'Lớp Nhà trẻ B1', TRUE), ('th.gv.b03', 'CS-B', 'Lớp Nhà trẻ B1', FALSE),
             ('th.gv.b04', 'CS-B', 'Lớp Mẫu giáo bé B1', TRUE), ('th.gv.b05', 'CS-B', 'Lớp Mẫu giáo bé B1', FALSE),
             ('th.gv.b06', 'CS-B', 'Lớp Mẫu giáo nhỡ B1', TRUE), ('th.gv.b07', 'CS-B', 'Lớp Mẫu giáo nhỡ B1', FALSE),
             ('th.gv.b08', 'CS-B', 'Lớp Mẫu giáo lớn B2', TRUE), ('th.gv.b09', 'CS-B', 'Lớp Mẫu giáo lớn B2', FALSE))
         AS v (username, campus_code, class_name, homeroom)
JOIN user_accounts u ON u.username = v.username
JOIN campuses c ON c.code = v.campus_code
JOIN classrooms cl ON cl.campus_id = c.id AND cl.name = v.class_name
    AND cl.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027')
WHERE NOT EXISTS (
    SELECT 1 FROM staff_assignments a
    WHERE a.user_id = u.id AND a.staff_role = 'TEACHER' AND a.classroom_id = cl.id AND a.valid_from = DATE '2026-09-01'
);

INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from)
SELECT u.id, v.staff_role, c.id, DATE '2026-09-01'
FROM (VALUES ('th.pht.b', 'VICE_PRINCIPAL', 'CS-B'), ('th.bep.b', 'KITCHEN_STAFF', 'CS-B')) AS v (username, staff_role, campus_code)
JOIN user_accounts u ON u.username = v.username
JOIN campuses c ON c.code = v.campus_code
WHERE NOT EXISTS (
    SELECT 1 FROM staff_assignments a
    WHERE a.user_id = u.id AND a.staff_role = v.staff_role AND a.campus_id = c.id AND a.valid_from = DATE '2026-09-01'
);

-- Tổ trưởng nhà trẻ và mẫu giáo bé (mẫu giáo nhỡ, lớn đã có từ seed demo); 1 người/nhóm tuổi/năm (AUTH-23)
INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id, valid_from)
SELECT u.id, 'AGE_GROUP_TEAM_LEADER', g.id, y.id, DATE '2026-09-01'
FROM (VALUES ('th.gv.a02', 'ag-2'), ('th.gv.a04', 'ag-3')) AS v (username, age_group_code)
JOIN user_accounts u ON u.username = v.username
JOIN age_groups g ON g.code = v.age_group_code
JOIN school_years y ON y.name = '2026-2027'
WHERE NOT EXISTS (
    SELECT 1 FROM staff_assignments a
    WHERE a.staff_role = 'AGE_GROUP_TEAM_LEADER' AND a.age_group_id = g.id AND a.school_year_id = y.id
);

-- 5. Trẻ giả lập: sĩ số mỗi lớp ~22 (kể cả trẻ demo có sẵn), tuổi theo nhóm tuổi của lớp tại 01/09/2026
CREATE TEMP TABLE th_class_plan AS
SELECT v.campus_code, v.class_name, v.age_group_code, v.n,
       COALESCE(SUM(v.n) OVER (ORDER BY v.ord ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)::int AS start_idx
FROM (VALUES (1, 'CS-A', 'Lớp A1', 'ag-4', 20),
             (2, 'CS-B', 'Lớp B1', 'ag-5', 21),
             (3, 'CS-A', 'Lớp Nhà trẻ A1', 'ag-2', 19),
             (4, 'CS-B', 'Lớp Nhà trẻ B1', 'ag-2', 19),
             (5, 'CS-A', 'Lớp Mẫu giáo bé A1', 'ag-3', 22),
             (6, 'CS-B', 'Lớp Mẫu giáo bé B1', 'ag-3', 22),
             (7, 'CS-A', 'Lớp Mẫu giáo lớn A1', 'ag-5', 22),
             (8, 'CS-A', 'Lớp Mẫu giáo lớn A2', 'ag-5', 21),
             (9, 'CS-B', 'Lớp Mẫu giáo nhỡ B1', 'ag-4', 22),
             (10, 'CS-B', 'Lớp Mẫu giáo lớn B2', 'ag-5', 21))
         AS v (ord, campus_code, class_name, age_group_code, n);

-- Tên: (họ, tên đệm, tên) chọn theo idx sao cho không trùng; giới tính xen kẽ theo (idx % 20 + idx / 20)
CREATE TEMP TABLE th_children AS
SELECT s.*,
       sur[(s.idx % 20) + 1] || ' '
           || CASE WHEN s.gender = 'MALE' THEN mid_m[(s.idx / 20) % 8 + 1] ELSE mid_f[(s.idx / 20) % 8 + 1] END || ' '
           || CASE WHEN s.gender = 'MALE' THEN giv_m[(s.idx / 20) % 12 + 1] ELSE giv_f[(s.idx / 20) % 12 + 1] END AS full_name,
       sur[(s.idx % 20) + 1] AS surname
FROM (
    SELECT p.campus_code, p.class_name, p.age_group_code, p.start_idx + k AS idx, k,
           CASE WHEN ((p.start_idx + k) % 20 + (p.start_idx + k) / 20) % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END AS gender,
           'HS26' || lpad((101 + p.start_idx + k)::text, 3, '0') AS code,
           (DATE '2026-09-01' - make_interval(months => g.age_to_months::int))::date
               + 1 + ((k * 47 + (p.start_idx + k) * 13) % 360) AS date_of_birth
    FROM th_class_plan p
    JOIN age_groups g ON g.code = p.age_group_code
    CROSS JOIN LATERAL generate_series(0, p.n - 1) AS k
) s
CROSS JOIN (SELECT ARRAY['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ',
                         'Ngô', 'Dương', 'Lý', 'Phan', 'Đinh', 'Mai', 'Trịnh', 'Lưu', 'Tạ', 'Cao'] AS sur,
                   ARRAY['Văn', 'Đức', 'Minh', 'Gia', 'Quang', 'Hữu', 'Tuấn', 'Bảo'] AS mid_m,
                   ARRAY['Thị', 'Ngọc', 'Thu', 'Khánh', 'Bảo', 'Diệu', 'Gia', 'Hà'] AS mid_f,
                   ARRAY['An', 'Bách', 'Cường', 'Dũng', 'Hải', 'Hưng', 'Khôi', 'Long', 'Nam', 'Phúc', 'Sơn', 'Việt'] AS giv_m,
                   ARRAY['An', 'Chi', 'Diệp', 'Giang', 'Hân', 'Lan', 'Linh', 'My', 'Ngân', 'Quỳnh', 'Thảo', 'Vy'] AS giv_f) n;

INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on)
SELECT t.code, t.full_name, t.date_of_birth, t.gender, 'ACTIVE', DATE '2026-09-01'
FROM th_children t
ON CONFLICT (code) DO NOTHING;

INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT left(code, 4), max(substring(code FROM 5)::integer) FROM children GROUP BY left(code, 4)
ON CONFLICT (code_prefix) DO UPDATE SET last_number = GREATEST(child_code_sequences.last_number, EXCLUDED.last_number);

INSERT INTO enrollments (child_id, classroom_id, valid_from)
SELECT ch.id, cl.id, DATE '2026-09-01'
FROM th_children t
JOIN children ch ON ch.code = t.code
JOIN campuses c ON c.code = t.campus_code
JOIN classrooms cl ON cl.campus_id = c.id AND cl.name = t.class_name
    AND cl.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027')
WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.valid_to IS NULL);

-- Phụ huynh trên hồ sơ (chưa có tài khoản ⇒ chờ kích hoạt): người đầu theo idx chẵn/lẻ là bố/mẹ, 2/3 trẻ có người thứ hai
INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id)
SELECT ch.id, g.position, g.full_name, g.relationship, g.phone, g.citizen_id
FROM th_children t
JOIN children ch ON ch.code = t.code
CROSS JOIN LATERAL (
    SELECT p.position, p.relationship,
           CASE WHEN p.relationship = 'FATHER'
                    THEN t.surname || ' Văn ' || (ARRAY['An', 'Bách', 'Cường', 'Dũng', 'Hải', 'Hưng', 'Khôi', 'Long', 'Nam', 'Phúc', 'Sơn', 'Việt'])[(t.idx * 7) % 12 + 1]
                ELSE (ARRAY['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương', 'Lý', 'Phan', 'Đinh', 'Mai', 'Trịnh', 'Lưu', 'Tạ', 'Cao'])[(t.idx * 3 + 5) % 20 + 1]
                    || ' Thị ' || (ARRAY['An', 'Chi', 'Diệp', 'Giang', 'Hân', 'Lan', 'Linh', 'My', 'Ngân', 'Quỳnh', 'Thảo', 'Vy'])[(t.idx * 5) % 12 + 1]
           END AS full_name,
           '0901' || lpad((t.idx * 2 + 1 + p.position)::text, 6, '0') AS phone,
           '9990' || lpad((t.idx * 2 + 1 + p.position)::text, 8, '0') AS citizen_id
    FROM (VALUES (0, CASE WHEN t.idx % 2 = 0 THEN 'FATHER' ELSE 'MOTHER' END),
                 (1, CASE WHEN t.idx % 2 = 0 THEN 'MOTHER' ELSE 'FATHER' END)) AS p (position, relationship)
    WHERE p.position = 0 OR t.idx % 3 <> 0
) g
WHERE NOT EXISTS (SELECT 1 FROM child_guardians x WHERE x.child_id = ch.id AND x.position = g.position);

INSERT INTO child_profiles (child_id, premature, ethnicity, beneficiary, address_province, address_ward, address_street)
SELECT ch.id, 'NO', 'Kinh', 'Không', 'Thành phố Hải Phòng', 'Thôn ' || (t.idx % 6 + 1),
       'Số ' || (t.idx % 40 + 1) || ' ngõ ' || (t.idx % 9 + 1)
FROM th_children t
JOIN children ch ON ch.code = t.code
ON CONFLICT (child_id) DO NOTHING;

-- Khai báo sức khỏe khi tiếp nhận: không có gì đặc biệt, trừ ~6% trẻ khai dị ứng (tôm, sữa bò, trứng, đậu phộng, cua)
INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state, self_history_state,
                                       family_history_state, tb_contact_state, declared_by, declared_at)
SELECT ch.id, CASE WHEN t.idx % 17 = 3 THEN 'REPORTED' ELSE 'NONE_REPORTED' END, 'NONE_REPORTED', 'NONE_REPORTED',
       'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', u.id, TIMESTAMPTZ '2026-09-01 08:00+07'
FROM th_children t
JOIN children ch ON ch.code = t.code
JOIN user_accounts u ON u.username = 'principal'
ON CONFLICT (child_id) DO NOTHING;

INSERT INTO child_allergies (child_id, name)
SELECT ch.id, (ARRAY['Tôm', 'Sữa bò', 'Trứng', 'Đậu phộng', 'Cua'])[(t.idx / 17) % 5 + 1]
FROM th_children t
JOIN children ch ON ch.code = t.code
JOIN child_health_declarations d ON d.child_id = ch.id AND d.allergies_state = 'REPORTED'
WHERE t.idx % 17 = 3
  AND NOT EXISTS (SELECT 1 FROM child_allergies a WHERE a.child_id = ch.id);

INSERT INTO child_health_declaration_history (child_id, action, user_id, created_at)
SELECT d.child_id, 'DECLARED', d.declared_by, d.declared_at
FROM th_children t
JOIN children ch ON ch.code = t.code
JOIN child_health_declarations d ON d.child_id = ch.id
WHERE NOT EXISTS (SELECT 1 FROM child_health_declaration_history h WHERE h.child_id = d.child_id);

INSERT INTO child_history (child_id, action, user_id, created_at)
SELECT ch.id, 'ENROLLED', u.id, TIMESTAMPTZ '2026-09-01 08:00+07'
FROM th_children t
JOIN children ch ON ch.code = t.code
JOIN user_accounts u ON u.username = 'principal'
WHERE NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = ch.id);

-- Phụ huynh đã kích hoạt tài khoản: người thứ nhất của trẻ có idx chẵn (~một nửa số trẻ); tên đăng nhập = SĐT (AUTH-18).
-- Chỉ hồ sơ DB (không có user Keycloak) nên không đăng nhập được; phục vụ đơn nghỉ, thông báo đón trẻ, liên kết phụ huynh–trẻ.
INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role, citizen_id)
SELECT '00000000-0000-4000-d000-' || lpad(t.idx::text, 12, '0'), g.phone, g.full_name, 'PARENT', g.citizen_id
FROM th_children t
JOIN children ch ON ch.code = t.code
JOIN child_guardians g ON g.child_id = ch.id AND g.position = 0
WHERE t.idx % 2 = 0
  AND NOT EXISTS (SELECT 1 FROM user_accounts u WHERE u.username = g.phone OR u.citizen_id = g.citizen_id)
ON CONFLICT (keycloak_user_id) DO NOTHING;

INSERT INTO guardian_child_links (user_id, child_id, relationship)
SELECT u.id, ch.id, g.relationship
FROM th_children t
JOIN children ch ON ch.code = t.code
JOIN child_guardians g ON g.child_id = ch.id AND g.position = 0
JOIN user_accounts u ON u.username = g.phone AND u.keycloak_user_id LIKE '00000000-0000-4000-d000-%'
WHERE t.idx % 2 = 0
ON CONFLICT (user_id, child_id) DO NOTHING;

UPDATE child_guardians g SET user_id = u.id
FROM th_children t, children ch, user_accounts u
WHERE ch.code = t.code AND g.child_id = ch.id AND g.position = 0 AND t.idx % 2 = 0
  AND u.username = g.phone AND u.keycloak_user_id LIKE '00000000-0000-4000-d000-%' AND g.user_id IS NULL;

DROP TABLE th_children;
DROP TABLE th_class_plan;

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
