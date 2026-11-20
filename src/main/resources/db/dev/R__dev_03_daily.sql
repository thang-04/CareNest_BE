-- Bộ dữ liệu dev (profile "dev"): vận hành lớp hằng ngày — điểm danh, đơn nghỉ, mở lại ngày điểm danh, sức khỏe đầu
-- ngày, đón trẻ, đánh giá hằng ngày, nhận xét lớp, yêu cầu đổi SĐT phụ huynh, SMS tài khoản phụ huynh, thông báo.
-- Dữ liệu giả nhìn như thật. Chạy sau R__dev_01_people_structure.sql (và dev 02 catalog). Neo CURRENT_DATE.
--
-- Ngày học = thứ 2–6 không thuộc school_holidays, từ 03/09/2026 tới hôm qua; hôm nay là ngày đang làm dở: Cơ sở 1 đã điểm
-- danh + sức khỏe đầu ngày + một phần đánh giá, Cơ sở 2 chưa (chỉ có bản ghi Vắng có phép do đơn nghỉ). Ngày tới chỉ có
-- đơn nghỉ phụ huynh đã gửi trước. Biến thiên theo hàm băm cố định, không random().
--
-- Công thức điểm danh dùng chung (file bếp tính suất theo đúng công thức này):
--   h = abs(hashtext(mã trẻ || ':' || ngày)) % 100; h < 3 vắng có phép, 3 <= h < 5 vắng không phép, còn lại có mặt.
-- Vắng có phép của trẻ có tài khoản phụ huynh đến từ đơn nghỉ (ATT-04/ATT-09: bản ghi do tài khoản phụ huynh ghi).
-- Idempotent: ON CONFLICT / NOT EXISTS theo khóa tự nhiên; chạy lại ngày sau chỉ bù ngày còn thiếu.

-- 0. Ngữ cảnh, ngày học, lớp × ngày (GV ghi = GV có phân công còn hiệu lực ngày đó, luân phiên theo băm)
CREATE TEMP TABLE d3_ctx AS
SELECT CURRENT_DATE AS today, y.id AS year_id, y.end_date
FROM school_years y
WHERE y.name = '2026-2027';

CREATE TEMP TABLE d3_days AS
SELECT s.day,
       row_number() OVER (ORDER BY s.day)::int AS idx,
       CASE WHEN s.day < x.today THEN 'past' WHEN s.day = x.today THEN 'today' ELSE 'future' END AS kind
FROM d3_ctx x
CROSS JOIN LATERAL (SELECT d::date AS day FROM generate_series(DATE '2026-09-03', x.today + 10, INTERVAL '1 day') AS d) s
WHERE EXTRACT(ISODOW FROM s.day) < 6
  AND s.day <= x.end_date
  AND NOT EXISTS (
      SELECT 1 FROM school_holidays h
      WHERE h.school_year_id = x.year_id AND s.day BETWEEN h.start_date AND h.end_date);

CREATE TEMP TABLE d3_class_day AS
SELECT cl.id AS classroom_id, cl.campus_id, cp.code AS campus_code, cl.name AS class_name, g.code AS age_code,
       g.meal_sessions, d.day, d.idx, d.kind,
       t.ids[1 + abs(hashtext('gv:' || cl.id || ':' || d.day)::bigint) % array_length(t.ids, 1)] AS teacher_id,
       t.ids[1 + abs(hashtext('gd:' || cl.id || ':' || d.day)::bigint) % array_length(t.ids, 1)] AS pickup_teacher_id,
       -- Lưu cả phiếu một lần (cùng thời điểm cho cả lớp), trước giờ chốt 08:30
       (d.day + TIME '07:45' + (abs(hashtext('ga:' || cl.id || ':' || d.day)::bigint) % 21) * INTERVAL '1 minute')
           AT TIME ZONE 'Asia/Ho_Chi_Minh' AS base_at
FROM d3_days d
CROSS JOIN d3_ctx x
JOIN classrooms cl ON cl.school_year_id = x.year_id
JOIN campuses cp ON cp.id = cl.campus_id
JOIN age_groups g ON g.id = cl.age_group_id
JOIN LATERAL (
    SELECT array_agg(a.user_id ORDER BY a.is_homeroom DESC, a.user_id) AS ids
    FROM staff_assignments a
    WHERE a.classroom_id = cl.id AND a.staff_role = 'TEACHER'
      AND a.valid_from <= d.day AND (a.valid_to IS NULL OR a.valid_to >= d.day)) t ON t.ids IS NOT NULL;

-- Trẻ × ngày theo lớp hiệu lực ngày đó. Hôm nay: Cơ sở 1 đủ, Cơ sở 2 chỉ trẻ có đơn nghỉ; ngày tới: chỉ trẻ có đơn nghỉ
CREATE TEMP TABLE d3_att AS
SELECT ch.id AS child_id, ch.code, ch.full_name AS child_name, cd.classroom_id, cd.campus_id, cd.campus_code,
       cd.class_name, cd.age_code, cd.meal_sessions, cd.day, cd.idx, cd.kind, cd.teacher_id, cd.pickup_teacher_id,
       cd.base_at, h.h,
       CASE WHEN h.h < 3 AND p.parent_id IS NOT NULL THEN 'EXCUSED' WHEN h.h < 5 THEN 'UNEXCUSED' ELSE 'PRESENT' END AS status,
       p.parent_id, p.n_accounts,
       abs(hashtext('lt:' || ch.code || ':' || cd.day)::bigint) % 100 AS late_roll,
       abs(hashtext('sk:' || ch.code || ':' || cd.day)::bigint) % 100 AS sick_roll,
       abs(hashtext('sv:' || ch.code || ':' || cd.day)::bigint) % 6 AS sick_var,
       abs(hashtext('se:' || ch.code || ':' || cd.day)::bigint) % 2 AS sick_early
FROM d3_class_day cd
JOIN enrollments e ON e.classroom_id = cd.classroom_id
    AND e.valid_from <= cd.day AND (e.valid_to IS NULL OR e.valid_to >= cd.day)
JOIN children ch ON ch.id = e.child_id
CROSS JOIN LATERAL (SELECT abs(hashtext(ch.code || ':' || cd.day::text)::bigint) % 100 AS h) h
CROSS JOIN LATERAL (
    SELECT (array_agg(g.user_id ORDER BY g.account_recipient DESC, g.position))[1] AS parent_id, count(*) AS n_accounts
    FROM child_guardians g
    WHERE g.child_id = ch.id AND g.user_id IS NOT NULL) p
WHERE cd.kind = 'past'
   OR (cd.kind = 'today' AND (cd.campus_code = 'CS-A' OR (h.h < 3 AND p.parent_id IS NOT NULL)))
   OR (cd.kind = 'future' AND h.h < 3 AND p.parent_id IS NOT NULL);

ALTER TABLE d3_att ADD COLUMN late boolean, ADD COLUMN early boolean;
-- Bé đến muộn trước giờ chốt: cô lưu Vắng không phép rồi sửa Có mặt (~2%); bé sốt được đón sớm (từ sức khỏe đầu ngày)
UPDATE d3_att
SET late = status = 'PRESENT' AND kind IN ('past', 'today') AND late_roll < 2,
    early = status = 'PRESENT' AND kind = 'past' AND sick_roll < 3 AND sick_var = 0 AND sick_early = 0;
UPDATE d3_att SET late = FALSE WHERE early;

-- 1. Đơn nghỉ (ATT-04, ATT-05: thông báo, không duyệt). Mỗi chuỗi ngày vắng có phép liên tiếp của trẻ có tài khoản
-- phụ huynh = một đơn, gửi tối hôm trước hoặc sáng sớm trước giờ chốt; đơn cho ngày tới gửi từ tối qua
CREATE TEMP TABLE d3_leave AS
SELECT s.child_id, s.code, s.parent_id, s.from_date, s.to_date, s.n_days,
       CASE
           WHEN s.from_date >= x.today
               THEN ((x.today - 1) + TIME '19:30' + (s.r % 150) * INTERVAL '1 minute')
           WHEN s.r % 100 < 35
               THEN (s.from_date + TIME '06:00' + (s.r % 100) * INTERVAL '1 minute')
           ELSE ((s.from_date - 1) + TIME '19:00' + (s.r % 170) * INTERVAL '1 minute')
       END AT TIME ZONE 'Asia/Ho_Chi_Minh' AS created_at,
       CASE WHEN s.n_days = 1
           THEN (ARRAY['Con bị sốt nhẹ từ tối qua, gia đình xin cho con nghỉ ở nhà theo dõi.',
                       'Con ho và sổ mũi, xin cô cho con nghỉ một hôm.',
                       'Gia đình có việc riêng, xin cho con nghỉ học hôm nay.',
                       'Đưa con đi tiêm phòng theo lịch của trạm y tế xã.',
                       'Con bị đau bụng, đi ngoài, gia đình cho con ở nhà.',
                       'Nhà có đám cưới ở quê, cả nhà về quê nên xin cho con nghỉ.',
                       'Con ngủ dậy mệt, quấy khóc, xin cô cho con nghỉ một ngày.',
                       'Đưa con đi khám răng ở bệnh viện huyện.',
                       'Con bị viêm họng, bác sĩ dặn nghỉ ngơi ở nhà.',
                       'Bố mẹ đưa con đi làm giấy tờ, xin cho con nghỉ.',
                       'Con bị muỗi đốt sưng nhiều ở chân, gia đình cho con ở nhà một hôm.',
                       'Ông bà ngoại lên chơi, xin cho con nghỉ ở nhà với ông bà.'])[1 + s.r % 12]
           ELSE (ARRAY['Gia đình về quê có việc mấy ngày, xin cho con nghỉ.',
                       'Con bị sốt chưa dứt, bác sĩ dặn ở nhà theo dõi thêm.',
                       'Con bị thủy đậu, gia đình cho con nghỉ đến khi khỏi hẳn.',
                       'Cả nhà đi thăm người thân ở xa, xin cho con nghỉ mấy hôm.',
                       'Con viêm phế quản, đang uống thuốc theo đơn, xin nghỉ đến khi đỡ.'])[1 + s.r % 5]
       END AS reason
FROM (
    SELECT g.child_id, min(g.code) AS code, g.parent_id, min(g.day) AS from_date, max(g.day) AS to_date,
           count(*) AS n_days, abs(hashtext('lv:' || min(g.code) || ':' || min(g.day))::bigint) AS r
    FROM (SELECT a.*, a.idx - row_number() OVER (PARTITION BY a.child_id ORDER BY a.idx) AS grp
          FROM d3_att a
          WHERE a.status = 'EXCUSED' AND a.parent_id IS NOT NULL) g
    GROUP BY g.child_id, g.parent_id, g.grp) s
CROSS JOIN d3_ctx x;

INSERT INTO leave_requests (child_id, from_date, to_date, reason, created_by, created_at)
SELECT l.child_id, l.from_date, l.to_date, l.reason, l.parent_id, l.created_at
FROM d3_leave l
WHERE NOT EXISTS (
    SELECT 1 FROM leave_requests q
    WHERE q.child_id = l.child_id AND q.from_date <= l.to_date AND q.to_date >= l.from_date);

-- Một số đơn phụ huynh gửi tối hôm trước rồi hủy sáng hôm sau trước giờ chốt vì con đỡ, con vẫn đi học (ATT-05)
INSERT INTO leave_requests (child_id, from_date, to_date, reason, created_by, created_at, cancelled_from, cancelled_by,
                            cancelled_at)
SELECT a.child_id, a.day, a.day,
       (ARRAY['Con hơi sốt buổi tối, xin cho con nghỉ ngày mai.',
              'Con kêu mệt, gia đình định cho con nghỉ ở nhà.',
              'Gia đình có thể phải đi việc sớm, xin cho con nghỉ.'])[1 + a.late_roll % 3],
       a.parent_id,
       ((a.day - 1) + TIME '20:00' + (a.sick_roll * INTERVAL '1 minute')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
       a.day, a.parent_id,
       (a.day + TIME '06:15' + ((a.late_roll % 60) * INTERVAL '1 minute')) AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM d3_att a
WHERE a.kind = 'past' AND a.status = 'PRESENT' AND a.parent_id IS NOT NULL AND NOT a.late AND NOT a.early
  AND abs(hashtext('lc:' || a.code || ':' || a.day)::bigint) % 250 = 0
  AND NOT EXISTS (
      SELECT 1 FROM leave_requests q WHERE q.child_id = a.child_id AND a.day BETWEEN q.from_date AND q.to_date);

-- 2. Mở lại ngày điểm danh (ATT-08): mỗi lớp một lần (lớp thứ k theo tên: 1 + 2k ngày học trước hôm nay) do PHT điểm
-- trường (đôi khi HT) mở để cô sửa một bé đến sau giờ chốt; vài lần thu hồi sớm sau khi cô sửa xong. Thêm một lần đang mở (hạn tới mai) ở Lớp Mầm 2
CREATE TEMP TABLE d3_unlock AS
SELECT cd.classroom_id, cd.campus_id, cd.class_name, cd.day, cd.teacher_id, r.r,
       CASE WHEN r.r % 4 = 0 THEN ht.user_id ELSE COALESCE(vp.user_id, ht.user_id) END AS unlocked_by,
       (cd.day + TIME '09:35' + (r.r % 50) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS unlocked_at,
       (cd.day + TIME '09:35' + (r.r % 50 + 12 + r.r % 18) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS fix_at,
       r.r % 3 = 0 AS revoked,
       (ARRAY['Bé đến lớp lúc 9 giờ sau giờ chốt, cô cần chuyển từ vắng sang có mặt.',
              'Phụ huynh đưa bé đi tiêm chủng về muộn, cô chưa kịp cập nhật trước giờ chốt.',
              'Cô điểm danh nhầm một bé vắng, thực tế bé có mặt cả ngày.',
              'Gia đình có việc nên đưa bé đến muộn, cô báo lại sau giờ chốt.'])[1 + r.r % 4] AS reason,
       FALSE AS active
FROM d3_class_day cd
CROSS JOIN LATERAL (SELECT abs(hashtext('ul:' || cd.class_name)::bigint) AS r,
                           (SELECT count(*) FROM classrooms c2
                            WHERE c2.school_year_id = (SELECT year_id FROM d3_ctx)
                              AND c2.name COLLATE "C" < cd.class_name COLLATE "C") AS rk) r
CROSS JOIN (SELECT max(idx) AS mx FROM d3_days WHERE kind = 'past') p
LEFT JOIN LATERAL (
    SELECT a.user_id FROM staff_assignments a
    WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = cd.campus_id
      AND a.valid_from <= cd.day AND (a.valid_to IS NULL OR a.valid_to >= cd.day)
    ORDER BY a.id LIMIT 1) vp ON TRUE
JOIN LATERAL (
    SELECT a.user_id FROM staff_assignments a
    WHERE a.staff_role = 'PRINCIPAL' AND a.valid_from <= cd.day AND (a.valid_to IS NULL OR a.valid_to >= cd.day)
    ORDER BY a.id LIMIT 1) ht ON TRUE
WHERE cd.kind = 'past' AND p.mx > 2
  AND cd.idx = GREATEST(2, p.mx - 1 - 2 * r.rk::int)
  -- Chạy lại ngày sau không mở thêm: lớp đã có lần mở lại thì thôi
  AND NOT EXISTS (SELECT 1 FROM attendance_day_unlocks x WHERE x.classroom_id = cd.classroom_id);

INSERT INTO d3_unlock (classroom_id, campus_id, class_name, day, teacher_id, r, unlocked_by, unlocked_at, fix_at,
                       revoked, reason, active)
SELECT cd.classroom_id, cd.campus_id, cd.class_name, cd.day, cd.teacher_id, 0, vp.user_id,
       (cd.day + TIME '16:20') AT TIME ZONE 'Asia/Ho_Chi_Minh', NULL, FALSE,
       'Giáo viên báo có thể điểm danh nhầm, cần kiểm tra lại trước khi tổng hợp sổ báo ăn tháng.', TRUE
FROM d3_class_day cd
CROSS JOIN (SELECT max(idx) AS mx FROM d3_days WHERE kind = 'past') p
JOIN LATERAL (
    SELECT a.user_id FROM staff_assignments a
    WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = cd.campus_id
      AND a.valid_from <= cd.day AND (a.valid_to IS NULL OR a.valid_to >= cd.day)
    ORDER BY a.id LIMIT 1) vp ON TRUE
WHERE cd.kind = 'past' AND cd.idx = p.mx AND cd.class_name = 'Lớp Mầm 2'
  AND NOT EXISTS (SELECT 1 FROM d3_unlock u WHERE u.classroom_id = cd.classroom_id AND u.day = cd.day)
  AND NOT EXISTS (
      SELECT 1 FROM attendance_day_unlocks x WHERE x.classroom_id = cd.classroom_id AND x.valid_until > now());

INSERT INTO attendance_day_unlocks (classroom_id, campus_id, date, valid_until, reason, unlocked_by, unlocked_at,
                                    revoked_by, revoked_at)
SELECT u.classroom_id, u.campus_id, u.day,
       CASE WHEN u.active THEN ((SELECT today FROM d3_ctx) + 1 + TIME '17:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            WHEN u.revoked THEN u.fix_at + INTERVAL '5 minutes'
            ELSE (u.day + TIME '17:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END,
       u.reason, u.unlocked_by, u.unlocked_at,
       CASE WHEN u.revoked THEN u.unlocked_by END,
       CASE WHEN u.revoked THEN u.fix_at + INTERVAL '5 minutes' END
FROM d3_unlock u
WHERE NOT EXISTS (
    SELECT 1 FROM attendance_day_unlocks x WHERE x.classroom_id = u.classroom_id AND x.date = u.day);

-- Chỉ giữ lần mở lại vừa thêm (lần đã có từ trước đã có thông báo, bản ghi sửa)
DELETE FROM d3_unlock u
WHERE NOT EXISTS (
    SELECT 1 FROM attendance_day_unlocks x
    WHERE x.classroom_id = u.classroom_id AND x.date = u.day AND x.unlocked_at = u.unlocked_at);

-- Bé được sửa trong lần mở lại: bé có mặt (theo công thức) có băm nhỏ nhất của lớp ngày đó
CREATE TEMP TABLE d3_unlock_fix AS
SELECT c.child_id, u.day, u.teacher_id, u.fix_at, x.id AS unlock_id
FROM d3_unlock u
JOIN attendance_day_unlocks x ON x.classroom_id = u.classroom_id AND x.date = u.day AND x.unlocked_at = u.unlocked_at
JOIN LATERAL (
    SELECT a.child_id FROM d3_att a
    WHERE a.classroom_id = u.classroom_id AND a.day = u.day AND a.status = 'PRESENT' AND NOT a.late AND NOT a.early
    ORDER BY abs(hashtext('uc:' || a.code || ':' || a.day)::bigint), a.child_id
    LIMIT 1) c ON TRUE
WHERE NOT u.active;

-- 3. Điểm danh + lịch sử. Vắng có phép có đơn: bản ghi của tài khoản phụ huynh lúc gửi đơn; còn lại GV lớp ghi
CREATE TEMP TABLE d3_att_src AS
SELECT a.child_id, a.classroom_id, a.campus_id, a.day, a.status,
       CASE WHEN a.status = 'PRESENT' THEN a.meal_sessions ELSE '' END AS meals,
       CASE WHEN l.child_id IS NOT NULL THEN 'LEAVE' WHEN f.child_id IS NOT NULL THEN 'UNLOCK'
            WHEN a.late THEN 'LATE' ELSE 'TEACHER' END AS path,
       CASE WHEN l.child_id IS NOT NULL THEN l.parent_id ELSE a.teacher_id END AS rec_by,
       CASE WHEN l.child_id IS NOT NULL THEN l.created_at ELSE a.base_at END AS first_at,
       CASE WHEN l.child_id IS NOT NULL THEN l.created_at
            WHEN f.child_id IS NOT NULL THEN f.fix_at
            WHEN a.late THEN (a.day + TIME '08:10' + (a.sick_roll % 19) * INTERVAL '1 minute')
                AT TIME ZONE 'Asia/Ho_Chi_Minh'
            ELSE a.base_at END AS rec_at,
       f.unlock_id
FROM d3_att a
LEFT JOIN d3_leave l ON l.child_id = a.child_id AND a.day BETWEEN l.from_date AND l.to_date AND a.status = 'EXCUSED'
LEFT JOIN d3_unlock_fix f ON f.child_id = a.child_id AND f.day = a.day;

WITH ins AS (
    INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals, recorded_by, recorded_at,
                                    created_at, updated_at)
    SELECT s.child_id, s.classroom_id, s.campus_id, s.day, s.status, s.meals, s.rec_by, s.rec_at, s.first_at, s.rec_at
    FROM d3_att_src s
    ON CONFLICT (child_id, date) DO NOTHING
    RETURNING id, child_id, date)
INSERT INTO attendance_record_history (record_id, action, status, meals, user_id, at, unlock_id, child_id, classroom_id,
                                       campus_id, date)
SELECT i.id, v.action, v.status, v.meals, s.rec_by, v.at, v.unlock_id, s.child_id, s.classroom_id, s.campus_id, s.day
FROM ins i
JOIN d3_att_src s ON s.child_id = i.child_id AND s.day = i.date
CROSS JOIN LATERAL (
    SELECT 'RECORDED' AS action,
           CASE WHEN s.path IN ('LATE', 'UNLOCK') THEN 'UNEXCUSED' ELSE s.status END AS status,
           CASE WHEN s.path IN ('LATE', 'UNLOCK') THEN '' ELSE s.meals END AS meals,
           s.first_at AS at, NULL::bigint AS unlock_id
    UNION ALL
    SELECT 'UPDATED', s.status, s.meals, s.rec_at, s.unlock_id
    WHERE s.path IN ('LATE', 'UNLOCK')) v;

-- 4. Sức khỏe đầu ngày của trẻ có mặt (HLT-07, HLT-15): đo nhiệt độ lúc đón; ~3% có biểu hiện cần lưu ý. Bé sốt được đo
-- lại khoảng 10 giờ; một nửa số bé sốt tăng thì gia đình đón sớm. Hôm nay chỉ Cơ sở 1 đã ghi
CREATE TEMP TABLE d3_health AS
SELECT a.child_id, a.code, a.classroom_id, a.campus_id, a.day, a.kind, a.sick_roll, a.sick_var, a.early,
       s.rec_by AS teacher_id,
       CASE WHEN s.path = 'UNLOCK' THEN s.rec_at + INTERVAL '2 minutes'
            ELSE s.rec_at + (1 + abs(hashtext('ht:' || a.code || ':' || a.day)::bigint) % 20) * INTERVAL '1 minute'
       END AS at,
       abs(hashtext('hn:' || a.code || ':' || a.day)::bigint) AS n
FROM d3_att a
JOIN d3_att_src s ON s.child_id = a.child_id AND s.day = a.day
WHERE a.status = 'PRESENT' AND (a.kind = 'past' OR (a.kind = 'today' AND a.campus_code = 'CS-A'));

INSERT INTO daily_health_entries (request_id, child_id, classroom_id, campus_id, date, temperature, observation, symptoms,
                                  note, actions, recorded_by, recorded_at)
SELECT md5('dh:' || h.code || ':' || h.day || ':1')::uuid::text, h.child_id, h.classroom_id, h.campus_id, h.day, t.temp,
       CASE WHEN h.sick_roll < 3 THEN 'NOTED' ELSE 'NO_CONCERN' END,
       CASE WHEN h.sick_roll >= 3 THEN ''
            ELSE (ARRAY['FEVER', 'COUGH', 'RUNNY_NOSE', 'OTHER', 'TIRED', 'COUGH,RUNNY_NOSE'])[1 + h.sick_var] END,
       CASE WHEN h.sick_roll >= 3 THEN ''
            WHEN h.sick_var = 0 THEN (ARRAY['Bé sốt nhẹ ' || t.temp_vn || ' độ, mẹ cho biết tối qua bé hơi nóng người.',
                                            'Đo nách ' || t.temp_vn || ' độ, bé hơi mệt, cô cho bé uống nước và theo dõi.',
                                            'Bé sốt nhẹ ' || t.temp_vn || ' độ lúc đón, đã báo mẹ.'])[1 + h.n % 3]
            WHEN h.sick_var = 1 THEN (ARRAY['Bé ho khan vài tiếng, không sốt.',
                                            'Bé ho có đờm, mẹ dặn cô cho bé uống nước ấm.',
                                            'Bé ho nhiều về đêm theo lời bố, ở lớp bé vẫn chơi bình thường.'])[1 + h.n % 3]
            WHEN h.sick_var = 2 THEN (ARRAY['Bé sổ mũi trong, cô nhắc bé lau mũi và rửa tay.',
                                            'Bé chảy nước mũi, hắt hơi nhiều lúc mới đến lớp.',
                                            'Sổ mũi nhẹ, mẹ đã gửi khăn giấy riêng cho bé.'])[1 + h.n % 3]
            WHEN h.sick_var = 3 THEN (ARRAY['Nổi vài nốt mẩn đỏ ở cánh tay, bé không ngứa nhiều.',
                                            'Phát ban nhẹ ở lưng, nhân viên y tế đã xem và dặn theo dõi.',
                                            'Có vết muỗi đốt sưng đỏ ở chân, đã bôi thuốc theo hướng dẫn của y tế.'])[1 + h.n % 3]
            WHEN h.sick_var = 4 THEN (ARRAY['Bé mệt, ít nói, ngồi một mình lúc mới đến.',
                                            'Bé ngủ dậy muộn, còn mệt và quấy khi mẹ về.',
                                            'Bé kêu mỏi người, cô cho bé nghỉ ở góc yên tĩnh.'])[1 + h.n % 3]
            ELSE (ARRAY['Bé ho và sổ mũi, nhân viên y tế đã xem.',
                        'Bé ho, sổ mũi, giọng hơi khàn, đã báo phụ huynh.'])[1 + h.n % 2]
       END,
       CASE WHEN h.sick_roll >= 3 THEN ''
            ELSE (ARRAY['MONITOR,PARENT_INFORMED', 'MONITOR', 'MONITOR,PARENT_INFORMED',
                        'MONITOR,MEDICAL_ROOM,PARENT_INFORMED', 'MONITOR,PARENT_INFORMED',
                        'MONITOR,MEDICAL_ROOM'])[1 + h.sick_var] END,
       h.teacher_id, h.at
FROM d3_health h
CROSS JOIN LATERAL (
    SELECT tv AS temp, replace(to_char(tv, 'FM90.0'), '.', ',') AS temp_vn
    FROM (SELECT CASE WHEN h.sick_roll < 3 AND h.sick_var = 0 THEN 37.6 + (h.n % 4) / 10.0
                      ELSE 36.2 + (h.n % 8) / 10.0 END::numeric(3, 1) AS tv) q) t
ON CONFLICT (recorded_by, request_id) DO NOTHING;

-- Đo lại bé sốt khoảng 10 giờ (chỉ ngày đã qua): đã hạ sốt hoặc sốt tăng ⇒ báo gia đình đón sớm
INSERT INTO daily_health_entries (request_id, child_id, classroom_id, campus_id, date, temperature, observation, symptoms,
                                  note, actions, recorded_by, recorded_at)
SELECT md5('dh:' || h.code || ':' || h.day || ':2')::uuid::text, h.child_id, h.classroom_id, h.campus_id, h.day, t.temp,
       'NOTED',
       CASE WHEN h.early THEN 'FEVER,TIRED' ELSE 'FEVER' END,
       CASE WHEN h.early THEN 'Đo lại lúc 10 giờ bé sốt ' || t.temp_vn || ' độ, quấy khóc, đã báo gia đình đón sớm.'
            ELSE 'Đo lại lúc 10 giờ còn ' || t.temp_vn || ' độ, bé ăn nhẹ và chơi bình thường.' END,
       CASE WHEN h.early THEN 'MONITOR,MEDICAL_ROOM,PARENT_INFORMED,EARLY_PICKUP' ELSE 'MONITOR' END,
       h.teacher_id, (h.day + TIME '09:50' + (h.n % 25) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM d3_health h
CROSS JOIN LATERAL (
    SELECT tv AS temp, replace(to_char(tv, 'FM90.0'), '.', ',') AS temp_vn
    FROM (SELECT CASE WHEN h.early THEN 38.2 + (h.n % 5) / 10.0 ELSE 37.1 + (h.n % 3) / 10.0 END::numeric(3, 1) AS tv) q) t
WHERE h.kind = 'past' AND h.sick_roll < 3 AND h.sick_var = 0
ON CONFLICT (recorded_by, request_id) DO NOTHING;

-- 5. Đón trẻ (PCK-01, PCK-02): mỗi trẻ có mặt ngày đã qua được trả một lần; ~88% phụ huynh khớp ảnh, ~11% người thân
-- không có trong hồ sơ được phụ huynh xác nhận qua điện thoại; ~1% có lần gọi phụ huynh không xác nhận (không trả) rồi
-- phụ huynh đến đón. Bé sốt được đón sớm khoảng 10 giờ 30. Hôm nay chưa trả trẻ nào
CREATE TEMP TABLE d3_pick AS
SELECT a.child_id, a.code, a.child_name, a.classroom_id, a.campus_id, a.day, a.n_accounts, a.early,
       COALESCE(a.pickup_teacher_id, a.teacher_id) AS teacher_id,
       abs(hashtext('pk:' || a.code || ':' || a.day)::bigint) % 100 AS k,
       abs(hashtext('pg:' || a.code || ':' || a.day)::bigint) AS m,
       CASE WHEN a.early
            THEN (a.day + TIME '10:30' + (abs(hashtext('pt:' || a.code || ':' || a.day)::bigint) % 40) * INTERVAL '1 minute')
            ELSE (a.day + TIME '16:20' + (abs(hashtext('pt:' || a.code || ':' || a.day)::bigint) % 76) * INTERVAL '1 minute')
       END AT TIME ZONE 'Asia/Ho_Chi_Minh' AS handed_at
FROM d3_att a
WHERE a.kind = 'past' AND a.status = 'PRESENT'
  AND NOT EXISTS (SELECT 1 FROM pickup_records p WHERE p.child_id = a.child_id AND p.date = a.day);

INSERT INTO pickup_records (child_id, classroom_id, campus_id, date, outcome, verification, guardian_position,
                            pickup_person_name, pickup_person_relationship, pickup_person_phone, handed_over_at, note,
                            notified_parents, recorded_by, recorded_at)
-- Lần gọi phụ huynh không xác nhận: không trả trẻ
SELECT p.child_id, p.classroom_id, p.campus_id, p.day, 'NOT_HANDED_OVER', 'PHONE_DECLINED', NULL, '', NULL, '', NULL,
       (ARRAY['Người đến đón tự giới thiệu là chú của bé. Gọi ' || r.label || ' bé, ' || r.label
                  || ' cho biết không nhờ ai đón hôm nay. Chưa trả trẻ, chờ ' || r.label || ' đến.',
              'Một người hàng xóm đến đón nhưng không có tên trong hồ sơ. Gọi ' || r.label || ' bé không đồng ý nhờ đón hộ.',
              'Người đón không mang theo giấy tờ, gọi ' || r.label || ' bé không liên lạc được nên chưa trả trẻ.'])[1 + p.m % 3],
       0, p.teacher_id, p.handed_at - INTERVAL '25 minutes'
FROM d3_pick p
JOIN child_guardians g0 ON g0.child_id = p.child_id AND g0.position = 0
CROSS JOIN LATERAL (SELECT CASE g0.relationship WHEN 'FATHER' THEN 'bố' WHEN 'MOTHER' THEN 'mẹ'
                                WHEN 'GRANDFATHER' THEN 'ông' WHEN 'GRANDMOTHER' THEN 'bà' ELSE 'người nhà' END AS label) r
WHERE p.k = 99 AND NOT p.early
UNION ALL
-- Lần trả trẻ: khớp ảnh phụ huynh trong hồ sơ, hoặc người thân được phụ huynh xác nhận qua điện thoại
SELECT p.child_id, p.classroom_id, p.campus_id, p.day, 'HANDED_OVER', v.verification,
       CASE WHEN v.verification = 'PHOTO_MATCH' THEN g.position END,
       CASE WHEN v.verification = 'PHOTO_MATCH' THEN g.full_name ELSE '' END,
       CASE WHEN v.verification = 'PHOTO_MATCH' THEN g.relationship END,
       CASE WHEN v.verification = 'PHOTO_MATCH' THEN g.phone ELSE '' END,
       date_trunc('minute', p.handed_at),
       CASE WHEN p.early THEN 'Bé sốt, gia đình đón về sớm để theo dõi.'
            WHEN v.verification = 'PHOTO_MATCH' THEN ''
            ELSE (ARRAY['Đã gọi ' || r.label || ' bé, ' || r.label || ' xác nhận nhờ bà ngoại đón hộ hôm nay.',
                        'Gọi ' || r.label || ' bé xác nhận: ông nội đón hộ vì bố mẹ đi làm về muộn.',
                        'Đã gọi ' || r.label || ' bé, ' || r.label || ' nhờ cô ruột của bé đón, người đón đọc đúng tên lớp và tên cô.',
                        'Gọi ' || r.label || ' bé xác nhận nhờ chú ruột đón hộ, đã đối chiếu tên người đón.',
                        'Đã gọi ' || r.label || ' bé, ' || r.label || ' xác nhận nhờ dì của bé đón.',
                        'Gọi ' || r.label || ' bé xác nhận nhờ bác hàng xóm đón hộ, ' || r.label || ' sẽ đón bé tại nhà bác.'])[1 + p.m % 6]
       END,
       p.n_accounts, p.teacher_id,
       date_trunc('minute', p.handed_at) + (p.m % 3) * INTERVAL '1 minute' + INTERVAL '20 seconds'
FROM d3_pick p
JOIN child_guardians g0 ON g0.child_id = p.child_id AND g0.position = 0
CROSS JOIN LATERAL (SELECT CASE g0.relationship WHEN 'FATHER' THEN 'bố' WHEN 'MOTHER' THEN 'mẹ'
                                WHEN 'GRANDFATHER' THEN 'ông' WHEN 'GRANDMOTHER' THEN 'bà' ELSE 'người nhà' END AS label) r
CROSS JOIN LATERAL (SELECT CASE WHEN p.early OR p.k < 88 OR p.k = 99 THEN 'PHOTO_MATCH' ELSE 'PHONE_CONFIRMED' END
                               AS verification) v
JOIN LATERAL (
    SELECT x.position, x.full_name, x.relationship, x.phone
    FROM child_guardians x
    WHERE x.child_id = p.child_id
    ORDER BY (x.position = CASE WHEN p.early OR p.k = 99 THEN 0
                                WHEN p.m % 100 < 60 THEN 0 WHEN p.m % 100 < 92 THEN 1 ELSE 2 END) DESC, x.position
    LIMIT 1) g ON TRUE;

-- 6. Đánh giá hằng ngày (OBS-01, OBS-02, OBS-10): mọi trẻ có mặt ngày đã qua — ăn từng bữa của nhóm tuổi, ngủ, cảm xúc,
-- tham gia, hành vi nổi bật, cờ bé ngoan; vài nhận xét ngắn. Hai tuần đầu nhà trẻ, mầm còn khóc khi bố mẹ về.
-- Hôm nay Cơ sở 1 mới ghi sau bữa trưa cho khoảng một nửa lớp
INSERT INTO daily_observations (child_id, classroom_id, campus_id, date, meals, nap, emotion, participation, behaviors,
                                behavior_note, flag, comment, recorded_by, recorded_at, updated_by, updated_at)
SELECT a.child_id, a.classroom_id, a.campus_id, a.day,
       COALESCE((
           SELECT jsonb_object_agg(sess, CASE WHEN q % 100 < 80 THEN 'AN_HET' WHEN q % 100 < 97 THEN 'AN_IT'
                                              ELSE 'KHONG_AN' END)
           FROM (SELECT sess, abs(hashtext('me:' || sess || ':' || a.code || ':' || a.day)::bigint) AS q
                 FROM unnest(string_to_array(a.meal_sessions, ',')) AS sess
                 WHERE (NOT a.early OR sess = 'MORNING')
                   AND (a.kind = 'past' OR sess IN ('MORNING', 'LUNCH'))) m), '{}'::jsonb),
       CASE WHEN a.early OR a.kind = 'today' THEN NULL
            WHEN o.n % 100 < 82 THEN 'NGU_TOT' WHEN o.n % 100 < 95 THEN 'NGU_IT' ELSE 'KHONG_NGU' END,
       CASE WHEN a.early THEN 'SAD'
            WHEN a.day < DATE '2026-09-15' AND a.age_code IN ('ag-2', 'ag-3') AND o.e < 30
                THEN (ARRAY['SAD', 'UPSET'])[1 + o.e % 2]
            WHEN o.e < 60 THEN 'HAPPY' WHEN o.e < 90 THEN 'CALM' WHEN o.e < 96 THEN 'SAD' ELSE 'UPSET' END,
       CASE WHEN a.early THEN 'CHUA_TICH_CUC'
            WHEN o.q < 50 THEN 'TICH_CUC' WHEN o.q < 92 THEN 'BINH_THUONG' ELSE 'CHUA_TICH_CUC' END,
       b.behaviors, b.note,
       CASE WHEN a.kind = 'today' THEN NULL
            WHEN a.early THEN FALSE
            ELSE b.behaviors NOT LIKE '%TRANH_GIANH%' AND b.behaviors NOT LIKE '%CHUA_HOP_TAC%'
                 AND NOT (o.q >= 92 AND o.n % 2 = 0) END,
       CASE WHEN a.early THEN 'Bé sốt nên gia đình đón về sớm.'
            WHEN o.c < 10 THEN (ARRAY['Hôm nay bé tự xúc cơm ăn hết suất.',
                                      'Bé hăng hái giơ tay trả lời cô trong giờ học.',
                                      'Bé biết cất dọn đồ chơi gọn gàng sau giờ chơi.',
                                      'Bé thuộc bài thơ mới và đọc to cho cả lớp nghe.',
                                      'Bé chủ động chào cô và các bạn khi đến lớp.',
                                      'Bé ăn chậm, cô cần động viên thêm ở bữa trưa.',
                                      'Bé thích góc tạo hình, tô màu rất khéo.',
                                      'Bé nhường đồ chơi cho bạn nhỏ hơn.',
                                      'Bé còn nhút nhát khi tham gia trò chơi tập thể.',
                                      'Bé tự đi vệ sinh và rửa tay đúng cách.'])[1 + o.c]
            ELSE '' END,
       a.teacher_id, t.at, a.teacher_id, t.at
FROM d3_att a
CROSS JOIN LATERAL (SELECT abs(hashtext('on:' || a.code || ':' || a.day)::bigint) AS n,
                           abs(hashtext('oe:' || a.code || ':' || a.day)::bigint) % 100 AS e,
                           abs(hashtext('oq:' || a.code || ':' || a.day)::bigint) % 100 AS q,
                           abs(hashtext('ob:' || a.code || ':' || a.day)::bigint) % 100 AS bh,
                           abs(hashtext('oc:' || a.code || ':' || a.day)::bigint) % 100 AS c) o
CROSS JOIN LATERAL (
    SELECT CASE WHEN a.early THEN '' WHEN o.bh < 10 THEN 'HOP_TAC_TOT' WHEN o.bh < 15 THEN 'GIUP_DO_BAN'
                WHEN o.bh < 17 THEN 'HOP_TAC_TOT,GIUP_DO_BAN' WHEN o.bh < 20 THEN 'TRANH_GIANH'
                WHEN o.bh < 23 THEN 'KHONG_TAP_TRUNG' WHEN o.bh < 25 THEN 'CHUA_HOP_TAC' WHEN o.bh = 25 THEN 'RUT_RE'
                ELSE '' END AS behaviors,
           CASE WHEN a.early THEN ''
                WHEN o.bh BETWEEN 17 AND 19 THEN (ARRAY['Bé tranh đồ chơi với bạn ở góc xây dựng, cô đã nhắc nhở.',
                                                        'Bé giành bút màu của bạn, sau đó đã xin lỗi bạn.'])[1 + o.n % 2]
                WHEN o.bh BETWEEN 20 AND 22 THEN 'Bé hay quay ngang trong giờ học, cô nhắc bé chú ý.'
                WHEN o.bh BETWEEN 23 AND 24 THEN 'Bé chưa muốn xếp hàng cùng các bạn khi ra sân.'
                WHEN o.bh = 25 THEN 'Bé ngồi một mình, ít chơi với bạn, cô sẽ trao đổi thêm với gia đình.'
                ELSE '' END AS note) b
CROSS JOIN LATERAL (
    SELECT CASE WHEN a.kind = 'today'
                THEN (a.day + TIME '12:45' + (abs(hashtext('ot:' || a.classroom_id || ':' || a.day)::bigint) % 20)
                          * INTERVAL '1 minute')
                ELSE (a.day + TIME '15:35' + (abs(hashtext('ot:' || a.classroom_id || ':' || a.day)::bigint) % 35)
                          * INTERVAL '1 minute')
           END AT TIME ZONE 'Asia/Ho_Chi_Minh' AS at) t
WHERE a.status = 'PRESENT'
  AND (a.kind = 'past' OR (a.kind = 'today' AND a.campus_code = 'CS-A' AND o.n % 2 = 0))
ON CONFLICT (child_id, date) DO NOTHING;

-- Nhận xét chung của lớp mỗi ngày đã qua (OBS-19)
INSERT INTO class_daily_comments (classroom_id, campus_id, date, text, recorded_by, updated_at)
SELECT cd.classroom_id, cd.campus_id, cd.day,
       (ARRAY['Cả lớp hào hứng tham gia hoạt động học và hoạt động ngoài trời, các con ăn ngon miệng.',
              'Các con chơi hợp tác tốt ở góc chơi, giờ ngủ trưa đúng giờ, nề nếp lớp ổn định.',
              'Lớp thực hiện tốt giờ vệ sinh và ăn bán trú, vài bạn còn cần cô nhắc khi xếp hàng.',
              'Các con tích cực trong hoạt động tạo hình, biết chia sẻ đồ chơi với bạn.',
              'Buổi sáng trời mưa nên lớp chơi trong lớp, các con hứng thú với trò chơi dân gian.',
              'Các con thuộc bài hát mới, nhiều bạn mạnh dạn biểu diễn trước lớp.',
              'Giờ ăn trưa các con ăn hết suất, một số bạn ăn chậm cần cô động viên.',
              'Lớp đi dạo quanh sân trường, các con quan sát cây xanh và đặt nhiều câu hỏi.',
              'Một vài bạn còn quấy khi bố mẹ về, cô đã dỗ và cho các con chơi cùng bạn.',
              'Các con biết cất dép, cất ba lô đúng chỗ, tự lấy nước uống khi khát.',
              'Hoạt động làm quen với toán các con đếm và so sánh số lượng khá tốt.',
              'Các con tập thể dục sáng đều, giờ chiều chơi xếp hình theo nhóm vui vẻ.'])
           [1 + abs(hashtext('cm:' || cd.classroom_id || ':' || cd.day)::bigint) % 12],
       cd.teacher_id,
       (cd.day + TIME '16:05' + (abs(hashtext('ct:' || cd.classroom_id || ':' || cd.day)::bigint) % 35)
           * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM d3_class_day cd
WHERE cd.kind = 'past'
  AND EXISTS (SELECT 1 FROM d3_att a WHERE a.classroom_id = cd.classroom_id AND a.day = cd.day AND a.status = 'PRESENT')
ON CONFLICT (classroom_id, date) DO NOTHING;

-- 7. Yêu cầu đổi SĐT phụ huynh (AUTH-21): PHT/HT gửi, HT hoặc PHT điểm trường duyệt. Chờ duyệt: 3 (2 phụ huynh có tài
-- khoản, 1 chưa có); đã duyệt: 1 phụ huynh chưa có tài khoản (chỉ đổi SĐT hồ sơ) và 1 có tài khoản — số mới chính là SĐT /
-- tên đăng nhập hiện tại (khớp Keycloak), số cũ là số trước khi đổi; 1 từ chối; 1 hủy do bị yêu cầu sau thay thế
CREATE TEMP TABLE d3_pc_cand AS
SELECT ch.id AS child_id, ch.full_name AS child_name, cp.code AS campus_code, cl.campus_id,
       g0.id AS g0_id, g0.full_name AS g0_name, g0.phone AS g0_phone, g0.user_id AS g0_user,
       g1.id AS g1_id, g1.full_name AS g1_name, g1.phone AS g1_phone,
       row_number() OVER (PARTITION BY cp.code ORDER BY md5('pc:' || ch.code)) AS rn
FROM children ch
JOIN enrollments e ON e.child_id = ch.id AND e.valid_to IS NULL
JOIN classrooms cl ON cl.id = e.classroom_id
JOIN campuses cp ON cp.id = cl.campus_id
JOIN child_guardians g0 ON g0.child_id = ch.id AND g0.position = 0 AND g0.user_id IS NOT NULL
JOIN user_accounts u0 ON u0.id = g0.user_id AND u0.username = g0.phone
JOIN child_guardians g1 ON g1.child_id = ch.id AND g1.position = 1 AND g1.user_id IS NULL
WHERE ch.status = 'ACTIVE'
  -- Chỉ xét lớp năm học hiện tại (lớp năm cũ của trẻ học tiếp không làm đổi tập ứng viên khi chạy lại)
  AND NOT EXISTS (SELECT 1 FROM enrollments x WHERE x.child_id = ch.id AND x.valid_to >= DATE '2026-09-01')
  AND (SELECT count(*) FROM child_guardians z WHERE z.user_id = g0.user_id) = 1;

CREATE TEMP TABLE d3_pc AS
SELECT v.ord, v.status, c.child_id, c.child_name, c.campus_id,
       CASE WHEN v.target = 'G0' THEN c.g0_id ELSE c.g1_id END AS guardian_id,
       CASE WHEN v.target = 'G0' THEN c.g0_user END AS user_id,
       CASE WHEN v.target = 'G0' THEN c.g0_name ELSE c.g1_name END AS guardian_name,
       CASE WHEN v.target = 'G0' THEN c.g0_phone ELSE c.g1_phone END AS cur_phone,
       (ARRAY['0975', '0868', '0337', '0915', '0394'])[1 + abs(hashtext('np:' || c.child_id || ':' || v.ord)::bigint) % 5]
           || lpad((abs(hashtext('nd:' || c.child_id || ':' || v.ord)::bigint) % 1000000)::text, 6, '0') AS other_phone,
       CASE WHEN v.requester = 'P' THEN ht.user_id ELSE vp.user_id END AS requested_by,
       CASE WHEN v.decider = 'P' THEN ht.user_id WHEN v.decider = 'VP' THEN vp.user_id END AS decided_by,
       ((GREATEST(x.today - v.req_ago, DATE '2026-09-07') + TIME '09:10')
           + (abs(hashtext('pr:' || c.child_id || ':' || v.ord)::bigint) % 90) * INTERVAL '1 minute')
           AT TIME ZONE 'Asia/Ho_Chi_Minh' AS requested_at,
       CASE WHEN v.decider IS NOT NULL
            THEN (GREATEST(x.today - v.req_ago, DATE '2026-09-07') + 1 + TIME '08:40') AT TIME ZONE 'Asia/Ho_Chi_Minh'
       END AS decided_at,
       v.reason, v.note
FROM (VALUES
    (1, 'CS-A', 1, 'G0', 'CANCELLED', 'P', NULL, 3, 'Phụ huynh báo mất SIM, đăng ký số điện thoại mới.', NULL),
    (2, 'CS-A', 1, 'G0', 'PENDING', 'P', NULL, 1,
     'Lần trước ghi nhầm một chữ số, phụ huynh gửi lại số đúng.', NULL),
    (3, 'CS-B', 1, 'G0', 'PENDING', 'P', NULL, 2,
     'Phụ huynh chuyển sang dùng số của nhà mạng khác, số cũ sắp hết hạn.', NULL),
    (4, 'CS-B', 2, 'G1', 'PENDING', 'VP', NULL, 1, 'Bố bé đổi số điện thoại mới, nhờ nhà trường cập nhật hồ sơ.', NULL),
    (5, 'CS-A', 2, 'G1', 'APPROVED', 'VP', 'P', 30, 'Mẹ bé đổi số điện thoại do số cũ bị khóa.', NULL),
    (6, 'CS-B', 3, 'G0', 'APPROVED', 'VP', 'P', 24, 'Phụ huynh mất điện thoại, đã làm lại SIM với số mới.', NULL),
    (7, 'CS-A', 3, 'G0', 'REJECTED', 'P', 'VP', 12, 'Phụ huynh nhờ đổi sang số của người nhà để tiện liên lạc.',
     'Số mới là số của người nhà, không phải phụ huynh đứng tên. Đề nghị phụ huynh dùng số chính chủ.')
) AS v (ord, campus_code, rn, target, status, requester, decider, req_ago, reason, note)
CROSS JOIN d3_ctx x
JOIN d3_pc_cand c ON c.campus_code = v.campus_code AND c.rn = v.rn
JOIN LATERAL (SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'PRINCIPAL' AND a.valid_to IS NULL
              ORDER BY a.id LIMIT 1) ht ON TRUE
JOIN LATERAL (SELECT a.user_id FROM staff_assignments a
              WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = c.campus_id AND a.valid_to IS NULL
              ORDER BY a.id LIMIT 1) vp ON TRUE;

-- Đã duyệt: số hiện tại là số mới; còn lại: số hiện tại là số cũ. Số "khác" không được trùng SĐT / tên đăng nhập nào
ALTER TABLE d3_pc ADD COLUMN old_phone text, ADD COLUMN new_phone text;
UPDATE d3_pc
SET old_phone = CASE WHEN status = 'APPROVED' THEN other_phone ELSE cur_phone END,
    new_phone = CASE WHEN status = 'APPROVED' THEN cur_phone ELSE other_phone END;
DELETE FROM d3_pc p
WHERE EXISTS (SELECT 1 FROM user_accounts u WHERE u.username = p.other_phone)
   OR EXISTS (SELECT 1 FROM child_guardians g WHERE g.phone = p.other_phone);

INSERT INTO guardian_phone_change_requests (user_id, child_id, guardian_id, old_phone, new_phone, status, reason,
                                            requested_by, requested_at, decided_by, decided_at, decision_note,
                                            sms_status, created_at, updated_at)
SELECT p.user_id, p.child_id, p.guardian_id, p.old_phone, p.new_phone, p.status, p.reason, p.requested_by,
       p.requested_at, p.decided_by, p.decided_at, p.note,
       CASE WHEN p.status = 'APPROVED' AND p.user_id IS NOT NULL THEN 'SENT' END,
       p.requested_at,
       COALESCE(p.decided_at,
                CASE WHEN p.status = 'CANCELLED'
                     THEN (SELECT q.requested_at FROM d3_pc q WHERE q.guardian_id = p.guardian_id AND q.status = 'PENDING')
                END,
                p.requested_at)
FROM d3_pc p
WHERE NOT EXISTS (
    SELECT 1 FROM guardian_phone_change_requests r
    WHERE r.child_id = p.child_id AND r.guardian_id = p.guardian_id AND r.new_phone = p.new_phone)
  AND (p.status <> 'PENDING' OR NOT EXISTS (
      SELECT 1 FROM guardian_phone_change_requests r WHERE r.guardian_id = p.guardian_id AND r.status = 'PENDING'))
ORDER BY p.ord;

-- 8. SMS tài khoản phụ huynh: kích hoạt lúc liên kết tài khoản (số = tên đăng nhập lúc đó); vài tin lỗi được cấp lại
-- mật khẩu sáng hôm sau; tin đổi tên đăng nhập sau khi duyệt đổi SĐT
INSERT INTO parent_sms_logs (user_id, phone, purpose, status, sent_at)
SELECT u.id, COALESCE(pc.old_phone, u.username), 'ACTIVATION',
       CASE WHEN abs(hashtext('sm:' || u.id)::bigint) % 50 = 0 THEN 'FAILED' ELSE 'SENT' END,
       l.linked_at + (abs(hashtext('st:' || u.id)::bigint) % 20) * INTERVAL '1 minute'
FROM user_accounts u
JOIN LATERAL (SELECT min(g.linked_at) AS linked_at FROM child_guardians g WHERE g.user_id = u.id) l
    ON l.linked_at IS NOT NULL
LEFT JOIN guardian_phone_change_requests pc ON pc.user_id = u.id AND pc.status = 'APPROVED'
WHERE u.primary_role = 'PARENT'
  AND NOT EXISTS (SELECT 1 FROM parent_sms_logs s WHERE s.user_id = u.id AND s.purpose = 'ACTIVATION');

INSERT INTO parent_sms_logs (user_id, phone, purpose, status, sent_at)
SELECT s.user_id, s.phone, 'PASSWORD_RESET', 'SENT',
       ((s.sent_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 1 + TIME '08:20') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM parent_sms_logs s
WHERE s.purpose = 'ACTIVATION' AND s.status = 'FAILED'
  AND NOT EXISTS (SELECT 1 FROM parent_sms_logs x WHERE x.user_id = s.user_id AND x.purpose = 'PASSWORD_RESET');

INSERT INTO parent_sms_logs (user_id, phone, purpose, status, sent_at)
SELECT pc.user_id, pc.new_phone, 'USERNAME_CHANGED', 'SENT', pc.decided_at + INTERVAL '1 minute'
FROM guardian_phone_change_requests pc
JOIN d3_pc p ON p.guardian_id = pc.guardian_id AND p.new_phone = pc.new_phone
WHERE pc.status = 'APPROVED' AND pc.user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM parent_sms_logs x
      WHERE x.user_id = pc.user_id AND x.purpose = 'USERNAME_CHANGED' AND x.phone = pc.new_phone);

-- 9. Thông báo theo đúng nơi code tạo ra
-- Bé đã được đón (PCK-04): mỗi tài khoản phụ huynh của trẻ; ngày cũ phần lớn đã đọc, 3 ngày gần nhất phần lớn chưa đọc
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT g.user_id, 'CHILD_PICKED_UP', left('Bé ' || ch.full_name || ' đã được đón', 150),
       left('Bé ' || ch.full_name || ' đã được đón lúc '
                || to_char(p.handed_over_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI') || ' ngày '
                || to_char(p.date, 'DD/MM/YYYY') || '.'
                || CASE WHEN p.verification = 'PHOTO_MATCH'
                        THEN ' Người đón: ' || p.pickup_person_name || ' ('
                             || CASE p.pickup_person_relationship WHEN 'FATHER' THEN 'Bố' WHEN 'MOTHER' THEN 'Mẹ'
                                    WHEN 'GRANDFATHER' THEN 'Ông' WHEN 'GRANDMOTHER' THEN 'Bà'
                                    WHEN 'SIBLING' THEN 'Anh/chị' WHEN 'GUARDIAN' THEN 'Người giám hộ'
                                    ELSE 'Khác' END || ').'
                        ELSE ' Người đón đã được phụ huynh xác nhận qua điện thoại.' END, 500),
       p.child_id, p.recorded_at,
       CASE WHEN r.r % 100 < CASE WHEN p.date >= x.today - 3 THEN 35 ELSE 95 END
            THEN p.recorded_at + (5 + r.r % 120) * INTERVAL '1 minute' END
FROM pickup_records p
CROSS JOIN d3_ctx x
JOIN d3_pick dp ON dp.child_id = p.child_id AND dp.day = p.date
JOIN children ch ON ch.id = p.child_id
JOIN child_guardians g ON g.child_id = p.child_id AND g.user_id IS NOT NULL
CROSS JOIN LATERAL (SELECT abs(hashtext('nr:' || g.user_id || ':' || p.id)::bigint) AS r) r
WHERE p.outcome = 'HANDED_OVER'
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.user_id = g.user_id AND n.type = 'CHILD_PICKED_UP' AND n.child_id = p.child_id
        AND n.created_at = p.recorded_at);

-- Mở lại điểm danh (ATT-08): giáo viên đang dạy lớp lúc mở; lần đang mở chưa đọc
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT a.user_id, 'ATTENDANCE_DAY_UNLOCKED', left('Mở lại điểm danh ' || u.class_name, 150),
       left('Điểm danh ngày ' || to_char(u.day, 'DD/MM/YYYY') || ' được mở lại để kiểm tra, sửa. Lý do: ' || u.reason, 500),
       NULL, u.unlocked_at,
       CASE WHEN NOT u.active AND u.day < (SELECT today FROM d3_ctx) - 7 THEN u.unlocked_at + INTERVAL '8 minutes' END
FROM d3_unlock u
JOIN staff_assignments a ON a.classroom_id = u.classroom_id AND a.staff_role = 'TEACHER'
    AND a.valid_from <= u.day AND (a.valid_to IS NULL OR a.valid_to >= u.day)
WHERE NOT EXISTS (
    SELECT 1 FROM notifications n
    WHERE n.user_id = a.user_id AND n.type = 'ATTENDANCE_DAY_UNLOCKED' AND n.created_at = u.unlocked_at);

-- Yêu cầu đổi SĐT: người duyệt (HT + PHT điểm trường của trẻ, trừ người gửi); kết quả báo người gửi khi người khác quyết
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT ap.user_id, 'GUARDIAN_PHONE_CHANGE_REQUESTED', 'Yêu cầu đổi số điện thoại phụ huynh',
       left(rq.full_name || ' đề nghị đổi '
                || CASE WHEN r.user_id IS NULL THEN 'số điện thoại của ' || g.full_name
                        ELSE 'số điện thoại đăng nhập của ' || acc.full_name END
                || ' (phụ huynh của ' || ch.full_name || '). Duyệt ở màn Tài khoản phụ huynh.', 500),
       r.child_id, r.requested_at,
       CASE WHEN r.status <> 'PENDING' THEN r.requested_at + INTERVAL '50 minutes' END
FROM guardian_phone_change_requests r
JOIN d3_pc p ON p.guardian_id = r.guardian_id AND p.new_phone = r.new_phone
JOIN children ch ON ch.id = r.child_id
JOIN child_guardians g ON g.id = r.guardian_id
LEFT JOIN user_accounts acc ON acc.id = r.user_id
JOIN user_accounts rq ON rq.id = r.requested_by
CROSS JOIN LATERAL (
    SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'PRINCIPAL' AND a.valid_to IS NULL
    UNION
    SELECT a.user_id FROM staff_assignments a
    WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = p.campus_id AND a.valid_to IS NULL) ap
WHERE ap.user_id <> r.requested_by
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.user_id = ap.user_id AND n.type = 'GUARDIAN_PHONE_CHANGE_REQUESTED' AND n.child_id = r.child_id
        AND n.created_at = r.requested_at)
UNION ALL
SELECT r.requested_by, 'GUARDIAN_PHONE_CHANGE_DECIDED',
       CASE WHEN r.status = 'APPROVED' THEN 'Đã duyệt đổi số điện thoại phụ huynh'
            ELSE 'Không duyệt đổi số điện thoại phụ huynh' END,
       left(CASE WHEN r.status = 'APPROVED' AND r.user_id IS NOT NULL
                     THEN dc.full_name || ' đã duyệt đổi số điện thoại đăng nhập của ' || acc.full_name
                          || '. Tài khoản mới đã gửi qua SMS tới số mới.'
                 WHEN r.status = 'APPROVED' THEN dc.full_name || ' đã duyệt đổi số điện thoại của ' || g.full_name || '.'
                 ELSE dc.full_name || ' không duyệt đổi số điện thoại của ' || COALESCE(acc.full_name, g.full_name)
                      || COALESCE(': ' || r.decision_note, '.') END, 500),
       r.child_id, r.decided_at, r.decided_at + INTERVAL '2 hours'
FROM guardian_phone_change_requests r
JOIN d3_pc p ON p.guardian_id = r.guardian_id AND p.new_phone = r.new_phone
JOIN child_guardians g ON g.id = r.guardian_id
LEFT JOIN user_accounts acc ON acc.id = r.user_id
JOIN user_accounts dc ON dc.id = r.decided_by
WHERE r.status IN ('APPROVED', 'REJECTED') AND r.decided_by <> r.requested_by
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.user_id = r.requested_by AND n.type = 'GUARDIAN_PHONE_CHANGE_DECIDED' AND n.child_id = r.child_id
        AND n.created_at = r.decided_at);

-- Trẻ nghỉ học (CHD-03) và trẻ chuyển vào lớp (CHD-10): giáo viên lớp lúc đó, theo lịch sử hồ sơ trẻ của file nền
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT a.user_id, 'CHILD_WITHDRAWN', 'Trẻ nghỉ học',
       ch.full_name || ' nghỉ học từ ngày ' || to_char(ch.left_on, 'DD/MM/YYYY') || '.', ch.id, h.created_at,
       h.created_at + INTERVAL '40 minutes'
FROM child_history h
JOIN children ch ON ch.id = h.child_id AND ch.left_on IS NOT NULL
JOIN enrollments e ON e.child_id = ch.id AND e.valid_from <= ch.left_on AND e.valid_to >= ch.left_on
JOIN staff_assignments a ON a.classroom_id = e.classroom_id AND a.staff_role = 'TEACHER'
    AND a.valid_from <= ch.left_on AND (a.valid_to IS NULL OR a.valid_to >= ch.left_on)
WHERE h.action = 'WITHDRAWN'
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.user_id = a.user_id AND n.type = 'CHILD_WITHDRAWN' AND n.child_id = ch.id)
UNION ALL
SELECT a.user_id, 'CHILD_PLACED', 'Lớp có trẻ mới', ch.full_name || ' đã được xếp vào ' || cl.name || '.', ch.id,
       pl.placed_at, pl.placed_at + INTERVAL '30 minutes'
FROM child_placements pl
JOIN children ch ON ch.id = pl.child_id
JOIN classrooms cl ON cl.id = pl.to_classroom_id
JOIN staff_assignments a ON a.classroom_id = cl.id AND a.staff_role = 'TEACHER'
    AND a.valid_from <= (pl.placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
    AND (a.valid_to IS NULL OR a.valid_to >= (pl.placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
WHERE pl.from_classroom_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.user_id = a.user_id AND n.type = 'CHILD_PLACED' AND n.child_id = ch.id AND n.created_at = pl.placed_at);

DROP TABLE d3_pc;
DROP TABLE d3_pc_cand;
DROP TABLE d3_pick;
DROP TABLE d3_health;
DROP TABLE d3_att_src;
DROP TABLE d3_unlock_fix;
DROP TABLE d3_unlock;
DROP TABLE d3_leave;
DROP TABLE d3_att;
DROP TABLE d3_class_day;
DROP TABLE d3_days;
DROP TABLE d3_ctx;

-- 9. Yêu cầu sửa khai báo sức khỏe của GV chủ nhiệm (CHD-11): một yêu cầu đã bị HT từ chối, GV thấy lý do ở màn khai
-- báo. Không seed yêu cầu chờ duyệt: payload chờ duyệt phải là khai báo đầy đủ hợp lệ mới duyệt được.
INSERT INTO child_profile_change_requests (child_id, kind, payload, status, requested_by, requested_at, decided_by,
                                           decided_at, decision_note)
SELECT e.child_id,
       'HEALTH_DECLARATION',
       jsonb_build_object('declaration', jsonb_build_object(
                                  'allergies', jsonb_build_object('state', 'REPORTED', 'items', jsonb_build_array('Tôm')),
                                  'diet', jsonb_build_object('state', 'NOT_PROVIDED', 'text', ''),
                                  'otherNotes', jsonb_build_object('state', 'NOT_PROVIDED', 'text', '')),
                          'reason', 'Phụ huynh báo bé dị ứng tôm'),
       'REJECTED',
       sa.user_id,
       (CURRENT_DATE - 2) + TIME '15:10',
       (SELECT u.id FROM user_accounts u WHERE u.primary_role = 'PRINCIPAL' ORDER BY u.id LIMIT 1),
       (CURRENT_DATE - 1) + TIME '08:20',
       'Chưa có xác nhận của phụ huynh, đề nghị gửi kèm giấy khám dị ứng'
FROM staff_assignments sa
JOIN enrollments e ON e.classroom_id = sa.classroom_id AND e.valid_to IS NULL
WHERE sa.staff_role = 'TEACHER' AND sa.is_homeroom AND sa.valid_to IS NULL
  AND EXISTS (SELECT 1 FROM user_accounts u WHERE u.primary_role = 'PRINCIPAL')
  AND NOT EXISTS (SELECT 1 FROM child_profile_change_requests r WHERE r.kind = 'HEALTH_DECLARATION')
ORDER BY sa.user_id, e.child_id
LIMIT 1;
