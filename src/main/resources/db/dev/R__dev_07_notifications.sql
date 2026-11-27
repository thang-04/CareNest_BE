-- Bộ dữ liệu dev (profile "dev"): thông báo phát sinh từ các luồng giáo dục, sức khỏe, báo ăn. Dữ liệu giả nhìn như thật.
-- Chạy sau toàn bộ seed 01–06 (cần lịch sử duyệt hồ sơ lớp, đợt khám, kết quả cân đo, sổ báo ăn).
-- Tiêu đề/nội dung theo đúng mẫu trong code (EduClassDocServiceImpl, HealthNotifier, TbContactNotifier, MealCountServiceImpl).
-- Thông báo cũ hơn 3 ngày coi như đã đọc sau 2 giờ, trừ việc còn chờ xử lý; gần đây để chưa đọc.
-- Idempotent theo (người nhận, loại, tiêu đề, thời điểm).

CREATE TEMP TABLE n7 (user_id bigint, type varchar, title varchar, body varchar, child_id bigint, link varchar,
                      at timestamptz, pending boolean NOT NULL DEFAULT false);

-- 1. Hồ sơ lớp (hồ sơ chủ đề, gói học kỳ): mỗi bước trong lịch sử duyệt ⇒ thông báo cho người xử lý bước kế tiếp
CREATE TEMP TABLE n7_steps AS
SELECT d.id AS doc_id, d.kind, d.classroom_id, cl.campus_id, cl.age_group_id,
       CASE WHEN d.kind = 'semester' THEN 'Học kỳ ' || d.semester_no ELSE 'Hồ sơ chủ đề ' || COALESCE(t.name, '') END
           || ' · ' || cl.name AS what,
       CASE WHEN d.kind = 'semester' THEN '/education/class/' || d.classroom_id || '/semesters/' || d.semester_no
            ELSE '/education/class/' || d.classroom_id || '/themes/' || d.theme_id END AS link,
       h.e->>'action' AS action, h.e->>'roleKey' AS role_key, h.e->>'by' AS actor_name,
       (h.e->>'actorId')::bigint AS actor_id, COALESCE(h.e->>'note', '') AS note, (h.e->>'at')::timestamptz AS at,
       -- bước cuối của hồ sơ còn chờ duyệt ⇒ thông báo vẫn chưa đọc
       d.status = 'REVIEW' AND h.ord = jsonb_array_length(d.history) AS pending
FROM edu_class_docs d
JOIN classrooms cl ON cl.id = d.classroom_id
LEFT JOIN edu_themes t ON t.id = d.theme_id
CROSS JOIN LATERAL jsonb_array_elements(d.history) WITH ORDINALITY AS h (e, ord)
WHERE d.kind IN ('dossier', 'semester') AND h.e->>'action' IN ('submit', 'approve', 'reject');

-- Gửi duyệt: chờ tổ trưởng nhóm tuổi; người gửi chính là tổ trưởng thì bỏ bước tổ, chờ PHT điểm trường
INSERT INTO n7
SELECT COALESCE(tl.user_id, vp.user_id), 'EDU_SUBMITTED', 'Chờ bạn duyệt: ' || s.what,
       s.actor_name || ' gửi duyệt' || CASE WHEN s.note = '' THEN '' ELSE '. Ghi chú: ' || s.note END,
       NULL, s.link, s.at, s.pending
FROM n7_steps s
LEFT JOIN staff_assignments tl ON tl.staff_role = 'AGE_GROUP_TEAM_LEADER' AND tl.age_group_id = s.age_group_id
    AND tl.user_id <> s.actor_id AND tl.valid_from <= s.at::date AND (tl.valid_to IS NULL OR tl.valid_to >= s.at::date)
LEFT JOIN staff_assignments vp ON vp.staff_role = 'VICE_PRINCIPAL' AND vp.campus_id = s.campus_id
    AND vp.valid_from <= s.at::date AND (vp.valid_to IS NULL OR vp.valid_to >= s.at::date)
WHERE s.action = 'submit';

-- Tổ trưởng duyệt ⇒ chuyển PHT điểm trường duyệt tiếp
INSERT INTO n7
SELECT vp.user_id, 'EDU_STEP_APPROVED', 'Chuyển bạn duyệt tiếp: ' || s.what,
       s.actor_name || ' đã duyệt bước trước' || CASE WHEN s.note = '' THEN '' ELSE '. Ghi chú: ' || s.note END,
       NULL, s.link, s.at, s.pending
FROM n7_steps s
JOIN staff_assignments vp ON vp.staff_role = 'VICE_PRINCIPAL' AND vp.campus_id = s.campus_id
    AND vp.valid_from <= s.at::date AND (vp.valid_to IS NULL OR vp.valid_to >= s.at::date)
WHERE s.action = 'approve' AND s.role_key = 'TEAM_LEADER';

-- PHT duyệt ban hành / bị trả lại ⇒ giáo viên chủ nhiệm lớp
INSERT INTO n7
SELECT hr.user_id,
       CASE WHEN s.action = 'reject' THEN 'EDU_RETURNED' ELSE 'EDU_RELEASED' END,
       CASE WHEN s.action = 'reject' THEN 'Bị trả lại: ' ELSE 'Đã ban hành: ' END || s.what,
       CASE WHEN s.action = 'reject' THEN s.actor_name || ' trả lại. Lý do: ' || s.note
            ELSE s.actor_name || ' đã duyệt ban hành' || CASE WHEN s.note = '' THEN '' ELSE '. Ghi chú: ' || s.note END END,
       NULL, s.link, s.at, false
FROM n7_steps s
JOIN staff_assignments hr ON hr.staff_role = 'TEACHER' AND hr.is_homeroom AND hr.classroom_id = s.classroom_id
    AND hr.valid_from <= s.at::date AND (hr.valid_to IS NULL OR hr.valid_to >= s.at::date)
WHERE s.action = 'reject' OR (s.action = 'approve' AND s.role_key = 'VICE_PRINCIPAL');

-- Nhắc hạn: hồ sơ chủ đề còn nháp mà chủ đề bắt đầu trong 10 ngày tới (hạn = ngày trước khi chủ đề bắt đầu)
INSERT INTO n7
SELECT hr.user_id, 'EDU_DEADLINE_NEAR', 'Gần đến hạn: Hồ sơ chủ đề ' || t.name || ' · ' || cl.name,
       'Chưa gửi duyệt, còn ' || (t.start_date - 1 - CURRENT_DATE) || ' ngày tới hạn ' || to_char(t.start_date - 1, 'DD/MM/YYYY'),
       NULL, '/education/class/' || d.classroom_id || '/themes/' || d.theme_id,
       (CURRENT_DATE + time '06:30') AT TIME ZONE 'Asia/Ho_Chi_Minh', true
FROM edu_class_docs d
JOIN edu_themes t ON t.id = d.theme_id
JOIN classrooms cl ON cl.id = d.classroom_id
JOIN staff_assignments hr ON hr.staff_role = 'TEACHER' AND hr.is_homeroom AND hr.classroom_id = d.classroom_id
    AND hr.valid_to IS NULL
WHERE d.kind = 'dossier' AND d.status = 'DRAFT' AND t.start_date - 1 BETWEEN CURRENT_DATE AND CURRENT_DATE + 10;

-- 2. Mở đợt khám/kỳ cân đo ⇒ GV các lớp của điểm trường và PHT điểm trường
INSERT INTO n7
SELECT r.user_id, 'HEALTH_CHECK_OPENED',
       CASE WHEN c.kind = 'EXAM' THEN 'Đợt khám sức khỏe mới' ELSE 'Kỳ cân đo mới' END,
       CASE WHEN c.kind = 'EXAM' THEN 'Đợt khám sức khỏe' ELSE 'Kỳ cân đo' END || ' "' || c.name || '" đã mở, từ ngày '
           || to_char(c.start_date, 'DD/MM/YYYY') || ' đến ngày ' || to_char(c.end_date, 'DD/MM/YYYY') || '.',
       NULL, NULL, c.created_at, c.closed_at IS NULL
FROM health_check_campaigns c
CROSS JOIN LATERAL (
    SELECT a.user_id FROM staff_assignments a JOIN classrooms cl ON cl.id = a.classroom_id
    WHERE a.staff_role = 'TEACHER' AND cl.campus_id = c.campus_id
      AND a.valid_from <= c.created_at::date AND (a.valid_to IS NULL OR a.valid_to >= c.created_at::date)
    UNION
    SELECT a.user_id FROM staff_assignments a
    WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = c.campus_id
      AND a.valid_from <= c.created_at::date AND (a.valid_to IS NULL OR a.valid_to >= c.created_at::date)) r
WHERE c.cancelled_at IS NULL;

-- 3. Kết quả cân đo/khám được công bố lần đầu trong mỗi đợt ⇒ phụ huynh có tài khoản
INSERT INTO n7
SELECT g.user_id, 'HEALTH_RESULT_PUBLISHED', 'Kết quả sức khỏe mới',
       'Đã có kết quả cân đo, khám sức khỏe mới của ' || ch.full_name || '. Mở Sổ sức khỏe để xem.', ch.id, NULL, m.at, false
FROM (SELECT child_id, campaign_id, min(published_at) AS at FROM health_measurements
      WHERE published_at IS NOT NULL GROUP BY child_id, campaign_id) m
JOIN children ch ON ch.id = m.child_id
JOIN child_guardians g ON g.child_id = ch.id AND g.user_id IS NOT NULL;

-- 4. Khai báo tiếp xúc người bệnh lao khi tiếp nhận ⇒ HT, PHT điểm trường, GV lớp
INSERT INTO n7
SELECT r.user_id, 'CHILD_TB_CONTACT', 'Trẻ có tiền sử tiếp xúc với người bệnh lao',
       'Phụ huynh khai ' || ch.full_name || ' có tiền sử tiếp xúc với người bệnh lao (gần nhất ngày '
           || to_char(d.tb_contact_date, 'DD/MM/YYYY') || '). Nhà trường trao đổi với phụ huynh về việc đưa trẻ đi khám sàng'
           || ' lọc; bác sĩ xem lại ở đợt khám tới.',
       ch.id, NULL, d.declared_at, false
FROM child_health_declarations d
JOIN children ch ON ch.id = d.child_id
JOIN enrollments e ON e.child_id = ch.id AND e.valid_from <= d.declared_at::date + 30
    AND (e.valid_to IS NULL OR e.valid_to >= d.declared_at::date)
JOIN classrooms cl ON cl.id = e.classroom_id
CROSS JOIN LATERAL (
    SELECT a.user_id FROM staff_assignments a
    WHERE (a.staff_role = 'PRINCIPAL' OR (a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = cl.campus_id)
           OR (a.staff_role = 'TEACHER' AND a.classroom_id = cl.id))
      AND a.valid_from <= d.declared_at::date + 30 AND (a.valid_to IS NULL OR a.valid_to >= d.declared_at::date)) r
WHERE d.tb_contact_state = 'REPORTED';

-- 5. Sổ báo ăn đã xác nhận mà có số mới ⇒ PHT điểm trường
INSERT INTO n7
SELECT vp.user_id, 'MEAL_COUNT_RECONFIRM', 'Sổ báo ăn cần xác nhận lại',
       'Số suất ngày ' || to_char(mc.date, 'DD/MM/YYYY') || ' đã thay đổi sau khi xác nhận (giáo viên sửa điểm danh). Vào Sổ'
           || ' báo ăn để xác nhận lại; bếp vẫn dùng số đã xác nhận trước đó.',
       NULL, NULL, COALESCE(mc.updated_at, mc.aggregated_at), true
FROM (SELECT DISTINCT ON (campus_id, date) campus_id, date, updated_at, aggregated_at FROM meal_counts
      WHERE needs_reconfirmation ORDER BY campus_id, date, session) mc
JOIN staff_assignments vp ON vp.staff_role = 'VICE_PRINCIPAL' AND vp.campus_id = mc.campus_id AND vp.valid_to IS NULL;

INSERT INTO notifications (user_id, type, title, body, child_id, link, created_at, read_at)
SELECT DISTINCT ON (n.user_id, n.type, n.title, n.at) n.user_id, n.type, n.title, n.body, n.child_id, n.link, n.at,
       CASE WHEN n.at < now() - interval '3 days' AND NOT n.pending THEN n.at + interval '2 hours' END
FROM n7 n
WHERE n.user_id IS NOT NULL AND n.at <= now()
  AND NOT EXISTS (SELECT 1 FROM notifications x
                  WHERE x.user_id = n.user_id AND x.type = n.type AND x.title = n.title AND x.created_at = n.at)
ORDER BY n.user_id, n.type, n.title, n.at, n.pending DESC;

DROP TABLE n7_steps;
DROP TABLE n7;
