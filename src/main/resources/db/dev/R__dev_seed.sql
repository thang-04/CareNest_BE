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

-- Tiêu chí quan sát mẫu (bộ chính thức chờ P-11); thang Tốt/Đạt/Cần cố gắng
INSERT INTO observation_criteria (code, name, sort_order) VALUES
    ('PARTICIPATION', 'Tham gia hoạt động', 1),
    ('BEHAVIOUR', 'Hành vi, nề nếp', 2),
    ('EMOTION', 'Cảm xúc', 3),
    ('EATING', 'Ăn', 4),
    ('SLEEPING', 'Ngủ', 5)
ON CONFLICT (code) DO NOTHING;

INSERT INTO criteria_options (criteria_id, code, label, score, sort_order)
SELECT c.id, o.code, o.label, o.score, o.sort_order
FROM observation_criteria c
CROSS JOIN (VALUES ('GOOD', 'Tốt', 3, 1),
                   ('OK', 'Đạt', 2, 2),
                   ('NEEDS_SUPPORT', 'Cần cố gắng', 1, 3)) AS o (code, label, score, sort_order)
WHERE c.code IN ('PARTICIPATION', 'BEHAVIOUR', 'EMOTION', 'EATING', 'SLEEPING')
ON CONFLICT (criteria_id, code) DO NOTHING;

-- Hai tuần quan sát giả (thứ 2–6, 28/09–09/10/2026) cho 2 trẻ lớp A1 để demo nhận xét tuần/kỳ
INSERT INTO observations (child_id, classroom_id, observed_on, note, recorded_by)
SELECT ch.id, r.id, d::date,
       CASE d::date
           WHEN DATE '2026-09-30' THEN 'Hăng hái giơ tay phát biểu trong giờ kể chuyện'
           WHEN DATE '2026-10-02' THEN 'Chủ động giúp bạn cất đồ chơi'
           WHEN DATE '2026-10-06' THEN 'Ngủ trưa muộn, cần cô vỗ về một lúc'
           WHEN DATE '2026-10-08' THEN 'Ăn hết suất, tự xúc cơm gọn gàng'
       END,
       u.id
FROM generate_series(DATE '2026-09-28', DATE '2026-10-09', INTERVAL '1 day') AS d
JOIN children ch ON ch.full_name IN ('Trẻ Demo A1-01', 'Trẻ Demo A1-02')
JOIN classrooms r ON r.name = 'Lớp A1'
JOIN user_accounts u ON u.username = 'teacher.a'
WHERE EXTRACT(ISODOW FROM d) < 6
ON CONFLICT (child_id, observed_on) DO NOTHING;

-- Phần lớn "Tốt", xen kẽ "Đạt"; trẻ A1-02 ngủ trưa dao động Đạt/Cần cố gắng để có điểm cần theo dõi
INSERT INTO observation_values (observation_id, criteria_id, option_id)
SELECT o.id, c.id, opt.id
FROM observations o
JOIN children ch ON ch.id = o.child_id AND ch.full_name IN ('Trẻ Demo A1-01', 'Trẻ Demo A1-02')
CROSS JOIN observation_criteria c
JOIN criteria_options opt ON opt.criteria_id = c.id
    AND opt.sort_order = CASE
        WHEN c.code = 'SLEEPING' AND ch.full_name = 'Trẻ Demo A1-02'
            THEN 2 + (EXTRACT(DAY FROM o.observed_on)::int % 2)
        WHEN (EXTRACT(DAY FROM o.observed_on)::int + c.sort_order) % 4 = 0 THEN 2
        ELSE 1
    END
WHERE c.code IN ('PARTICIPATION', 'BEHAVIOUR', 'EMOTION', 'EATING', 'SLEEPING')
ON CONFLICT (observation_id, criteria_id) DO NOTHING;
