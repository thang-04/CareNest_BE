-- CHỈ DÙNG TRÊN DB DEV: bổ sung data để test 3 phần — Cấu hình trường, Điểm danh & suất ăn, Đánh giá trẻ.
-- Nạp sau R__dev_seed (đã có điểm trường, lớp, trẻ, phân công GV, điểm danh, suất ăn, quan sát hằng ngày).
-- Bổ sung: kế hoạch giáo dục năm (mục tiêu + chủ đề), phiếu đánh giá trẻ (tháng/học kỳ/năm), kỳ cân đo.
-- Idempotent: chạy lại nhiều lần không nhân bản.
BEGIN;

-- ============ 1. CẤU HÌNH TRƯỜNG: liên kết năm học – điểm trường – nhóm tuổi – phòng ============
-- (bảng school_year_campuses / school_year_rooms / school_year_age_groups đang trống)
INSERT INTO school_year_campuses (school_year_id, campus_id, code, name, short_name, address, phone, is_main)
SELECT sy.id, c.id, c.code, c.name, c.short_name, c.address, NULL, (c.id = 1)
FROM school_years sy CROSS JOIN campuses c
WHERE NOT EXISTS (
  SELECT 1 FROM school_year_campuses x WHERE x.school_year_id = sy.id AND x.campus_id = c.id);

INSERT INTO school_year_rooms (school_year_id, room_id, campus_id, code, name, room_type, capacity, status)
SELECT sy.id, r.id, r.campus_id, r.code, r.name, r.room_type, r.capacity, 'ACTIVE'
FROM school_years sy CROSS JOIN rooms r
WHERE NOT EXISTS (
  SELECT 1 FROM school_year_rooms x WHERE x.school_year_id = sy.id AND x.room_id = r.id);

INSERT INTO school_year_age_groups (school_year_id, age_group_id, code, name, short_name,
                                    sort_order, age_from_months, age_to_months, meal_sessions,
                                    max_class_size, nutrition_note)
SELECT sy.id, ag.id, ag.code, ag.name, ag.code,
       ag.id, 24 + (ag.id - 1) * 12, 35 + (ag.id - 1) * 12,
       'BREAKFAST,LUNCH,SNACK', 25, ''
FROM school_years sy CROSS JOIN age_groups ag
WHERE NOT EXISTS (
  SELECT 1 FROM school_year_age_groups x
  WHERE x.school_year_id = sy.id AND x.age_group_id = ag.id);

-- ============ 2. ĐÁNH GIÁ TRẺ: kế hoạch giáo dục năm (mục tiêu + chủ đề) ============
-- Một kế hoạch APPROVED cho mỗi (năm học, nhóm tuổi) để màn Cấu hình trường có dữ liệu gửi duyệt.
INSERT INTO edu_year_plans (code, school_year_id, age_group_id, title, description, status, owner_id,
                            owner_title, sent_by, sent_at, issued_by, issued_at, created_at, updated_at)
SELECT 'KHGD-' || sy.id || '-' || ag.id, sy.id, ag.id,
       'Kế hoạch giáo dục năm học ' || sy.name || ' – ' || ag.name,
       'Kế hoạch giáo dục mẫu cho nhóm tuổi ' || ag.name || ' (dữ liệu dev để test)',
       'APPROVED',
       vp.id, 'Phó hiệu trưởng', vp.id, now(), pr.id, now(), now(), now()
FROM school_years sy CROSS JOIN age_groups ag
CROSS JOIN LATERAL (SELECT id FROM user_accounts WHERE username = 'viceprincipal' LIMIT 1) vp
CROSS JOIN LATERAL (SELECT id FROM user_accounts WHERE username = 'principal' LIMIT 1) pr
WHERE NOT EXISTS (
  SELECT 1 FROM edu_year_plans p WHERE p.school_year_id = sy.id AND p.age_group_id = ag.id);

-- Mục tiêu theo lĩnh vực cho mỗi kế hoạch (2 mục tiêu / lĩnh vực, đủ 5 lĩnh vực)
INSERT INTO edu_plan_goals (year_plan_id, domain_id, sort_order, text, goal_no)
SELECT p.id, d.id, (d.sort_order * 10) + g.n, d.name || ' – Mục tiêu ' || g.n, g.n
FROM edu_year_plans p
CROSS JOIN edu_domains d
CROSS JOIN (VALUES (1), (2)) AS g(n)
WHERE p.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM edu_plan_goals gg WHERE gg.year_plan_id = p.id);

-- Chủ đề (theme) cho mỗi kế hoạch: 4 chủ đề/kỳ, kỳ I = tháng 9–12, kỳ II = tháng 1–5
INSERT INTO edu_themes (year_plan_id, sort_order, name, abbr, start_date, end_date, festivals, note)
SELECT p.id, t.n, t.name, t.abbr, t.start_date, t.end_date, '', 'Chủ đề mẫu (dữ liệu dev)'
FROM edu_year_plans p
CROSS JOIN (VALUES
  (1, 'Chủ đề 1: Tôi và gia đình',          'CD1', DATE '2026-09-01', DATE '2026-10-15'),
  (2, 'Chủ đề 2: Bé khỏe và vui',            'CD2', DATE '2026-10-16', DATE '2026-11-30'),
  (3, 'Chủ đề 3: Bé yêu thương và sẻ chia sẻ', 'CD3', DATE '2026-12-01', DATE '2027-01-15'),
  (4, 'Chủ đề 4: Bé khám phá thế giới',      'CD4', DATE '2027-01-16', DATE '2027-05-31')
) AS t(n, name, abbr, start_date, end_date)
WHERE p.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM edu_themes th WHERE th.year_plan_id = p.id);

-- ============ 3. ĐÁNH GIÁ TRẺ: phiếu tháng / học kỳ / năm ============
-- Phiếu tháng (MONTH): 1 phiếu/trẻ/lớp, trạng thái chờ duyệt (AWAITING_REVIEW) để sửa và gửi duyệt được.
INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type,
                               period_start, period_end, status, draft_content, draft_at,
                               created_at, updated_at, goal_results)
SELECT ch.id, e.classroom_id, c.campus_id, c.school_year_id, 'MONTH',
       DATE '2026-10-01', DATE '2026-10-31', 'AWAITING_REVIEW',
       'Nhận xét tháng 10/2026 (dữ liệu dev)', now(), now(), now(),
       '[]'::text
FROM enrollments e
JOIN children ch ON ch.id = e.child_id
JOIN classrooms c ON c.id = e.classroom_id
WHERE c.school_year_id = 1
  AND e.valid_from <= DATE '2026-10-31' AND (e.valid_to IS NULL OR e.valid_to >= DATE '2026-10-01')
  AND NOT EXISTS (
    SELECT 1 FROM child_evaluations ce
    WHERE ce.child_id = ch.id AND ce.classroom_id = e.classroom_id AND ce.period_type = 'MONTH');

-- Phiếu học kỳ (SEMESTER): kỳ I đã chốt (CONFIRMED), kỳ II để trống để test luồng gửi duyệt.
INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type,
                               period_start, period_end, status, draft_content, draft_at, final_content,
                               confirmed_by, confirmed_at, created_at, updated_at, goal_results)
SELECT ch.id, e.classroom_id, c.campus_id, c.school_year_id, 'SEMESTER',
       DATE '2026-09-01', DATE '2027-01-15', 'CONFIRMED',
       'Bản nháp kỳ I', now(), 'Đạt kỳ I (dữ liệu dev)', gv.id, now(), now(), now(), '[]'::text
FROM enrollments e
JOIN children ch ON ch.id = e.child_id
JOIN classrooms c ON c.id = e.classroom_id
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = e.classroom_id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
WHERE c.school_year_id = 1
  AND e.valid_from <= DATE '2027-01-15' AND (e.valid_to IS NULL OR e.valid_to >= DATE '2026-09-01')
  AND NOT EXISTS (
    SELECT 1 FROM child_evaluations ce
    WHERE ce.child_id = ch.id AND ce.classroom_id = e.classroom_id AND ce.period_type = 'SEMESTER');

-- Phiếu cuối năm (YEAR): để trống trạng thái để test tạo + gửi duyệt + chốt.
INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type,
                               period_start, period_end, status, created_at, updated_at, goal_results)
SELECT ch.id, e.classroom_id, c.campus_id, c.school_year_id, 'YEAR',
       DATE '2026-09-01', DATE '2027-05-31', 'AWAITING_REVIEW', now(), now(), '[]'::text
FROM enrollments e
JOIN children ch ON ch.id = e.child_id
JOIN classrooms c ON c.id = e.classroom_id
WHERE c.school_year_id = 1
  AND e.valid_from <= DATE '2027-05-31' AND (e.valid_to IS NULL OR e.valid_to >= DATE '2026-09-01')
  AND NOT EXISTS (
    SELECT 1 FROM child_evaluations ce
    WHERE ce.child_id = ch.id AND ce.classroom_id = e.classroom_id AND ce.period_type = 'YEAR');

-- Bảng học kỳ chỉ tạo nháp; snapshot hợp lệ được service chốt khi giáo viên xác nhận.
INSERT INTO semester_assessments (classroom_id, campus_id, school_year_id, semester_no,
                                  period_start, period_end, note, version)
SELECT c.id, c.campus_id, c.school_year_id, 1,
       DATE '2026-09-01', DATE '2027-01-15', 'Bảng học kỳ I (dữ liệu dev)', 0
FROM classrooms c
WHERE c.school_year_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM semester_assessments s WHERE s.classroom_id = c.id AND s.semester_no = 1);

INSERT INTO semester_assessments (classroom_id, campus_id, school_year_id, semester_no,
                                  period_start, period_end, note, version)
SELECT c.id, c.campus_id, c.school_year_id, 2,
       DATE '2027-01-16', DATE '2027-05-31', 'Bảng học kỳ II (dữ liệu dev)', 0
FROM classrooms c
WHERE c.school_year_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM semester_assessments s WHERE s.classroom_id = c.id AND s.semester_no = 2);

-- ============ 4. ĐÁNH GIÁ THEO CHỦ ĐỀ (theme_evaluations) ============
-- Một phiếu chủ đề/lớp, đã chốt, để màn đánh giá chủ đề có dữ liệu.
INSERT INTO theme_evaluations (classroom_id, campus_id, school_year_id, theme_id, theme_name,
                               start_date, end_date, cells, note, confirmed_by, confirmed_at,
                               updated_by, updated_at, version)
SELECT c.id, c.campus_id, c.school_year_id, th.id, th.name, th.start_date, th.end_date,
       '[]'::text, 'Phiếu chủ đề mẫu (dữ liệu dev)', gv.id, now(), gv.id, now(), 0
FROM edu_themes th
JOIN edu_year_plans p ON p.id = th.year_plan_id
JOIN classrooms c ON c.school_year_id = p.school_year_id AND c.age_group_id = p.age_group_id
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = c.id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
WHERE th.sort_order = 1
  AND NOT EXISTS (
    SELECT 1 FROM theme_evaluations te WHERE te.classroom_id = c.id AND te.theme_id = th.id);

-- ============ 5. KỲ CÂN ĐO (health_check_campaigns kind=MEASUREMENT) ============
-- Mỗi điểm trường một kỳ cân đo đang mở (để GV nhập số đo) + một kỳ đã đóng (có số đo để xem lịch sử).
-- Ràng buộc: mỗi campus chỉ được có một kỳ chưa đóng (uk_health_check_campaigns_campus_open).
INSERT INTO health_check_campaigns (kind, campus_id, name, start_date, end_date, examiner, note,
                                     created_by, created_at, updated_at)
SELECT 'MEASUREMENT', c.id, 'Kỳ cân đo định kỳ tháng 11/2026 – ' || c.name,
       CURRENT_DATE - 20, CURRENT_DATE + 10, '', 'Kỳ cân đo mẫu (dữ liệu dev)',
       ua.id, now(), now()
FROM campuses c
CROSS JOIN LATERAL (SELECT id FROM user_accounts WHERE username = 'principal' LIMIT 1) ua
WHERE NOT EXISTS (
  SELECT 1 FROM health_check_campaigns h
  WHERE h.campus_id = c.id AND h.closed_at IS NULL AND h.cancelled_at IS NULL);

-- Kỳ cân đo đã đóng ở mỗi điểm trường (có số đo mẫu cho trẻ để xem lịch sử sức khỏe)
INSERT INTO health_check_campaigns (kind, campus_id, name, start_date, end_date, examiner, note,
                                     created_by, created_at, updated_at, closed_at, closed_by)
SELECT 'MEASUREMENT', c.id, 'Kỳ cân đo tháng 09/2026 – ' || c.name,
       DATE '2026-09-01', DATE '2026-09-30', '', 'Kỳ cân đo đã đóng (dữ liệu dev)',
       ua.id, now(), now(), now(), ua.id
FROM campuses c
CROSS JOIN LATERAL (SELECT id FROM user_accounts WHERE username = 'principal' LIMIT 1) ua
WHERE NOT EXISTS (
  SELECT 1 FROM health_check_campaigns h WHERE h.campus_id = c.id AND h.kind = 'MEASUREMENT' AND h.closed_at IS NOT NULL);

-- Số đo cho kỳ đã đóng: cân nặng/chiều cao theo tuổi để kiểm tra mức BMI theo WHO.
INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date,
                                  height_cm, weight_kg, measured_by, published_at,
                                  created_at, updated_at)
SELECT m.child_id, m.campaign_id, m.classroom_id, m.date,
       LEAST(140, GREATEST(45, (80 + GREATEST(0, EXTRACT(YEAR FROM m.date) - EXTRACT(YEAR FROM m.date_of_birth)) * 5)::numeric)),
       LEAST(50, GREATEST(4, (11 + GREATEST(0, EXTRACT(YEAR FROM m.date) - EXTRACT(YEAR FROM m.date_of_birth)) * 2)::numeric)),
       ua.id, now(), now(), now()
FROM (
  SELECT DISTINCT ON (e.child_id) e.child_id, e.classroom_id, h.campus_id, h.id AS campaign_id,
         h.start_date AS date, ch.date_of_birth
  FROM enrollments e
  JOIN children ch ON ch.id = e.child_id
  JOIN classrooms c ON c.id = e.classroom_id
  JOIN health_check_campaigns h ON h.campus_id = c.campus_id
       AND h.kind = 'MEASUREMENT' AND h.closed_at IS NOT NULL
  WHERE e.valid_from <= h.start_date AND (e.valid_to IS NULL OR e.valid_to >= h.start_date)
  ORDER BY e.child_id, h.start_date DESC
) m
CROSS JOIN LATERAL (SELECT id FROM user_accounts WHERE username = 'principal' LIMIT 1) ua
WHERE NOT EXISTS (
  SELECT 1 FROM health_measurements hm WHERE hm.child_id = m.child_id AND hm.campaign_id = m.campaign_id);

COMMIT;
