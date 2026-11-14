-- Bộ dữ liệu dev (profile "dev"): lịch sử năm học. Năm 2025-2026 đã đóng (lớp, phân công, trẻ, đánh giá học kỳ/cuối
-- năm, đánh giá học kỳ của lớp, xếp loại nhóm lớp, khen thưởng cuối năm, xếp lớp chuyển sang 2026-2027) và năm
-- 2027-2028 sắp diễn ra đang dựng dở (lớp, một phần phân công GV, một phần xếp lớp năm mới).
-- Dữ liệu giả nhìn như thật. Chạy sau R__dev_05_evaluation_health.sql. Neo CURRENT_DATE.
--
-- Mô phỏng đúng luồng code: năm cũ ACTIVE tới khi HT kích hoạt 2026-2027 (kích hoạt tự đóng năm cũ, đóng lớp, chụp
-- phòng/điểm trường/nhóm tuổi; lớp cũ của trẻ lên lớp kết thúc trước ngày bắt đầu năm mới 1 ngày). Trẻ ra trường /
-- nghỉ học sau 2025-2026 là hồ sơ mới (mã HS25…), để trạng thái "Đã nghỉ học" từ 01/06/2026.
-- Mục tiêu (MT) trong phiếu cuối năm và bảng học kỳ lấy theo kế hoạch năm của nhóm tuổi (cùng dòng gốc MT), năm cũ
-- không có kế hoạch giáo dục riêng.

-- ---------- Hàm phụ trong phiên (xóa cuối file) ----------
CREATE FUNCTION pg_temp.d06_h(k text) RETURNS bigint LANGUAGE sql IMMUTABLE AS
$$ SELECT abs(hashtext(k)::bigint) $$;

CREATE FUNCTION pg_temp.d06_at(d date, t time) RETURNS timestamptz LANGUAGE sql IMMUTABLE AS
$$ SELECT (d + t) AT TIME ZONE 'Asia/Ho_Chi_Minh' $$;

CREATE FUNCTION pg_temp.d06_pick(arr text[], h bigint) RETURNS text LANGUAGE sql IMMUTABLE AS
$$ SELECT arr[1 + (h % array_length(arr, 1))::int] $$;

-- ---------- 1. Năm học ----------
-- Hiệu trưởng hiện tại (cũng là HT năm cũ)
CREATE TEMP TABLE d06_principal AS
SELECT a.user_id FROM staff_assignments a
WHERE a.staff_role = 'PRINCIPAL' AND a.valid_to IS NULL ORDER BY a.id LIMIT 1;

INSERT INTO school_years (name, start_date, end_date, semester1_start, semester1_end, semester2_start, semester2_end,
                          prep_date, status, activated_at, activated_by, closed_at, closed_by, created_at, updated_at)
SELECT '2025-2026', DATE '2025-09-01', DATE '2026-05-31', DATE '2025-09-01', DATE '2026-01-16', DATE '2026-01-19',
       DATE '2026-05-22', DATE '2025-08-15', 'CLOSED', pg_temp.d06_at(DATE '2025-08-26', TIME '09:00'), p.user_id,
       coalesce(cur.activated_at, pg_temp.d06_at(DATE '2026-08-20', TIME '09:00')), p.user_id,
       pg_temp.d06_at(DATE '2025-06-20', TIME '09:00'),
       coalesce(cur.activated_at, pg_temp.d06_at(DATE '2026-08-20', TIME '09:00'))
FROM d06_principal p
LEFT JOIN school_years cur ON cur.name = '2026-2027'
WHERE NOT EXISTS (SELECT 1 FROM school_years y WHERE y.name = '2025-2026');

-- Năm sắp diễn ra: HT mới tạo, chưa đặt ngày học kỳ, chưa cài giờ chốt (thiết lập còn dở)
INSERT INTO school_years (name, start_date, end_date, prep_date, status, created_at, updated_at)
SELECT '2027-2028', DATE '2027-09-01', DATE '2028-05-31', DATE '2027-08-02', 'PLANNED',
       LEAST(pg_temp.d06_at(DATE '2026-11-09', TIME '10:15'), now()),
       LEAST(pg_temp.d06_at(DATE '2026-11-09', TIME '10:15'), now())
WHERE NOT EXISTS (SELECT 1 FROM school_years y WHERE y.name = '2027-2028');

CREATE TEMP TABLE d06_ctx AS
SELECT o.id AS old_id, c.id AS cur_id, c.start_date AS cur_start, n.id AS next_id, p.user_id AS principal_id,
       u.full_name AS principal_name
FROM school_years o
JOIN school_years c ON c.name = '2026-2027'
JOIN school_years n ON n.name = '2027-2028'
CROSS JOIN d06_principal p
JOIN user_accounts u ON u.id = p.user_id
WHERE o.name = '2025-2026';

-- Ngày nghỉ năm cũ, giờ chốt điểm danh năm cũ
INSERT INTO school_holidays (school_year_id, start_date, end_date, name)
SELECT x.old_id, v.s::date, v.e::date, v.name
FROM d06_ctx x
CROSS JOIN (VALUES ('2025-09-01', '2025-09-02', 'Nghỉ lễ Quốc khánh'),
                   ('2026-01-01', '2026-01-01', 'Nghỉ Tết Dương lịch'),
                   ('2026-02-14', '2026-02-22', 'Nghỉ Tết Nguyên đán Bính Ngọ'),
                   ('2026-04-27', '2026-04-27', 'Nghỉ bù Giỗ Tổ Hùng Vương'),
                   ('2026-04-30', '2026-05-01', 'Nghỉ lễ 30/4 và 1/5')) AS v (s, e, name)
WHERE NOT EXISTS (SELECT 1 FROM school_holidays h WHERE h.school_year_id = x.old_id AND h.start_date = v.s::date);

INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from, note, created_by, created_at)
SELECT x.old_id, TIME '08:30', DATE '2025-09-01', 'Chốt điểm danh và báo ăn trước 8 giờ 30', x.principal_id,
       pg_temp.d06_at(DATE '2025-08-25', TIME '09:00')
FROM d06_ctx x
WHERE NOT EXISTS (SELECT 1 FROM attendance_cutoffs a WHERE a.school_year_id = x.old_id);

-- Bản chụp lúc đóng năm (như SchoolYearRepository.snapshotRooms/Campuses/AgeGroups)
INSERT INTO school_year_rooms (school_year_id, room_id, campus_id, code, name, room_type, capacity, manager_user_id,
                               status, note)
SELECT x.old_id, r.id, r.campus_id, r.code, r.name, r.room_type, r.capacity, r.manager_user_id, r.status, r.note
FROM d06_ctx x CROSS JOIN rooms r WHERE r.status <> 'DELETED'
ON CONFLICT ON CONSTRAINT uk_school_year_rooms DO NOTHING;

INSERT INTO school_year_campuses (school_year_id, campus_id, code, name, short_name, address, phone, is_main)
SELECT x.old_id, c.id, c.code, c.name, c.short_name, c.address, c.phone, c.is_main
FROM d06_ctx x CROSS JOIN campuses c
ON CONFLICT ON CONSTRAINT uk_school_year_campuses DO NOTHING;

INSERT INTO school_year_age_groups (school_year_id, age_group_id, code, name, short_name, sort_order, age_from_months,
                                    age_to_months, meal_sessions, max_class_size, nutrition_note)
SELECT x.old_id, g.id, g.code, g.name, g.short_name, g.sort_order, g.age_from_months, g.age_to_months,
       g.meal_sessions, g.max_class_size, g.nutrition_note
FROM d06_ctx x CROSS JOIN age_groups g
ON CONFLICT ON CONSTRAINT uk_school_year_age_groups DO NOTHING;

-- ---------- 2. Lớp năm cũ (đã đóng) và năm sắp tới, cùng cơ cấu lớp/phòng với năm hiện tại ----------
INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity, status, created_at, updated_at)
SELECT cl.campus_id, y.id, cl.name, cl.age_group_id, cl.room_id, cl.capacity, y.st, y.created, y.updated
FROM classrooms cl
CROSS JOIN d06_ctx x
CROSS JOIN LATERAL (VALUES
    (x.old_id, 'CLOSED', pg_temp.d06_at(DATE '2025-07-10', TIME '09:30'), x.principal_id),
    (x.next_id, 'ACTIVE', LEAST(pg_temp.d06_at(DATE '2026-11-09', TIME '10:40'), now()), x.principal_id)
) AS y0 (id, st, created, by_id)
CROSS JOIN LATERAL (SELECT y0.id, y0.st, y0.created,
                           CASE WHEN y0.st = 'CLOSED' THEN (SELECT closed_at FROM school_years WHERE id = x.old_id)
                                ELSE y0.created END AS updated) y
WHERE cl.school_year_id = x.cur_id
ON CONFLICT ON CONSTRAINT uk_classrooms_school_year_campus_name DO NOTHING;

CREATE TEMP TABLE d06_cls AS
SELECT cur.id AS cur_id, o.id AS old_id, n.id AS next_id, cur.campus_id, cp.code AS campus_code, cur.name,
       cur.age_group_id, g.sort_order, g.age_from_months AS age_from, cur.capacity,
       row_number() OVER (ORDER BY cp.code, g.sort_order, cur.name)::int AS k,
       (SELECT max(sort_order) FROM age_groups) AS max_sort
FROM classrooms cur
CROSS JOIN d06_ctx x
JOIN campuses cp ON cp.id = cur.campus_id
JOIN age_groups g ON g.id = cur.age_group_id
JOIN classrooms o ON o.school_year_id = x.old_id AND o.campus_id = cur.campus_id AND o.name = cur.name
JOIN classrooms n ON n.school_year_id = x.next_id AND n.campus_id = cur.campus_id AND n.name = cur.name
WHERE cur.school_year_id = x.cur_id;

-- ---------- 3. Phân công năm cũ: HT, PHT, bếp, GV (cùng lớp như năm nay), tổ trưởng ----------
CREATE TEMP TABLE d06_staff (user_id bigint, staff_role text, campus_id bigint, classroom_id bigint, age_group_id bigint,
                             school_year_id bigint, is_homeroom boolean, valid_from date, valid_to date);

INSERT INTO d06_staff
SELECT a.user_id, a.staff_role, a.campus_id, NULL, NULL, NULL, false, DATE '2025-08-01', DATE '2026-08-14'
FROM staff_assignments a
WHERE a.staff_role IN ('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF') AND a.valid_to IS NULL;

-- GV chính đầu năm nay (kể cả GV đã nghỉ việc giữa năm nay) dạy lớp cùng tên năm cũ; GV mới vào giữa năm không có
INSERT INTO d06_staff
SELECT a.user_id, 'TEACHER', NULL, c.old_id, NULL, NULL, a.is_homeroom, DATE '2025-08-15', DATE '2026-05-31'
FROM staff_assignments a
JOIN d06_cls c ON c.cur_id = a.classroom_id
CROSS JOIN d06_ctx x
WHERE a.staff_role = 'TEACHER' AND NOT a.is_substitute AND a.valid_from <= x.cur_start;

INSERT INTO d06_staff
SELECT a.user_id, 'AGE_GROUP_TEAM_LEADER', NULL, NULL, a.age_group_id, x.old_id, false, DATE '2025-08-15',
       DATE '2026-05-31'
FROM staff_assignments a
CROSS JOIN d06_ctx x
WHERE a.staff_role = 'AGE_GROUP_TEAM_LEADER' AND a.school_year_id = x.cur_id AND a.valid_to IS NULL;

-- Năm sắp tới (dựng dở): mới phân GV cho các lớp Cơ sở 1, từ ngày mở chuẩn bị tới hết năm (như TeacherAssignment)
INSERT INTO d06_staff
SELECT a.user_id, 'TEACHER', NULL, c.next_id, NULL, NULL, a.is_homeroom, n.prep_date, n.end_date
FROM staff_assignments a
JOIN d06_cls c ON c.cur_id = a.classroom_id
CROSS JOIN d06_ctx x
JOIN school_years n ON n.id = x.next_id
WHERE a.staff_role = 'TEACHER' AND NOT a.is_substitute AND a.valid_to IS NULL AND c.campus_code = 'CS-A';

INSERT INTO staff_assignments (user_id, staff_role, campus_id, classroom_id, age_group_id, school_year_id, is_homeroom,
                               valid_from, valid_to, assigned_by, created_at, updated_at)
SELECT s.user_id, s.staff_role, s.campus_id, s.classroom_id, s.age_group_id, s.school_year_id, s.is_homeroom,
       s.valid_from, s.valid_to, x.principal_id,
       LEAST(pg_temp.d06_at(s.valid_from - 5, TIME '09:00'), now()),
       LEAST(pg_temp.d06_at(s.valid_from - 5, TIME '09:00'), now())
FROM d06_staff s CROSS JOIN d06_ctx x
WHERE NOT EXISTS (SELECT 1 FROM staff_assignments a
                  WHERE a.user_id = s.user_id AND a.staff_role = s.staff_role AND a.valid_from = s.valid_from
                    AND a.campus_id IS NOT DISTINCT FROM s.campus_id
                    AND a.classroom_id IS NOT DISTINCT FROM s.classroom_id
                    AND a.age_group_id IS NOT DISTINCT FROM s.age_group_id);

CREATE TEMP TABLE d06_hr AS
SELECT c.old_id, a.user_id, u.full_name
FROM d06_cls c
JOIN staff_assignments a ON a.classroom_id = c.old_id AND a.staff_role = 'TEACHER' AND a.is_homeroom
JOIN user_accounts u ON u.id = a.user_id;

CREATE TEMP TABLE d06_vp AS
SELECT DISTINCT ON (a.campus_id) a.campus_id, a.user_id, u.full_name
FROM staff_assignments a JOIN user_accounts u ON u.id = a.user_id
WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.valid_from <= DATE '2026-05-31' AND a.valid_to >= DATE '2025-09-01'
ORDER BY a.campus_id, a.id;

-- ---------- 4. Trẻ năm cũ ----------
-- 4a. Trẻ đang học năm nay vào lớp ngay đầu năm: phần lớn đã học lớp nhóm tuổi liền dưới năm cũ (cùng cơ sở),
--     số còn lại mới vào trường năm nay. Lớp Chồi năm cũ chỉ 1 lớp nên chỉ khoảng 60% trẻ Lá năm nay học lên từ đó.
CREATE TEMP TABLE d06_cont AS
WITH firsts AS (
    SELECT DISTINCT ON (e.child_id) e.child_id, e.classroom_id, e.valid_from
    FROM enrollments e JOIN d06_cls c ON c.cur_id = e.classroom_id
    ORDER BY e.child_id, e.valid_from, e.id
), cand AS (
    SELECT ch.id AS child_id, ch.code, ch.full_name, f.classroom_id AS cur_class, prev.old_id AS old_class,
           prev.capacity AS old_cap, pg_temp.d06_h(ch.code || ':y2526') AS h, c.sort_order
    FROM firsts f
    CROSS JOIN d06_ctx x
    JOIN children ch ON ch.id = f.child_id
    JOIN d06_cls c ON c.cur_id = f.classroom_id
    JOIN d06_cls prev ON prev.campus_id = c.campus_id AND prev.sort_order = c.sort_order - 1
    WHERE f.valid_from = x.cur_start
)
SELECT child_id, code, full_name, cur_class, old_class, h
FROM (SELECT cand.*, row_number() OVER (PARTITION BY old_class ORDER BY h, child_id) AS rn
      FROM cand
      WHERE h % 100 < CASE sort_order WHEN 2 THEN 80 WHEN 3 THEN 85 ELSE 60 END) z
WHERE rn <= old_cap - 2;

-- 4b. Hồ sơ mới: trẻ ra trường (lớp Lá năm cũ, 16 trẻ/lớp) và trẻ nghỉ học sau năm cũ (1 trẻ/lớp Nhà trẻ, Mầm, Chồi)
CREATE TEMP TABLE d06_new AS
WITH slots AS (
    SELECT c.old_id, c.campus_id, c.sort_order, c.max_sort, c.k,
           CASE WHEN c.sort_order = c.max_sort THEN 'GRADUATE' ELSE 'LEAVE' END AS kind,
           CASE WHEN c.sort_order = c.max_sort THEN 16 ELSE 1 END AS n
    FROM d06_cls c
), numbered AS (
    SELECT s.*, g.i,
           row_number() OVER (ORDER BY s.kind DESC, s.k, g.i)::int AS seq
    FROM slots s CROSS JOIN LATERAL generate_series(1, s.n) AS g (i)
)
SELECT 'HS25' || lpad(nb.seq::text, 3, '0') AS code, nb.old_id AS old_class, nb.kind, nb.seq,
       pg_temp.d06_h('HS25' || nb.seq || ':ten') AS h,
       -- Năm sinh theo nhóm tuổi năm cũ: lớp Lá năm cũ sinh 09/2019–08/2020, mỗi nhóm nhỏ hơn trẻ hơn 1 năm
       (make_date(2019 + (nb.max_sort - nb.sort_order)::int, 9, 1)
            + (pg_temp.d06_h('HS25' || nb.seq || ':dob') % 360)::int) AS dob
FROM numbered nb;

ALTER TABLE d06_new ADD COLUMN gender text, ADD COLUMN full_name text, ADD COLUMN left_reason text;
UPDATE d06_new SET gender = CASE WHEN h % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END;
UPDATE d06_new SET full_name =
    pg_temp.d06_pick(ARRAY['Nguyễn','Trần','Lê','Phạm','Hoàng','Vũ','Đặng','Bùi','Đỗ','Ngô','Dương','Phan','Đinh',
                           'Trịnh','Lương','Nguyễn','Trần','Lê'], h / 3)
    || ' ' || CASE WHEN gender = 'MALE'
                   THEN pg_temp.d06_pick(ARRAY['Minh','Gia','Đức','Quang','Anh','Bảo','Hải','Tuấn','Văn','Hoàng',
                                               'Nhật','Thành'], h / 59)
                   ELSE pg_temp.d06_pick(ARRAY['Thị','Ngọc','Bảo','Gia','Khánh','Minh','Thảo','Phương','Thu','Diệu',
                                               'Hà','Tú'], h / 59) END
    || ' ' || CASE WHEN gender = 'MALE'
                   THEN pg_temp.d06_pick(ARRAY['An','Bình','Khang','Phúc','Huy','Nam','Long','Khôi','Minh','Duy',
                                               'Đăng','Lâm','Hiếu','Kiên','Sơn','Tùng','Vinh','Quân'], h / 997)
                   ELSE pg_temp.d06_pick(ARRAY['An','Chi','Hà','Linh','Ngân','Nhi','Thư','Trang','Vy','Yến','Mai',
                                               'Ngọc','Hân','Lan','Như','My','Uyên','Diệp'], h / 997) END;
UPDATE d06_new SET left_reason = CASE WHEN kind = 'GRADUATE'
    THEN 'Hoàn thành chương trình giáo dục mầm non, ra trường vào lớp 1'
    ELSE pg_temp.d06_pick(ARRAY['Gia đình chuyển nơi ở về quê nội, xin cho trẻ nghỉ học',
                                'Gia đình chuyển trẻ sang trường gần nơi làm việc của bố mẹ',
                                'Bố mẹ chuyển công tác vào Đà Nẵng, cả nhà chuyển theo'], h / 7) END;

INSERT INTO children (full_name, date_of_birth, code, gender, status, enrolled_on, left_on, left_reason, created_at,
                      updated_at)
SELECT n.full_name, n.dob, n.code, n.gender, 'LEFT', DATE '2025-09-01', DATE '2026-06-01', n.left_reason,
       pg_temp.d06_at(DATE '2025-08-18', TIME '08:00') + n.seq * INTERVAL '7 minutes',
       pg_temp.d06_at(DATE '2026-06-01', TIME '08:00')
FROM d06_new n
ON CONFLICT ON CONSTRAINT uk_children_code DO NOTHING;

INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT 'HS25', max(seq) FROM d06_new HAVING count(*) > 0
ON CONFLICT (code_prefix) DO UPDATE SET last_number = GREATEST(child_code_sequences.last_number, EXCLUDED.last_number);

-- Danh sách lớp năm cũ: trẻ học tiếp (lớp cũ kết thúc 31/08/2026, hôm trước ngày bắt đầu năm mới, như chuyển năm
-- học) và trẻ ra trường / nghỉ (kết thúc 31/05/2026, ngày nghỉ học 01/06/2026)
CREATE TEMP TABLE d06_roster AS
SELECT t.child_id, ch.code, ch.full_name, regexp_replace(ch.full_name, '^.* ', '') AS given, t.old_class, t.cur_class,
       t.kind, t.valid_to, pg_temp.d06_h(ch.code || ':eval2526') AS h, c.campus_id, c.sort_order, c.max_sort,
       c.age_group_id, c.age_from, c.k
FROM (SELECT child_id, old_class, cur_class, 'PROMOTE' AS kind, DATE '2026-08-31' AS valid_to FROM d06_cont
      UNION ALL
      SELECT ch.id, n.old_class, NULL, n.kind, DATE '2026-05-31' FROM d06_new n JOIN children ch ON ch.code = n.code) t
JOIN children ch ON ch.id = t.child_id
JOIN d06_cls c ON c.old_id = t.old_class;

INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to, created_by, created_at, updated_at)
SELECT r.child_id, r.old_class, DATE '2025-09-01', r.valid_to, vp.user_id,
       pg_temp.d06_at(DATE '2025-08-22', TIME '10:00'), pg_temp.d06_at(r.valid_to + 1, TIME '00:05')
FROM d06_roster r
LEFT JOIN d06_vp vp ON vp.campus_id = r.campus_id
WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = r.child_id AND e.classroom_id = r.old_class);

-- Bản xếp lớp chuyển sang 2026-2027 (đã áp dụng khi kích hoạt): lên lớp / ra trường / nghỉ học
INSERT INTO class_placements (school_year_id, child_id, from_classroom_id, action, to_classroom_id, updated_by,
                              created_at, updated_at)
SELECT x.cur_id, r.child_id, r.old_class, r.kind, r.cur_class, x.principal_id,
       pg_temp.d06_at(DATE '2026-08-10' + (r.k % 4), TIME '09:00') + (r.h % 150) * INTERVAL '1 minute',
       pg_temp.d06_at(DATE '2026-08-10' + (r.k % 4), TIME '09:00') + (r.h % 150) * INTERVAL '1 minute'
FROM d06_roster r CROSS JOIN d06_ctx x
ON CONFLICT ON CONSTRAINT uk_class_placements_school_year_child DO NOTHING;

-- ---------- 5. Mục tiêu theo nhóm tuổi (dòng gốc MT của kế hoạch năm đã duyệt) ----------
CREATE TEMP TABLE d06_goal AS
SELECT p.age_group_id, COALESCE(g.lineage_id, g.id) AS lineage, g.goal_no, g.text, d.id AS domain_id,
       d.name AS domain_name, coalesce(d.sort_order, 99) AS dom_sort,
       row_number() OVER (PARTITION BY p.age_group_id, d.id ORDER BY g.goal_no, g.sort_order, g.id)::int AS dom_rank,
       row_number() OVER (PARTITION BY p.age_group_id ORDER BY d.sort_order NULLS LAST, d.id NULLS LAST, g.goal_no,
                          g.sort_order, g.id)::int AS ord
FROM edu_team_plans p
CROSS JOIN d06_ctx x
JOIN edu_plan_goals g ON g.team_plan_id = p.id
LEFT JOIN edu_domains d ON d.id = g.domain_id
WHERE p.school_year_id = x.cur_id AND p.status = 'APPROVED'
  AND p.id = (SELECT q.id FROM edu_team_plans q WHERE q.school_year_id = p.school_year_id
              AND q.age_group_id = p.age_group_id AND q.status = 'APPROVED' ORDER BY q.version_no DESC, q.id DESC LIMIT 1);

CREATE TEMP TABLE d06_theme AS
SELECT p.age_group_id, array_agg(t.name ORDER BY t.start_date) AS names
FROM edu_team_plans p CROSS JOIN d06_ctx x JOIN edu_themes t ON t.team_plan_id = p.id
WHERE p.school_year_id = x.cur_id AND p.status = 'APPROVED'
GROUP BY p.age_group_id;

-- Kết quả từng MT của trẻ (năm và học kỳ): Đạt khi ≥ 50% chủ đề có kết quả Đạt
CREATE TEMP TABLE d06_cell AS
SELECT r.child_id, r.old_class, gl.lineage, gl.domain_id, gl.dom_rank, v.per,
       pg_temp.d06_h(r.code || ':' || gl.lineage || ':' || v.per) AS hc
FROM d06_roster r
JOIN d06_goal gl ON gl.age_group_id = r.age_group_id
CROSS JOIN (VALUES ('Y'), ('S1'), ('S2')) AS v (per)
WHERE v.per = 'Y' OR (gl.dom_rank <= 8 AND gl.dom_rank % 2 = CASE v.per WHEN 'S1' THEN 1 ELSE 0 END);

ALTER TABLE d06_cell ADD COLUMN met boolean, ADD COLUMN total int, ADD COLUMN met_n int;
UPDATE d06_cell SET met = hc % 100 >= 9, total = 2 + ((hc / 7) % 3)::int;
UPDATE d06_cell SET met_n = CASE WHEN met THEN total - CASE WHEN total > 2 THEN ((hc / 11) % 2)::int ELSE 0 END
                                 ELSE (total - 1) / 2 END;

-- ---------- 6. Đánh giá học kỳ I, II và cuối năm của trẻ (GV chủ nhiệm xác nhận) ----------
CREATE TEMP TABLE d06_eval AS
SELECT r.*, v.per, v.pstart, v.pend, v.draft_day, v.act_from, v.act_span,
       pg_temp.d06_h(r.code || ':' || v.per || ':2526') AS he
FROM d06_roster r
CROSS JOIN (VALUES ('SEMESTER', 'S1', DATE '2025-09-01', DATE '2026-01-16', DATE '2026-01-17', DATE '2026-01-19', 5),
                   ('SEMESTER', 'S2', DATE '2026-01-19', DATE '2026-05-22', DATE '2026-05-23', DATE '2026-05-25', 4),
                   ('YEAR', 'Y', DATE '2025-09-01', DATE '2026-05-31', DATE '2026-05-23', DATE '2026-05-26', 4))
     AS v (ptype, per, pstart, pend, draft_day, act_from, act_span);
ALTER TABLE d06_eval ADD COLUMN ptype text;
UPDATE d06_eval SET ptype = CASE per WHEN 'Y' THEN 'YEAR' ELSE 'SEMESTER' END;

ALTER TABLE d06_eval ADD COLUMN school_days int, ADD COLUMN present int, ADD COLUMN flags int, ADD COLUMN actor bigint,
    ADD COLUMN confirmed_at timestamptz, ADD COLUMN draft_at timestamptz, ADD COLUMN edited boolean,
    ADD COLUMN program boolean;
UPDATE d06_eval e SET school_days = (
    SELECT count(*) FROM generate_series(e.pstart, e.pend, INTERVAL '1 day') d
    WHERE extract(isodow FROM d) < 6
      AND NOT EXISTS (SELECT 1 FROM school_holidays h CROSS JOIN d06_ctx x
                      WHERE h.school_year_id = x.old_id AND d::date BETWEEN h.start_date AND h.end_date));
UPDATE d06_eval SET present = school_days - (he % (CASE WHEN sort_order = 1 THEN 14 ELSE 9 END))::int,
                    flags = CASE per WHEN 'S1' THEN 9 WHEN 'S2' THEN 9 ELSE 19 END + ((he / 3) % 8)::int,
                    draft_at = pg_temp.d06_at(draft_day, TIME '06:30'),
                    confirmed_at = pg_temp.d06_at(act_from + ((he / 5) % act_span)::int, TIME '13:00')
                                   + ((he / 17) % 90) * INTERVAL '1 minute',
                    edited = (he / 23) % 10 < 7;
UPDATE d06_eval e SET actor = hr.user_id FROM d06_hr hr WHERE hr.old_id = e.old_class;
-- Lớp 5–6 tuổi: hai trẻ chưa hoàn thành chương trình (HT vẫn cho ra trường theo độ tuổi)
UPDATE d06_eval SET program = CASE WHEN age_from >= 60 THEN code NOT IN ('HS25007', 'HS25041') END WHERE per = 'Y';

ALTER TABLE d06_eval ADD COLUMN draft_text text, ADD COLUMN final_text text;
UPDATE d06_eval SET draft_text =
    'Trong ' || CASE WHEN per = 'Y' THEN 'năm học vừa qua' ELSE 'học kỳ vừa qua' END || ', bé ' || given
    || ' đi học ' || present || ' ngày, cô đánh giá hằng ngày ' || (present - (he % 4)::int)
    || ' ngày và bé nhận ' || flags || ' cờ bé ngoan.'
    || E'\nBản nháp tổng hợp cùng ' || CASE per WHEN 'S1' THEN 4 WHEN 'S2' THEN 4 ELSE 9 END
    || ' đánh giá tháng cô đã xác nhận.'
    || E'\nCảm xúc: phần lớn thời gian bé ' || pg_temp.d06_pick(ARRAY['vui vẻ', 'vui vẻ', 'bình thường'], he / 29)
    || '.' || E'\nGợi ý phối hợp với gia đình: khen ngợi khi bé cố gắng.';

UPDATE d06_eval SET final_text =
    CASE per WHEN 'S1' THEN 'Học kỳ I năm học 2025-2026: ' WHEN 'S2' THEN 'Học kỳ II năm học 2025-2026: '
             ELSE 'Năm học 2025-2026: ' END
    || replace(pg_temp.d06_pick(ARRAY[
           '{ten} đi học chuyên cần, đến lớp vui vẻ, lễ phép chào cô, chào bạn.',
           '{ten} đã quen nề nếp lớp, tự cất đồ dùng cá nhân đúng nơi quy định.',
           '{ten} ngoan, biết vâng lời cô, hòa đồng và hay giúp đỡ các bạn.',
           '{ten} mạnh dạn, tự tin hơn nhiều so với đầu năm, thích tham gia các hoạt động tập thể.',
           '{ten} có ý thức tự phục vụ tốt, biết rửa tay trước khi ăn và sau khi đi vệ sinh.',
           '{ten} đi học đều, giữ được nề nếp xếp hàng, chơi đoàn kết với bạn.'], he), '{ten}', given)
    || ' ' || pg_temp.d06_pick(ARRAY[
           'Bé ăn hết suất, ăn được nhiều loại rau, ngủ trưa đủ giấc.',
           'Bé tự xúc ăn gọn gàng, biết mời cô, mời bạn trước khi ăn.',
           'Bé ăn ngoan nhưng còn chậm, cô tiếp tục động viên để bé ăn hết suất.',
           'Bé có sức khỏe tốt, ít nghỉ ốm, tham gia đầy đủ giờ vận động.',
           'Bé còn kén một vài món rau, nhưng đã chịu ăn thử khi cô khuyến khích.'], he / 11)
    || ' ' || CASE
           WHEN age_from < 36 THEN pg_temp.d06_pick(ARRAY[
               'Bé nói được câu ngắn để bày tỏ mong muốn, thích nghe cô đọc thơ, kể chuyện.',
               'Bé nhận biết màu đỏ, vàng, xanh, gọi đúng tên nhiều con vật và đồ dùng quen thuộc.',
               'Bé thích xếp khối, xâu vòng, tham gia vận động theo nhạc cùng cô và các bạn.',
               'Bé biết chỉ và gọi tên các bộ phận trên cơ thể, hát theo cô được nhiều bài.'], he / 13)
           WHEN age_from < 48 THEN pg_temp.d06_pick(ARRAY[
               'Bé thuộc nhiều bài thơ, bài hát theo chủ đề, nhận biết hình tròn, vuông, tam giác.',
               'Bé đếm được trong phạm vi 5, biết so sánh to – nhỏ, cao – thấp.',
               'Bé cầm bút tô màu gọn hơn, thích chơi ở góc phân vai và góc xây dựng.',
               'Bé kể lại được đoạn ngắn câu chuyện quen thuộc, gọi tên các bạn trong lớp.'], he / 13)
           WHEN age_from < 60 THEN pg_temp.d06_pick(ARRAY[
               'Bé tích cực phát biểu, đếm và so sánh số lượng trong phạm vi 8.',
               'Bé vẽ, nặn sáng tạo, kể lại được nội dung câu chuyện theo tranh.',
               'Bé tham gia tốt các trò chơi vận động, biết chơi theo nhóm và chờ đến lượt.',
               'Bé phân biệt được các phương tiện giao thông, biết một số quy định khi đi đường.'], he / 13)
           ELSE pg_temp.d06_pick(ARRAY[
               'Bé nhận biết và phát âm đúng 29 chữ cái, tô chữ đúng nét, ngồi đúng tư thế.',
               'Bé đếm, thêm bớt, tách gộp trong phạm vi 10, mạnh dạn trả lời câu hỏi của cô.',
               'Bé tự tin kể chuyện sáng tạo theo tranh, biết giúp cô chuẩn bị đồ dùng học tập.',
               'Bé cầm bút đúng cách, chủ động hoàn thành nhiệm vụ cô giao, sẵn sàng vào lớp 1.'], he / 13) END
    || coalesce(' ' || nullif(pg_temp.d06_pick(ARRAY[
           'Bé cần mạnh dạn hơn khi trả lời câu hỏi trước lớp.',
           'Bé cần tập trung hơn trong giờ học, tránh nói chuyện riêng.',
           'Bé cần nhường nhịn bạn hơn khi chơi đồ chơi chung.',
           'Bé cần tự giác cất đồ chơi sau khi chơi.',
           '', ''], he / 17), ''), '')
    || CASE WHEN per = 'Y' AND program THEN ' Bé hoàn thành chương trình giáo dục mầm non.'
            WHEN per = 'Y' AND program = false
                THEN ' Bé chưa hoàn thành một số mục tiêu lĩnh vực ngôn ngữ, gia đình cần ôn thêm cùng bé trong hè.'
            ELSE '' END
    || ' ' || pg_temp.d06_pick(ARRAY[
           'Gia đình cho bé đi ngủ sớm để sáng dậy đúng giờ đến lớp.',
           'Gia đình trò chuyện, hỏi bé về các hoạt động ở lớp mỗi ngày.',
           'Gia đình khuyến khích bé tự xúc ăn, tự mặc quần áo ở nhà.',
           'Gia đình cùng bé đọc sách tranh, kể chuyện trước giờ ngủ.'], he / 19);

-- Phiếu cuối năm (OBS-28): danh sách MT đã chốt (g dòng gốc, m đạt, x GV sửa tay, n số, t câu MT, d/dn lĩnh vực,
-- s nguồn), nhận xét theo lĩnh vực và 4 lưu ý
CREATE TEMP TABLE d06_sheet AS
SELECT c.child_id, c.old_class,
       jsonb_agg(jsonb_build_object(
           'g', c.lineage, 'm', c.met, 'x', c.hc % 50 = 0, 'n', gl.goal_no, 't', gl.text, 'd', gl.domain_id,
           'dn', gl.domain_name,
           's', CASE WHEN c.hc % 50 = 0 THEN NULL
                     ELSE c.met_n || '/' || c.total || ' chủ đề đạt: '
                          || array_to_string(th.names[1 + ((c.hc / 13) % greatest(1, array_length(th.names, 1) - c.total + 1))::int
                                                     : ((c.hc / 13) % greatest(1, array_length(th.names, 1) - c.total + 1))::int + c.total], ', ') END)
           ORDER BY gl.ord)::text AS goals
FROM d06_cell c
JOIN d06_roster r ON r.child_id = c.child_id AND r.old_class = c.old_class
JOIN d06_goal gl ON gl.age_group_id = r.age_group_id AND gl.lineage = c.lineage
LEFT JOIN d06_theme th ON th.age_group_id = r.age_group_id
WHERE c.per = 'Y'
GROUP BY c.child_id, c.old_class;

CREATE TEMP TABLE d06_dcom AS
SELECT r.child_id, r.old_class,
       jsonb_object_agg(d.domain_id::text, replace(CASE d.dom_sort
           WHEN 1 THEN pg_temp.d06_pick(ARRAY['{ten} khỏe mạnh, vận động nhanh nhẹn, khéo léo khi đi, chạy, bật nhảy.',
                                              '{ten} có thói quen vệ sinh tốt, phối hợp tay – mắt khéo léo.',
                                              '{ten} tham gia vận động tích cực, cần luyện thêm các bài tập tung, bắt bóng.'], r.h)
           WHEN 2 THEN pg_temp.d06_pick(ARRAY['{ten} ham hiểu biết, hay đặt câu hỏi về thế giới xung quanh.',
                                              '{ten} nhận biết, phân loại đồ vật theo dấu hiệu nhanh, đếm đúng số lượng.',
                                              '{ten} chú ý quan sát, nhớ được các đặc điểm nổi bật của sự vật.'], r.h / 7)
           WHEN 3 THEN pg_temp.d06_pick(ARRAY['{ten} diễn đạt rõ ràng, mạnh dạn kể chuyện, đọc thơ trước lớp.',
                                              '{ten} hiểu và làm theo được yêu cầu nhiều bước của cô.',
                                              '{ten} vốn từ phong phú hơn, cần nói chậm và rõ lời hơn.'], r.h / 11)
           WHEN 4 THEN pg_temp.d06_pick(ARRAY['{ten} hòa đồng, biết chia sẻ đồ chơi và giúp đỡ bạn.',
                                              '{ten} lễ phép, biết cảm ơn, xin lỗi đúng lúc.',
                                              '{ten} còn rụt rè với người lạ nhưng gắn bó, tin tưởng cô và bạn.'], r.h / 13)
           ELSE pg_temp.d06_pick(ARRAY['{ten} yêu thích hát múa, vỗ tay đúng nhịp, vẽ tranh màu sắc tươi sáng.',
                                       '{ten} thích làm đồ chơi từ nguyên liệu thiên nhiên, sản phẩm gọn gàng.',
                                       '{ten} cảm nhận âm nhạc tốt, mạnh dạn biểu diễn trước các bạn.'], r.h / 17) END,
           '{ten}', r.given))::text AS comments
FROM d06_roster r
JOIN (SELECT DISTINCT age_group_id, domain_id, dom_sort FROM d06_goal WHERE domain_id IS NOT NULL) d
  ON d.age_group_id = r.age_group_id
GROUP BY r.child_id, r.old_class;

CREATE TEMP TABLE d06_eval_new (id bigint, child_id bigint, per text);

WITH ins AS (
    INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type, period_start,
                                   period_end, status, draft_content, draft_at, final_content, program_completed,
                                   confirmed_by, confirmed_at, created_at, updated_at, goal_results, note_for_child,
                                   note_for_teacher, note_for_school, note_for_parent, domain_comments)
    SELECT e.child_id, e.old_class, e.campus_id, x.old_id, e.ptype, e.pstart, e.pend, 'CONFIRMED', e.draft_text,
           e.draft_at, e.final_text, e.program, e.actor, e.confirmed_at, e.draft_at, e.confirmed_at,
           CASE WHEN e.per = 'Y' THEN coalesce(s.goals, '[]') END,
           CASE WHEN e.per = 'Y' THEN pg_temp.d06_pick(ARRAY[
               'Con nhớ giữ thói quen tự xúc ăn, tự cất đồ chơi khi lên lớp mới nhé.',
               'Con mạnh dạn giơ tay phát biểu như cô vẫn khen con nhé.',
               'Con nhớ rửa tay sạch trước khi ăn và chơi đoàn kết với các bạn.',
               'Con hãy chăm đọc sách tranh cùng bố mẹ trong dịp hè.'], e.he / 31) END,
           CASE WHEN e.per = 'Y' THEN CASE WHEN e.sort_order = e.max_sort THEN pg_temp.d06_pick(ARRAY[
               'Bé sẵn sàng vào lớp 1: cầm bút đúng, nhận biết đủ chữ cái và chữ số, cần rèn thêm tốc độ tô chữ.',
               'Bé tự tin, nề nếp tốt; thầy cô lớp 1 nên giao việc nhỏ để bé phát huy tính tự giác.',
               'Bé hiếu động, cần nhắc nhở tập trung trong giờ học đầu năm lớp 1.'], e.he / 37)
               ELSE pg_temp.d06_pick(ARRAY[
               'Bé còn rụt rè trong những ngày đầu, cô lớp mới nên gần gũi, động viên bé.',
               'Bé ăn chậm, cô lớp mới tiếp tục động viên bé ăn hết suất.',
               'Bé tiếp thu nhanh, có thể giao thêm nhiệm vụ trực nhật, chia đồ dùng.',
               'Bé hay quên đồ dùng cá nhân, cô lớp mới nhắc bé cất đúng chỗ.'], e.he / 37) END END,
           CASE WHEN e.per = 'Y' THEN pg_temp.d06_pick(ARRAY[
               'Đề nghị nhà trường tiếp tục duy trì bữa phụ chiều giàu rau quả.',
               'Gia đình mong nhà trường tổ chức thêm hoạt động ngoài trời cho trẻ.',
               'Không có đề xuất thêm.'], e.he / 41) END,
           CASE WHEN e.per = 'Y' THEN pg_temp.d06_pick(ARRAY[
               'Gia đình duy trì giờ ngủ, giờ ăn điều độ trong dịp hè.',
               'Gia đình cùng bé đọc sách, kể chuyện mỗi tối để bé thêm vốn từ.',
               'Gia đình cho bé tham gia vận động ngoài trời, hạn chế xem điện thoại, ti vi.',
               'Gia đình khen ngợi kịp thời khi bé tự làm được việc của mình.'], e.he / 43) END,
           CASE WHEN e.per = 'Y' THEN dc.comments END
    FROM d06_eval e
    CROSS JOIN d06_ctx x
    LEFT JOIN d06_sheet s ON s.child_id = e.child_id AND s.old_class = e.old_class
    LEFT JOIN d06_dcom dc ON dc.child_id = e.child_id AND dc.old_class = e.old_class
    WHERE e.actor IS NOT NULL
    ON CONFLICT ON CONSTRAINT uk_child_evaluations_child_period DO NOTHING
    RETURNING id, child_id, period_type, period_start
)
INSERT INTO d06_eval_new
SELECT i.id, i.child_id,
       CASE WHEN i.period_type = 'YEAR' THEN 'Y' WHEN i.period_start = DATE '2025-09-01' THEN 'S1' ELSE 'S2' END
FROM ins i;

-- Lịch sử: hệ thống tạo nháp, GV sửa (phần lớn), GV xác nhận — chỉ cho bản vừa tạo
INSERT INTO child_evaluation_events (evaluation_id, action, user_id, at)
SELECT n.id, v.action, v.user_id, v.at
FROM d06_eval_new n
JOIN d06_eval e ON e.child_id = n.child_id AND e.per = n.per
CROSS JOIN LATERAL (
    VALUES ('AI_DRAFTED', NULL::bigint, e.draft_at, TRUE),
           ('EDITED', e.actor, e.confirmed_at - INTERVAL '25 minutes', e.edited),
           ('CONFIRMED', e.actor, e.confirmed_at, TRUE)
) v (action, user_id, at, wanted)
WHERE v.wanted;

-- ---------- 7. Đánh giá học kỳ của lớp (bản chốt lưu kết quả MT từng trẻ, như SemesterAssessmentDtos.Response) ----
CREATE TEMP TABLE d06_sgoal AS
SELECT r.old_class, c.per, r.child_id, r.full_name, gl.domain_id, gl.domain_name, gl.dom_sort,
       jsonb_agg(jsonb_build_object('goalId', c.lineage, 'goalNo', gl.goal_no, 'text', gl.text, 'met', c.met,
                                    'metActivities', c.met_n, 'assessedActivities', c.total) ORDER BY gl.ord) AS goals,
       count(*) FILTER (WHERE c.met)::int AS met_count, count(*) FILTER (WHERE NOT c.met)::int AS not_met
FROM d06_cell c
JOIN d06_roster r ON r.child_id = c.child_id AND r.old_class = c.old_class
JOIN d06_goal gl ON gl.age_group_id = r.age_group_id AND gl.lineage = c.lineage
WHERE c.per IN ('S1', 'S2')
GROUP BY r.old_class, c.per, r.child_id, r.full_name, gl.domain_id, gl.domain_name, gl.dom_sort;

CREATE TEMP TABLE d06_sem AS
SELECT c.old_id, c.campus_id, c.name, c.k, c.age_group_id, v.per, v.no, v.pstart, v.pend, v.conf_day,
       hr.user_id AS hr_id
FROM d06_cls c
JOIN d06_hr hr ON hr.old_id = c.old_id
CROSS JOIN (VALUES ('S1', 1, DATE '2025-09-01', DATE '2026-01-16', DATE '2026-01-21'),
                   ('S2', 2, DATE '2026-01-19', DATE '2026-05-22', DATE '2026-05-27')) AS v (per, no, pstart, pend, conf_day);

INSERT INTO semester_assessments (classroom_id, campus_id, school_year_id, semester_no, period_start, period_end, note,
                                  result_snapshot, confirmed_by, confirmed_at)
SELECT s.old_id, s.campus_id, x.old_id, s.no, s.pstart, s.pend, s.note,
       jsonb_build_object(
           'classId', s.old_id, 'className', s.name, 'schoolYearId', x.old_id, 'yearName', '2025-2026',
           'semester', s.no, 'startDate', s.pstart, 'endDate', s.pend, 'status', 'CONFIRMED', 'note', s.note,
           'domains', coalesce((SELECT jsonb_agg(jsonb_build_object('domainId', d.domain_id, 'name', d.domain_name,
                                                                     'goals', '[]'::jsonb, 'metCount', 0,
                                                                     'notMetCount', 0, 'missingCount', 0,
                                                                     'met', NULL) ORDER BY d.dom_sort)
                                FROM (SELECT DISTINCT domain_id, domain_name, dom_sort FROM d06_sgoal g
                                      WHERE g.old_class = s.old_id AND g.per = s.per) d), '[]'::jsonb),
           'rows', coalesce((SELECT jsonb_agg(ch.obj ORDER BY ch.full_name, ch.child_id)
                             FROM (SELECT g.child_id, g.full_name,
                                          jsonb_build_object('childId', g.child_id, 'fullName', g.full_name,
                                              'domains', jsonb_agg(jsonb_build_object(
                                                  'domainId', g.domain_id, 'name', g.domain_name, 'goals', g.goals,
                                                  'metCount', g.met_count, 'notMetCount', g.not_met,
                                                  'missingCount', 0, 'met', g.met_count * 2 >= g.met_count + g.not_met)
                                                  ORDER BY g.dom_sort)) AS obj
                                   FROM d06_sgoal g WHERE g.old_class = s.old_id AND g.per = s.per
                                   GROUP BY g.child_id, g.full_name) ch), '[]'::jsonb),
           'canEdit', false, 'canConfirm', false,
           'confirmedAt', to_char(s.conf_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'confirmedByName', u.full_name,
           'signers', jsonb_build_object(
               'teachers', jsonb_build_array(u.full_name),
               'teamLeaders', coalesce((SELECT jsonb_agg(tu.full_name ORDER BY tu.full_name)
                                        FROM staff_assignments ta JOIN user_accounts tu ON tu.id = ta.user_id
                                        WHERE ta.staff_role = 'AGE_GROUP_TEAM_LEADER' AND ta.school_year_id = x.old_id
                                          AND ta.age_group_id = s.age_group_id), '[]'::jsonb),
               'vicePrincipal', vp.full_name))::text,
       s.hr_id, s.conf_at
FROM (SELECT d.*,
             pg_temp.d06_at(d.conf_day + (d.k % 2), TIME '15:30') + (d.k * 7) * INTERVAL '1 minute' AS conf_at,
             CASE d.per
                 WHEN 'S1' THEN pg_temp.d06_pick(ARRAY[
                     'Lớp hoàn thành mục tiêu học kỳ I; một số trẻ cần rèn thêm kỹ năng tự phục vụ và ngôn ngữ.',
                     'Đa số trẻ đạt mục tiêu các lĩnh vực; lớp tiếp tục chú ý trẻ còn rụt rè, ít giao tiếp.',
                     'Trẻ thích nghi tốt với nề nếp lớp; học kỳ II tăng cường hoạt động trải nghiệm ngoài trời.'],
                     pg_temp.d06_h(d.name || ':nsa1'))
                 ELSE pg_temp.d06_pick(ARRAY[
                     'Lớp hoàn thành tốt mục tiêu năm học; trẻ tiến bộ rõ ở lĩnh vực ngôn ngữ và thẩm mỹ.',
                     'Trẻ đạt phần lớn mục tiêu học kỳ II; cô phối hợp gia đình ôn luyện trong dịp hè.',
                     'Học kỳ II trẻ mạnh dạn, tự tin hơn; vài trẻ cần tiếp tục rèn vận động tinh.'],
                     pg_temp.d06_h(d.name || ':nsa2')) END AS note
      FROM d06_sem d) s
CROSS JOIN d06_ctx x
JOIN user_accounts u ON u.id = s.hr_id
LEFT JOIN d06_vp vp ON vp.campus_id = s.campus_id
ON CONFLICT ON CONSTRAINT uk_semester_assessment_class_period DO NOTHING;

-- ---------- 8. Xếp loại nhóm lớp (OBS-24): HK I, HK II do PHT cơ sở chấm, cuối năm do HT chấm ----------
CREATE TEMP TABLE d06_rating AS
SELECT c.old_id, c.campus_id, c.name, c.k, v.period, v.rate_day, v.by_principal,
       ARRAY(SELECT CASE WHEN pg_temp.d06_h(c.name || ':' || v.period || ':' || i) % 100 < 30 THEN 'XUAT_SAC'
                         WHEN pg_temp.d06_h(c.name || ':' || v.period || ':' || i) % 100 < 85 THEN 'TOT'
                         ELSE 'KHA' END
             FROM generate_series(1, 7) i ORDER BY i) AS lv
FROM d06_cls c
CROSS JOIN (VALUES ('SEMESTER_1', DATE '2026-01-19', false), ('SEMESTER_2', DATE '2026-05-25', false),
                   ('YEAR', DATE '2026-05-28', true)) AS v (period, rate_day, by_principal);

INSERT INTO class_ratings (classroom_id, campus_id, school_year_id, period, records, program, theme, environment,
                           routine, health, parents, overall, note, rated_by, rated_at)
SELECT r.old_id, r.campus_id, x.old_id, r.period, r.lv[1], r.lv[2], r.lv[3], r.lv[4], r.lv[5], r.lv[6], r.lv[7],
       CASE WHEN (SELECT count(*) FROM unnest(r.lv) z WHERE z = 'XUAT_SAC') >= 3 THEN 'XUAT_SAC'
            WHEN (SELECT count(*) FROM unnest(r.lv) z WHERE z IN ('XUAT_SAC', 'TOT')) >= 4 THEN 'TOT'
            ELSE 'KHA' END,
       pg_temp.d06_pick(ARRAY['Lớp thực hiện tốt chương trình, hồ sơ sổ sách đầy đủ, khoa học.',
                              'Môi trường lớp sáng tạo, trẻ nề nếp; cần cập nhật sổ theo dõi trẻ kịp thời hơn.',
                              'Giáo viên phối hợp tốt với phụ huynh, tỷ lệ chuyên cần cao.',
                              'Thực hiện chủ đề sinh động; góc chơi cần thay đổi đồ dùng thường xuyên hơn.'],
                        pg_temp.d06_h(r.name || ':' || r.period || ':note')),
       CASE WHEN r.by_principal OR vp.user_id IS NULL THEN x.principal_id ELSE vp.user_id END,
       pg_temp.d06_at(r.rate_day + (r.k % 2), TIME '10:00') + (r.k * 11) * INTERVAL '1 minute'
FROM d06_rating r
CROSS JOIN d06_ctx x
LEFT JOIN d06_vp vp ON vp.campus_id = r.campus_id
ON CONFLICT ON CONSTRAINT uk_class_ratings_class_period DO NOTHING;

-- ---------- 9. Khen thưởng cuối năm (OBS-14): GV đề xuất → PHT xét → HT duyệt ----------
-- Lớp chưa xong luồng khi năm đóng: Lá 2 chờ HT duyệt, Nhà trẻ 2 còn nháp, Mầm 2 bị PHT trả lại, Chồi 2 chờ PHT xét.
-- Mầm 1 từng bị trả lại rồi gửi lại được duyệt; Chồi 1 HT duyệt thẳng khi PHT chưa xét.
CREATE TEMP TABLE d06_rw AS
SELECT c.old_id, c.campus_id, c.campus_code, c.name, c.k, c.sort_order, c.max_sort, hr.user_id AS teacher_id,
       vp.user_id AS vp_id, x.principal_id,
       CASE c.name WHEN 'Lớp Lá 2' THEN 'PENDING_PRINCIPAL' WHEN 'Lớp Nhà trẻ 2' THEN 'DRAFT'
                   WHEN 'Lớp Mầm 2' THEN 'RETURNED' WHEN 'Lớp Chồi 2' THEN 'PENDING_VP'
                   WHEN 'Lớp Mầm 1' THEN 'RESUBMITTED' WHEN 'Lớp Chồi 1' THEN 'DIRECT' ELSE 'APPROVED' END AS flow
FROM d06_cls c
JOIN d06_hr hr ON hr.old_id = c.old_id
CROSS JOIN d06_ctx x
LEFT JOIN d06_vp vp ON vp.campus_id = c.campus_id;

-- Mốc sự kiện theo luồng (giờ hành chính, cuối tháng 5/2026)
CREATE TEMP TABLE d06_rw_ev AS
SELECT w.old_id, v.n, v.action, pg_temp.d06_at(v.d, v.t) + (w.k * 6) * INTERVAL '1 minute' AS at, v.note,
       CASE v.who WHEN 'T' THEN w.teacher_id WHEN 'V' THEN coalesce(w.vp_id, w.principal_id) ELSE w.principal_id END
           AS user_id
FROM d06_rw w
CROSS JOIN LATERAL (VALUES
    (1, 'CREATED', 'T', DATE '2026-05-22', TIME '15:40', NULL::text, true),
    (2, 'UPDATED', 'T', DATE '2026-05-25', TIME '10:15', NULL, w.flow IN ('DRAFT', 'RESUBMITTED', 'RETURNED')),
    (3, 'SUBMITTED', 'T', CASE WHEN w.flow = 'PENDING_VP' THEN DATE '2026-05-28' ELSE DATE '2026-05-26' END,
        TIME '09:20', NULL, w.flow <> 'DRAFT'),
    (4, 'RETURNED', 'V', DATE '2026-05-27', TIME '14:10',
        'Đề nghị cô rà soát lại danh hiệu "Bé chăm ngoan" của các bé nghỉ học nhiều trong học kỳ II.',
        w.flow IN ('RETURNED', 'RESUBMITTED')),
    (5, 'RESUBMITTED', 'T', DATE '2026-05-28', TIME '08:40', NULL, w.flow = 'RESUBMITTED'),
    (6, 'FORWARDED', 'V', DATE '2026-05-28', TIME '14:30', NULL,
        w.flow IN ('APPROVED', 'RESUBMITTED', 'PENDING_PRINCIPAL')),
    (7, 'APPROVED', 'P', DATE '2026-05-29', TIME '10:30', NULL, w.flow IN ('APPROVED', 'RESUBMITTED')),
    (8, 'DIRECT_APPROVED', 'P', DATE '2026-05-28', TIME '16:00',
        'Hiệu trưởng duyệt trực tiếp do Phó Hiệu trưởng đi tập huấn.', w.flow = 'DIRECT')
) v (n, action, who, d, t, note, wanted)
WHERE v.wanted;

CREATE TEMP TABLE d06_rw_new (id bigint, old_id bigint);

WITH ins AS (
    INSERT INTO reward_lists (classroom_id, campus_id, school_year_id, status, created_by, updated_by, submitted_at,
                              review_by, review_at, review_decision, review_comment, decision_by, decision_at,
                              decision, decision_comment, created_at, updated_at)
    SELECT w.old_id, w.campus_id, x.old_id,
           CASE w.flow WHEN 'RESUBMITTED' THEN 'APPROVED' WHEN 'DIRECT' THEN 'APPROVED' ELSE w.flow END,
           w.teacher_id, w.teacher_id,
           (SELECT max(e.at) FROM d06_rw_ev e WHERE e.old_id = w.old_id AND e.action IN ('SUBMITTED', 'RESUBMITTED')),
           rv.user_id, rv.at, CASE rv.action WHEN 'FORWARDED' THEN 'FORWARD' WHEN 'RETURNED' THEN 'RETURN' END, rv.note,
           dc.user_id, dc.at, CASE WHEN dc.action IS NOT NULL THEN 'APPROVE' END, dc.note,
           (SELECT min(e.at) FROM d06_rw_ev e WHERE e.old_id = w.old_id),
           (SELECT max(e.at) FROM d06_rw_ev e WHERE e.old_id = w.old_id)
    FROM d06_rw w
    CROSS JOIN d06_ctx x
    LEFT JOIN LATERAL (SELECT e.* FROM d06_rw_ev e WHERE e.old_id = w.old_id AND e.action IN ('FORWARDED', 'RETURNED')
                       ORDER BY e.at DESC LIMIT 1) rv ON true
    LEFT JOIN LATERAL (SELECT e.* FROM d06_rw_ev e WHERE e.old_id = w.old_id
                         AND e.action IN ('APPROVED', 'DIRECT_APPROVED') LIMIT 1) dc ON true
    ON CONFLICT ON CONSTRAINT uk_reward_lists_classroom DO NOTHING
    RETURNING id, classroom_id
)
INSERT INTO d06_rw_new SELECT id, classroom_id FROM ins;

INSERT INTO reward_list_events (list_id, action, user_id, at, note)
SELECT n.id, e.action, e.user_id, e.at, e.note
FROM d06_rw_new n JOIN d06_rw_ev e ON e.old_id = n.old_id
ORDER BY n.id, e.at;

-- Danh hiệu: một danh hiệu bé ngoan; thêm Cháu ngoan Bác Hồ (~1/5 trẻ); lớp 5–6 tuổi thêm danh hiệu hoàn thành
-- chương trình cho trẻ đã hoàn thành (thứ tự như RewardListServiceImpl.TITLES)
INSERT INTO reward_list_items (list_id, child_id, titles, note)
SELECT n.id, r.child_id,
       concat_ws('|',
           CASE WHEN r.h % 100 < 30 THEN 'Bé ngoan' WHEN r.h % 100 < 60 THEN 'Bé chăm ngoan'
                WHEN r.h % 100 < 85 THEN 'Bé khỏe, bé ngoan' ELSE 'Bé chăm ngoan xuất sắc' END,
           CASE WHEN (r.h / 7) % 100 < 20 THEN 'Cháu ngoan Bác Hồ' END,
           CASE WHEN y.program_completed IS TRUE
                THEN CASE WHEN (r.h / 11) % 100 < 35 THEN 'Hoàn thành xuất sắc chương trình giáo dục mầm non'
                          ELSE 'Hoàn thành tốt chương trình giáo dục mầm non' END END),
       CASE WHEN (r.h / 13) % 100 < 12 THEN pg_temp.d06_pick(ARRAY[
           'Bé tiến bộ vượt bậc trong học kỳ II.',
           'Bé đạt giải Nhì hội thi "Bé khỏe – Bé ngoan" cấp trường.',
           'Bé đi học chuyên cần cả năm, không nghỉ buổi nào.'], r.h / 17) END
FROM d06_rw_new n
JOIN d06_roster r ON r.old_class = n.old_id
LEFT JOIN child_evaluations y ON y.child_id = r.child_id AND y.classroom_id = r.old_class AND y.period_type = 'YEAR'
-- Vài trẻ nghỉ học nhiều không được đề xuất
WHERE (r.h / 19) % 100 >= 5
ON CONFLICT ON CONSTRAINT uk_reward_list_items_child DO NOTHING;

-- ---------- 10. Xếp lớp năm 2027-2028 đang làm dở ----------
-- Cơ sở 1: đã xếp xong Nhà trẻ, Mầm, Chồi; lớp Lá 2 có vài trẻ ra trường (HT chọn) và 1 trẻ ở lại; Lá 1 chưa xếp.
-- Cơ sở 2: mới xếp một nửa lớp Nhà trẻ 2, 1 trẻ Mầm 2 nghỉ học (gia đình báo chuyển đi); còn lại chưa xếp.
CREATE TEMP TABLE d06_next AS
SELECT e.child_id, ch.code, c.cur_id, c.campus_code, c.name, c.sort_order, c.max_sort, c.k, c.campus_id,
       pg_temp.d06_h(ch.code || ':xl2728') AS h,
       row_number() OVER (PARTITION BY c.cur_id ORDER BY ch.full_name, ch.id)::int AS rn
FROM enrollments e
JOIN children ch ON ch.id = e.child_id
JOIN d06_cls c ON c.cur_id = e.classroom_id
WHERE e.valid_to IS NULL AND ch.status = 'ACTIVE';

CREATE TEMP TABLE d06_next_pl AS
SELECT nx.child_id, nx.cur_id, nx.k, nx.h,
       CASE WHEN nx.campus_code = 'CS-A' AND nx.sort_order < nx.max_sort THEN 'PROMOTE'
            WHEN nx.campus_code = 'CS-A' AND nx.name = 'Lớp Lá 2' AND nx.rn <= 3 THEN 'GRADUATE'
            WHEN nx.campus_code = 'CS-A' AND nx.name = 'Lớp Lá 2' AND nx.rn = 4 THEN 'STAY'
            WHEN nx.campus_code = 'CS-B' AND nx.sort_order = 1 AND nx.rn <= 10 THEN 'PROMOTE'
            WHEN nx.campus_code = 'CS-B' AND nx.sort_order = 2 AND nx.rn = 5 THEN 'LEAVE' END AS action,
       -- Lên lớp: lớp nhóm tuổi kế tiếp cùng cơ sở (2 lớp Lá thì chia theo hash); ở lại: lớp cùng tên năm mới
       CASE WHEN nx.campus_code = 'CS-A' AND nx.name = 'Lớp Lá 2' AND nx.rn = 4
                THEN (SELECT c.next_id FROM d06_cls c WHERE c.cur_id = nx.cur_id)
            ELSE (SELECT t.next_id FROM d06_cls t WHERE t.campus_id = nx.campus_id AND t.sort_order = nx.sort_order + 1
                  ORDER BY (pg_temp.d06_h(nx.code || ':' || t.name) % 100), t.name LIMIT 1) END AS to_id
FROM d06_next nx;

INSERT INTO class_placements (school_year_id, child_id, from_classroom_id, action, to_classroom_id, updated_by,
                              created_at, updated_at)
SELECT x.next_id, p.child_id, p.cur_id, p.action, CASE WHEN p.action IN ('PROMOTE', 'STAY') THEN p.to_id END,
       x.principal_id,
       LEAST(pg_temp.d06_at(DATE '2026-11-10' + (p.k % 3), TIME '14:00') + (p.h % 120) * INTERVAL '1 minute',
             now() - INTERVAL '1 hour'),
       LEAST(pg_temp.d06_at(DATE '2026-11-10' + (p.k % 3), TIME '14:00') + (p.h % 120) * INTERVAL '1 minute',
             now() - INTERVAL '1 hour')
FROM d06_next_pl p CROSS JOIN d06_ctx x
WHERE p.action IS NOT NULL AND (p.action IN ('LEAVE', 'GRADUATE') OR p.to_id IS NOT NULL)
ON CONFLICT ON CONSTRAINT uk_class_placements_school_year_child DO NOTHING;

-- ---------- Dọn ----------
DROP TABLE d06_principal, d06_ctx, d06_cls, d06_staff, d06_hr, d06_vp, d06_cont, d06_new, d06_roster, d06_goal,
    d06_theme, d06_cell, d06_eval, d06_sheet, d06_dcom, d06_eval_new, d06_sgoal, d06_sem, d06_rating, d06_rw,
    d06_rw_ev, d06_rw_new, d06_next, d06_next_pl;
DROP FUNCTION pg_temp.d06_h(text), pg_temp.d06_at(date, time), pg_temp.d06_pick(text[], bigint);
