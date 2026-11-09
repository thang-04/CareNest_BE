-- DỮ LIỆU GIẢ CHỈ CHO DEV/DEMO (profile "dev"): vận hành 19/10–04/11/2026 của MN Thượng Hồng — điểm danh, sức khỏe
-- đầu ngày, sổ báo ăn đã xác nhận, trừ kho khô theo thực đơn tuần đã công bố. Chạy sau ..._catalog.sql (cần thực đơn
-- tuần, lô kho, trẻ, giáo viên). Ngày 05/11 (hôm nay) để trống cho người dùng tự thao tác.
-- Số liệu sinh theo hàm băm của mã trẻ + ngày (cố định, không ngẫu nhiên): ~6% vắng (có phép khi trẻ có tài khoản phụ
-- huynh, còn lại không phép), ~3% trẻ có mặt có biểu hiện cần lưu ý. Idempotent: ON CONFLICT/NOT EXISTS theo khóa tự nhiên, không ghi đè dữ liệu có sẵn.

-- 1. Lịch năm học: Quốc khánh 02/09; giờ chốt điểm danh/báo ăn (chỉ thêm khi năm học chưa có giờ chốt, HT chỉnh lại sau)
INSERT INTO school_holidays (school_year_id, start_date, end_date, name)
SELECT y.id, DATE '2026-09-02', DATE '2026-09-02', 'Quốc khánh'
FROM school_years y
WHERE y.name = '2026-2027'
  AND NOT EXISTS (SELECT 1 FROM school_holidays h WHERE h.school_year_id = y.id AND h.start_date = DATE '2026-09-02');

INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from, note, created_by)
SELECT y.id, TIME '09:00', DATE '2026-09-01', 'Giờ chốt demo, Hiệu trưởng chỉnh lại ở Cấu hình thời gian', u.id
FROM school_years y
JOIN user_accounts u ON u.username = 'principal'
WHERE y.name = '2026-2027'
  AND NOT EXISTS (SELECT 1 FROM attendance_cutoffs c WHERE c.school_year_id = y.id);

-- 2. Ngày học trong kỳ seed: thứ 2–6, trừ ngày nghỉ
CREATE TEMP TABLE th_ops_days AS
SELECT d::date AS day
FROM generate_series(DATE '2026-10-19', DATE '2026-11-04', INTERVAL '1 day') AS d
WHERE EXTRACT(ISODOW FROM d) < 6
  AND NOT EXISTS (
      SELECT 1 FROM school_holidays h
      WHERE d::date BETWEEN h.start_date AND h.end_date
        AND h.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027'));

-- Mỗi trẻ đang học × ngày: lớp, điểm trường, GV ghi (GV chủ nhiệm đang dạy), trạng thái theo băm, bữa ăn theo nhóm tuổi
CREATE TEMP TABLE th_ops_att AS
SELECT ch.id AS child_id, cl.id AS classroom_id, cl.campus_id, dd.day, t.user_id AS teacher_id,
       -- Vắng có phép đến từ đơn nghỉ của phụ huynh (ATT-04) nên chỉ trẻ có tài khoản phụ huynh; còn lại vắng không phép
       CASE WHEN h.r >= 6 THEN 'PRESENT'
            WHEN h.r < 4 AND EXISTS (SELECT 1 FROM child_guardians l WHERE l.child_id = ch.id AND l.user_id IS NOT NULL) THEN 'EXCUSED'
            ELSE 'UNEXCUSED' END AS status,
       CASE WHEN h.r < 6 THEN '' ELSE g.meal_sessions END AS meals,
       (dd.day + TIME '08:00' + (h.m % 30) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS recorded_at,
       h.s AS sick_roll
FROM th_ops_days dd
JOIN enrollments e ON e.valid_from <= dd.day AND (e.valid_to IS NULL OR e.valid_to >= dd.day)
JOIN classrooms cl ON cl.id = e.classroom_id AND cl.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027')
JOIN age_groups g ON g.id = cl.age_group_id
JOIN children ch ON ch.id = e.child_id
CROSS JOIN LATERAL (SELECT abs(hashtext(ch.code || dd.day::text)::bigint) % 100 AS r,
                           abs(hashtext('m' || ch.code || dd.day::text)::bigint) AS m,
                           abs(hashtext('s' || ch.code || dd.day::text)::bigint) % 100 AS s) h
JOIN LATERAL (
    SELECT a.user_id FROM staff_assignments a
    WHERE a.classroom_id = cl.id AND a.staff_role = 'TEACHER' AND a.valid_from <= dd.day
      AND (a.valid_to IS NULL OR a.valid_to >= dd.day)
    ORDER BY a.is_homeroom DESC, a.id
    LIMIT 1) t ON TRUE;

-- 3. Điểm danh + lịch sử ghi
INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals, recorded_by, recorded_at)
SELECT a.child_id, a.classroom_id, a.campus_id, a.day, a.status, a.meals, a.teacher_id, a.recorded_at
FROM th_ops_att a
ON CONFLICT (child_id, date) DO NOTHING;

INSERT INTO attendance_record_history (record_id, action, status, meals, user_id, at, child_id, classroom_id, campus_id, date)
SELECT r.id, 'RECORDED', r.status, r.meals, r.recorded_by, r.recorded_at, r.child_id, r.classroom_id, r.campus_id, r.date
FROM attendance_records r
JOIN th_ops_att a ON a.child_id = r.child_id AND a.day = r.date
WHERE NOT EXISTS (SELECT 1 FROM attendance_record_history h WHERE h.record_id = r.id);

-- 4. Sức khỏe đầu ngày của trẻ có mặt: bình thường 36,4–36,9 °C; ~3% có biểu hiện (sốt nhẹ/ho/sổ mũi), GV theo dõi
-- và đã báo phụ huynh. Giáo viên quan sát, không chẩn đoán (HLT-07).
INSERT INTO daily_health_entries (request_id, child_id, classroom_id, campus_id, date, temperature, observation,
                                  symptoms, note, actions, recorded_by, recorded_at)
SELECT 'seed-' || a.child_id || '-' || to_char(a.day, 'YYYYMMDD'), a.child_id, a.classroom_id, a.campus_id, a.day,
       CASE WHEN a.sick_roll < 3 THEN 37.5 + (a.sick_roll % 3) / 10.0 ELSE 36.4 + (a.sick_roll % 6) / 10.0 END,
       CASE WHEN a.sick_roll < 3 THEN 'NOTED' ELSE 'NO_CONCERN' END,
       CASE a.sick_roll WHEN 0 THEN 'FEVER' WHEN 1 THEN 'COUGH' WHEN 2 THEN 'RUNNY_NOSE' ELSE '' END,
       '',
       CASE WHEN a.sick_roll < 3 THEN 'MONITOR,PARENT_INFORMED' ELSE '' END,
       a.teacher_id, a.recorded_at + INTERVAL '5 minutes'
FROM th_ops_att a
WHERE a.status = 'PRESENT'
ON CONFLICT (recorded_by, request_id) DO NOTHING;

-- 5. Sổ báo ăn đã xác nhận: mỗi điểm trường × ngày × bữa; PHT điểm trường tổng hợp 09:05, xác nhận 09:30
INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, needs_reconfirmation, aggregated_at,
                         confirmed_by, confirmed_at, confirmed_by_role)
SELECT c.id, y.id, dd.day, s.session, 'CONFIRMED', FALSE,
       (dd.day + TIME '09:05') AT TIME ZONE 'Asia/Ho_Chi_Minh', pht.id,
       (dd.day + TIME '09:30') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'VICE_PRINCIPAL'
FROM th_ops_days dd
CROSS JOIN campuses c
JOIN school_years y ON y.name = '2026-2027'
CROSS JOIN (VALUES ('MORNING'), ('LUNCH'), ('AFTERNOON')) AS s (session)
JOIN user_accounts pht ON pht.username = CASE c.code WHEN 'CS-A' THEN 'viceprincipal' ELSE 'th.pht.b' END
WHERE c.code IN ('CS-A', 'CS-B')
  AND EXISTS (SELECT 1 FROM attendance_records r WHERE r.campus_id = c.id AND r.date = dd.day)
ON CONFLICT (campus_id, date, session) DO NOTHING;

-- Dòng theo lớp: chỉ lớp có bữa đó (nhà trẻ có bữa sáng); suất thường = số trẻ có mặt (ATT-02), không suất thay thế
INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal, substitute)
SELECT mc.id, cl.id, FALSE,
       count(*) FILTER (WHERE r.status = 'PRESENT'), count(*) FILTER (WHERE r.status <> 'PRESENT'), 0,
       count(*) FILTER (WHERE r.status = 'PRESENT'), 0
FROM meal_counts mc
JOIN th_ops_days dd ON dd.day = mc.date
JOIN classrooms cl ON cl.campus_id = mc.campus_id AND cl.school_year_id = mc.school_year_id
JOIN age_groups g ON g.id = cl.age_group_id AND (',' || g.meal_sessions || ',') LIKE '%,' || mc.session || ',%'
JOIN attendance_records r ON r.classroom_id = cl.id AND r.date = mc.date
WHERE mc.status = 'CONFIRMED'
  AND NOT EXISTS (SELECT 1 FROM meal_count_lines l WHERE l.meal_count_id = mc.id)
GROUP BY mc.id, cl.id;

INSERT INTO meal_count_history (meal_count_id, action, user_id, at)
SELECT mc.id, v.action, CASE v.action WHEN 'CONFIRMED' THEN mc.confirmed_by END,
       CASE v.action WHEN 'CONFIRMED' THEN mc.confirmed_at ELSE mc.aggregated_at END
FROM meal_counts mc
JOIN th_ops_days dd ON dd.day = mc.date
CROSS JOIN (VALUES ('AGGREGATED'), ('CONFIRMED')) AS v (action)
WHERE mc.status = 'CONFIRMED'
  AND NOT EXISTS (SELECT 1 FROM meal_count_history h WHERE h.meal_count_id = mc.id);

-- 6. Trừ kho khô khi suất ngày được chốt (NUT-11, P-38): nhu cầu = Σ định lượng × suất của món trong thực đơn tuần đã
-- công bố, theo khối thực đơn của nhóm tuổi (≤36 tháng là nhà trẻ — MenuBlock), chia (1 − thải bỏ%); trừ lô hết hạn trước
CREATE TEMP TABLE th_ops_demand AS
SELECT p.campus_id, p.date AS meal_date, di.food_id,
       round(sum(di.grams_per_child * p.portions) / (1 - max(f.waste_pct) / 100.0), 2) AS qty
FROM (SELECT mc.campus_id, mc.date, mc.session, cl.age_group_id, sum(l.normal + l.substitute) AS portions
      FROM meal_counts mc
      JOIN th_ops_days dd ON dd.day = mc.date
      JOIN meal_count_lines l ON l.meal_count_id = mc.id AND NOT l.pending
      JOIN classrooms cl ON cl.id = l.classroom_id
      WHERE mc.status = 'CONFIRMED'
      GROUP BY mc.campus_id, mc.date, mc.session, cl.age_group_id) p
JOIN age_groups g ON g.id = p.age_group_id
JOIN meal_plan_days d ON d.menu_date = p.date AND NOT d.off
JOIN meal_plan_weeks w ON w.id = d.week_id AND w.status = 'PUBLISHED'
JOIN meal_plan_items i ON i.day_id = d.id AND i.session = p.session
    AND i.menu_block = CASE WHEN g.age_to_months <= 36 THEN 'NURSERY' ELSE 'KINDERGARTEN' END
JOIN dish_ingredients di ON di.dish_id = i.dish_id AND di.age_group_id = p.age_group_id
JOIN foods f ON f.id = di.food_id AND f.storage = 'DRY'
GROUP BY p.campus_id, p.date, di.food_id;

INSERT INTO stock_daily_demands (campus_id, meal_date, food_id, required_qty, shortfall_qty)
SELECT d.campus_id, d.meal_date, d.food_id, d.qty, 0
FROM th_ops_demand d
ON CONFLICT (campus_id, meal_date, food_id) DO NOTHING;

INSERT INTO stock_movements (lot_id, campus_id, food_id, movement_type, quantity, meal_date, reason)
SELECT l.id, d.campus_id, d.food_id, 'DEDUCT', -d.qty, d.meal_date, ''
FROM th_ops_demand d
JOIN LATERAL (
    SELECT x.id FROM stock_lots x
    WHERE x.campus_id = d.campus_id AND x.food_id = d.food_id AND x.expiry_date >= d.meal_date
    ORDER BY x.expiry_date, x.id
    LIMIT 1) l ON TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM stock_movements m
    WHERE m.campus_id = d.campus_id AND m.food_id = d.food_id AND m.meal_date = d.meal_date
      AND m.movement_type = 'DEDUCT');

DROP TABLE th_ops_demand;
DROP TABLE th_ops_att;
DROP TABLE th_ops_days;
