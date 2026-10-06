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

INSERT INTO campuses (code, name) VALUES
    ('CS-A', 'Cơ sở A (demo)'),
    ('CS-B', 'Cơ sở B (demo)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO school_years (name, start_date, end_date) VALUES ('2026-2027', '2026-09-01', '2027-05-31')
ON CONFLICT (name) DO NOTHING;

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
FROM (VALUES ('parent.a', 'Trẻ Demo A1-01', 'MOTHER'),
             ('parent.b', 'Trẻ Demo B1-01', 'FATHER')) AS v (username, child_name, relationship)
JOIN user_accounts u ON u.username = v.username
JOIN children ch ON ch.full_name = v.child_name
ON CONFLICT (user_id, child_id) DO NOTHING;
