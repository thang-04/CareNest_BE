-- CHỈ DÙNG TRÊN DB DEV: nạp đầy đủ tầng giáo dục để luồng "Đánh giá trẻ" map được.
-- Chuỗi: lĩnh vực → mục tiêu → chủ đề → nội dung mục tiêu → giáo án lớp → kết quả hoạt động hằng ngày.
-- Nạp sau scripts/dev/seed-test-three-modules.sql (đã có edu_year_plans + edu_themes).
-- Idempotent: chạy lại không nhân bản.
BEGIN;

-- ============ 1. Mục tiêu chi tiết theo lĩnh vực (có nội dung con) ============
-- Mỗi lĩnh vực: 2 mục tiêu (đã có từ seed trước) + 1 mục tiêu nhánh + 2 kết quả kỳ vọng.
INSERT INTO edu_plan_sub_domains (year_plan_id, domain_id, sort_order, text)
SELECT p.id, d.id, 1, d.name || ' – Mục tiêu nhánh'
FROM edu_year_plans p CROSS JOIN edu_domains d
WHERE p.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM edu_plan_sub_domains s WHERE s.year_plan_id = p.id AND s.domain_id = d.id);

INSERT INTO edu_plan_expected_results (year_plan_id, domain_id, sort_order, text, sub_domain_id)
SELECT s.year_plan_id, s.domain_id, 1,
       'Trẻ tham gia và hoàn thành ' || d.name || ' theo chủ đề', s.id
FROM edu_plan_sub_domains s
JOIN edu_domains d ON d.id = s.domain_id
WHERE NOT EXISTS (
  SELECT 1 FROM edu_plan_expected_results r WHERE r.year_plan_id = s.year_plan_id AND r.domain_id = s.domain_id);

INSERT INTO edu_plan_expected_results (year_plan_id, domain_id, sort_order, text, sub_domain_id)
SELECT s.year_plan_id, s.domain_id, 2,
       'Trẻ thể hiện ' || d.name || ' qua hoạt động hằng ngày', s.id
FROM edu_plan_sub_domains s
JOIN edu_domains d ON d.id = s.domain_id
WHERE NOT EXISTS (
  SELECT 1 FROM edu_plan_expected_results r WHERE r.year_plan_id = s.year_plan_id AND r.domain_id = s.domain_id
    AND r.sort_order = 2);

-- ============ 2. Nội dung của mục tiêu (2 nội dung/mục tiêu) + gắn chủ đề ============
INSERT INTO edu_goal_contents (goal_id, sort_order, text)
SELECT g.id, n.n, g.text || ' – Nội dung ' || n.n
FROM edu_plan_goals g
CROSS JOIN (VALUES (1), (2)) AS n(n)
WHERE NOT EXISTS (SELECT 1 FROM edu_goal_contents c WHERE c.goal_id = g.id);

-- Nội dung mục tiêu thuộc chủ đề 1 (nội dung đầu tiên của mỗi mục tiêu)
INSERT INTO edu_content_theme_ticks (content_id, theme_id)
SELECT c.id, th.id
FROM edu_goal_contents c
JOIN edu_plan_goals g ON g.id = c.goal_id
JOIN edu_themes th ON th.year_plan_id = g.year_plan_id AND th.sort_order = 1
WHERE c.sort_order = 1
  AND NOT EXISTS (SELECT 1 FROM edu_content_theme_ticks t WHERE t.content_id = c.id AND t.theme_id = th.id);

-- Nội dung mục tiêu thuộc chủ đề 2 (nội dung thứ hai của mỗi mục tiêu)
INSERT INTO edu_content_theme_ticks (content_id, theme_id)
SELECT c.id, th.id
FROM edu_goal_contents c
JOIN edu_plan_goals g ON g.id = c.goal_id
JOIN edu_themes th ON th.year_plan_id = g.year_plan_id AND th.sort_order = 2
WHERE c.sort_order = 2
  AND NOT EXISTS (SELECT 1 FROM edu_content_theme_ticks t WHERE t.content_id = c.id AND t.theme_id = th.id);

-- ============ 3. Giáo án lớp (edu_class_docs) ============
-- doc_key đúng format BE: bp_<lớp>_<kế hoạch>, tp_<lớp>_<chủ đề>, wp_<lớp>_<ngày bắt đầu tuần>.
-- BRANCH: giáo án cả năm (mỗi lớp 1). THEME: giáo án chủ đề (mỗi lớp × chủ đề 1). WEEK: giáo án tuần.
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, year_plan_id, theme_id, week_start, status,
                            content, approvals, history, created_by, updated_by, created_at, updated_at)
SELECT 'bp_' || c.id || '_' || p.id, 'branch', c.id, p.id, NULL, NULL, 'DRAFT',
       '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, gv.id, gv.id, now(), now()
FROM classrooms c
JOIN edu_year_plans p ON p.school_year_id = c.school_year_id AND p.age_group_id = c.age_group_id
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = c.id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
WHERE NOT EXISTS (
  SELECT 1 FROM edu_class_docs d WHERE d.doc_key = 'bp_' || c.id || '_' || p.id);

INSERT INTO edu_class_docs (doc_key, kind, classroom_id, year_plan_id, theme_id, week_start, status,
                            content, approvals, history, created_by, updated_by, created_at, updated_at)
SELECT 'tp_' || c.id || '_' || th.id, 'theme', c.id, p.id, th.id, NULL, 'DRAFT',
       '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, gv.id, gv.id, now(), now()
FROM edu_themes th
JOIN edu_year_plans p ON p.id = th.year_plan_id
JOIN classrooms c ON c.school_year_id = p.school_year_id AND c.age_group_id = p.age_group_id
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = c.id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
WHERE th.sort_order <= 2
  AND NOT EXISTS (
    SELECT 1 FROM edu_class_docs d WHERE d.doc_key = 'tp_' || c.id || '_' || th.id);

-- Giáo án tuần: 2 tuần đầu của kỳ I (chủ đề 1), mỗi lớp 1 bản
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, year_plan_id, theme_id, week_start, status,
                            content, approvals, history, created_by, updated_by, created_at, updated_at)
SELECT 'wp_' || c.id || '_' || w.week_start::text, 'week', c.id, p.id, th.id, w.week_start, 'DRAFT',
       '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, gv.id, gv.id, now(), now()
FROM classrooms c
JOIN edu_year_plans p ON p.school_year_id = c.school_year_id AND p.age_group_id = c.age_group_id
JOIN edu_themes th ON th.year_plan_id = p.id AND th.sort_order = 1
CROSS JOIN (VALUES (DATE '2026-09-07'), (DATE '2026-09-14')) AS w(week_start)
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = c.id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
WHERE NOT EXISTS (
  SELECT 1 FROM edu_class_docs d WHERE d.doc_key = 'wp_' || c.id || '_' || w.week_start::text);

-- ============ 4. Kết quả hoạt động hằng ngày (daily_activity_results) ============
-- Nguồn cho luồng "Đánh giá trẻ" (AI dựa trên kết quả hoạt động): mỗi trẻ, mỗi ngày có dữ liệu.
-- activity_key theo giáo án tuần (class_doc_id) + mục tiêu đạt/chưa đạt.
INSERT INTO daily_activity_results (child_id, classroom_id, campus_id, date, activity_key, class_doc_id,
                                    participation, goals_met, goals_effort, goals_not_met,
                                    recorded_by, recorded_at, updated_by, updated_at)
SELECT m.child_id, m.classroom_id, m.campus_id, m.date, 'hoat_dong_chung', m.class_doc_id,
       'BINH_THUONG', m.met, '', m.not_met, gv.id, now(), gv.id, now()
FROM (
  SELECT DISTINCT ON (e.child_id, o.date) e.child_id, e.classroom_id, c.campus_id, o.date,
         d.id AS class_doc_id,
         (SELECT string_agg(g.id::text, ',') FROM edu_plan_goals g
            JOIN edu_year_plans p ON p.id = g.year_plan_id
           WHERE p.school_year_id = c.school_year_id AND p.age_group_id = c.age_group_id
             AND g.domain_id = 1) AS met,
         (SELECT string_agg(g.id::text, ',') FROM edu_plan_goals g
            JOIN edu_year_plans p ON p.id = g.year_plan_id
           WHERE p.school_year_id = c.school_year_id AND p.age_group_id = c.age_group_id
             AND g.domain_id = 2) AS not_met
  FROM enrollments e
  JOIN classrooms c ON c.id = e.classroom_id
  JOIN daily_observations o ON o.child_id = e.child_id AND o.classroom_id = e.classroom_id
  JOIN edu_class_docs d ON d.classroom_id = e.classroom_id AND d.kind = 'week'
  JOIN edu_year_plans p ON p.id = d.year_plan_id
  WHERE e.valid_from <= o.date AND (e.valid_to IS NULL OR e.valid_to >= o.date)
    AND o.date BETWEEN DATE '2026-10-19' AND DATE '2026-11-04'
  ORDER BY e.child_id, o.date
) m
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = m.classroom_id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
WHERE NOT EXISTS (
  SELECT 1 FROM daily_activity_results r
  WHERE r.child_id = m.child_id AND r.date = m.date AND r.activity_key = 'hoat_dong_chung');

-- ============ 5. Sự kiện kế hoạch giáo dục (lịch sử gửi duyệt) ============
INSERT INTO edu_plan_events (year_plan_id, action, action_label, actor_id, actor_name, actor_role_label,
                             tone, note, occurred_at)
SELECT p.id, 'SENT_FOR_REVIEW', 'Gửi duyệt kế hoạch giáo dục năm', ua.id, ua.full_name,
       'Phó hiệu trưởng', 'ok', 'Kế hoạch mẫu (dữ liệu dev)', now()
FROM edu_year_plans p
CROSS JOIN LATERAL (SELECT id, full_name FROM user_accounts WHERE username = 'viceprincipal' LIMIT 1) ua
WHERE p.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM edu_plan_events e WHERE e.year_plan_id = p.id AND e.action = 'SENT_FOR_REVIEW');

INSERT INTO edu_plan_events (year_plan_id, action, action_label, actor_id, actor_name, actor_role_label,
                             tone, note, occurred_at)
SELECT p.id, 'ISSUED', 'Ban hành kế hoạch giáo dục năm', ua.id, ua.full_name,
       'Hiệu trưởng', 'ok', 'Kế hoạch mẫu (dữ liệu dev)', now()
FROM edu_year_plans p
CROSS JOIN LATERAL (SELECT id, full_name FROM user_accounts WHERE username = 'principal' LIMIT 1) ua
WHERE p.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM edu_plan_events e WHERE e.year_plan_id = p.id AND e.action = 'ISSUED');

-- ============ 6. Lịch sử sự kiện đánh giá trẻ ============
-- MONTH: đã gửi duyệt (AWAITING_REVIEW) nên có sự kiện EDITED; SEMESTER: đã chốt nên có CONFIRMED.
INSERT INTO child_evaluation_events (evaluation_id, action, user_id, at)
SELECT ce.id, 'EDITED', ua.id, ce.created_at
FROM child_evaluations ce
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = ce.classroom_id AND sa.staff_role = 'TEACHER' LIMIT 1) ua
WHERE ce.status = 'AWAITING_REVIEW'
  AND NOT EXISTS (SELECT 1 FROM child_evaluation_events ev WHERE ev.evaluation_id = ce.id);

INSERT INTO child_evaluation_events (evaluation_id, action, user_id, at)
SELECT ce.id, 'CONFIRMED', ua.id, ce.confirmed_at
FROM child_evaluations ce
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = ce.classroom_id AND sa.staff_role = 'TEACHER' LIMIT 1) ua
WHERE ce.status = 'CONFIRMED' AND ce.confirmed_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM child_evaluation_events ev WHERE ev.evaluation_id = ce.id);

-- ============ 7. Bảng khen thưởng (reward_lists) ============
-- Ràng buộc: 1 danh sách / lớp. Nửa lớp để DRAFT (sửa + gửi duyệt), nửa lớp PENDING_VP (chờ duyệt).
INSERT INTO reward_lists (classroom_id, campus_id, school_year_id, status, created_by, updated_by,
                          created_at, updated_at, submitted_at, review_by, review_at, review_decision)
SELECT c.id, c.campus_id, c.school_year_id,
       CASE WHEN c.id % 2 = 1 THEN 'DRAFT' ELSE 'PENDING_VP' END,
       gv.id, gv.id,
       now() - INTERVAL '2 days', now() - INTERVAL '2 days',
       CASE WHEN c.id % 2 = 1 THEN NULL ELSE now() - INTERVAL '1 day' END,
       CASE WHEN c.id % 2 = 1 THEN NULL ELSE vp.id END,
       CASE WHEN c.id % 2 = 1 THEN NULL ELSE now() - INTERVAL '1 day' END,
       CASE WHEN c.id % 2 = 1 THEN NULL ELSE 'FORWARD' END
FROM classrooms c
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = c.id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
CROSS JOIN LATERAL (SELECT id FROM user_accounts WHERE username = 'viceprincipal' LIMIT 1) vp
WHERE c.school_year_id = 1
  AND NOT EXISTS (SELECT 1 FROM reward_lists r WHERE r.classroom_id = c.id);

-- ============ 8. Phiếu hành vi (behaviour_tickets) ============
-- 3 trẻ / lớp có vé khen (PR), 2 trẻ / lớp có vé nhắc (FLAG) trong tuần hiện tại.
INSERT INTO behaviour_tickets (child_id, classroom_id, campus_id, school_year_id, type, period_start,
                               period_end, present_days, flag_count, weekly_tickets, issued_by, issued_at)
SELECT m.child_id, m.classroom_id, m.campus_id, m.school_year_id, m.type,
       DATE '2026-11-03', DATE '2026-11-09', 5, m.flag_count, m.weekly, gv.id, now() - INTERVAL '1 day'
FROM (
  SELECT ch.id AS child_id, e.classroom_id, c.campus_id, c.school_year_id,
         ROW_NUMBER() OVER (PARTITION BY e.classroom_id ORDER BY ch.id) AS rn,
         CASE WHEN ch.id % 7 = 0 THEN 'WEEKLY' ELSE 'MONTHLY' END AS type,
         CASE WHEN ch.id % 7 = 0 THEN 0 ELSE 1 END AS flag_count,
         CASE WHEN ch.id % 7 = 0 THEN 1 ELSE 0 END AS weekly
  FROM enrollments e
  JOIN children ch ON ch.id = e.child_id
  JOIN classrooms c ON c.id = e.classroom_id
  WHERE c.school_year_id = 1
    AND e.valid_from <= DATE '2026-11-04' AND (e.valid_to IS NULL OR e.valid_to >= DATE '2026-11-03')
  ORDER BY e.classroom_id, ch.id
) m
CROSS JOIN LATERAL (
  SELECT sa.user_id AS id FROM staff_assignments sa
  WHERE sa.classroom_id = m.classroom_id AND sa.staff_role = 'TEACHER' LIMIT 1) gv
WHERE m.rn <= 5
  AND NOT EXISTS (
    SELECT 1 FROM behaviour_tickets b
    WHERE b.child_id = m.child_id AND b.type = m.type AND b.period_start = DATE '2026-11-03');

-- ============ 9. Mở khóa ngày chấm (assessment_day_unlocks) ============
-- BGH mở khóa cho 2 ngày gần nhất để GV ghi nhận / sửa được (mỗi lớp 1 bản).
INSERT INTO assessment_day_unlocks (classroom_id, campus_id, date, valid_until, reason,
                                    unlocked_by, unlocked_at)
SELECT c.id, c.campus_id, d.day, now() + INTERVAL '30 days', 'Mở khóa ghi nhận (dữ liệu dev)',
       vp.id, now()
FROM classrooms c
CROSS JOIN (VALUES (DATE '2026-11-04'), (DATE '2026-11-05')) AS d(day)
CROSS JOIN LATERAL (SELECT id FROM user_accounts WHERE username = 'viceprincipal' LIMIT 1) vp
WHERE c.school_year_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM assessment_day_unlocks u WHERE u.classroom_id = c.id AND u.date = d.day);

COMMIT;
