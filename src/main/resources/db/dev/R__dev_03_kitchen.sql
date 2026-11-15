-- Bộ dữ liệu dev (profile "dev"): bán trú vận hành — thực đơn tuần, sổ báo ăn, kho khô, hàng tươi, lưu mẫu thức ăn,
-- khung giờ ảnh bữa ăn. Dữ liệu giả nhìn như thật. Chạy sau R__dev_02_catalog.sql và R__dev_03_daily.sql (đọc điểm danh
-- thật để tính suất). Neo CURRENT_DATE.
--
-- Thực đơn tuần sao từ bộ mẫu 4 tuần (xoay vòng từ tuần 31/08/2026): công bố mọi tuần tới hết tuần sau, tuần kế tiếp là
-- bản nháp chờ công bố. Sổ báo ăn đã xác nhận cho mọi ngày học đã qua; hôm nay chỉ cơ sở đã điểm danh đủ (Cơ sở 1) có sổ
-- đã xác nhận, cơ sở còn lại để Sổ báo ăn tự tổng hợp khi mở sau giờ chốt. Kho khô nhập 2 tuần/lần và trừ theo đúng cách
-- tính của MealDemandCalculator (suất đã chốt × định lượng ÷ (1 − thải bỏ%)), lô hết hạn trước dùng trước, không âm.
-- Không seed ảnh bữa ăn (meal_photos.storage_key bắt buộc) và biên bản kiểm thực (ký cần tệp ảnh chữ ký; bản nháp do
-- màn hình tự dựng từ dữ liệu nhận hàng, lưu mẫu).
-- Idempotent: ON CONFLICT / NOT EXISTS theo khóa tự nhiên; chạy lại ngày sau chỉ bù tuần, ngày còn thiếu.

-- 0. Ngữ cảnh: hôm nay, năm học, người thao tác (tra theo phân công)
CREATE TEMP TABLE k3_ctx AS
SELECT CURRENT_DATE AS today,
       CURRENT_DATE - (EXTRACT(ISODOW FROM CURRENT_DATE)::int - 1) AS this_monday,
       y.id AS year_id, y.start_date, y.end_date,
       (SELECT a.user_id FROM staff_assignments a JOIN campuses c ON c.id = a.campus_id
        WHERE a.staff_role = 'VICE_PRINCIPAL' AND c.is_main AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
        ORDER BY a.id LIMIT 1) AS pht_main,
       (SELECT a.user_id FROM staff_assignments a
        WHERE a.staff_role = 'PRINCIPAL' AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
        ORDER BY a.id LIMIT 1) AS principal_id
FROM school_years y
WHERE y.name = '2026-2027';

CREATE TEMP TABLE k3_staff AS
SELECT c.id AS campus_id, c.code, c.is_main,
       (SELECT a.user_id FROM staff_assignments a
        WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = c.id AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
        ORDER BY a.id LIMIT 1) AS vp_id,
       (SELECT a.user_id FROM staff_assignments a
        WHERE a.staff_role = 'KITCHEN_STAFF' AND a.campus_id = c.id AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
        ORDER BY a.id LIMIT 1) AS kitchen_id
FROM campuses c
WHERE c.code IN ('CS-A', 'CS-B');

-- Thứ 2–6 từ tuần 31/08 tới hết tuần nháp; off_note khác NULL = ngày không phục vụ bữa (trước năm học, ngày nghỉ)
CREATE TEMP TABLE k3_days AS
SELECT s.day,
       s.day - (EXTRACT(ISODOW FROM s.day)::int - 1) AS week_start,
       CASE WHEN s.day < x.start_date THEN 'Chuẩn bị năm học mới, trẻ chưa đến trường' ELSE h.name END AS off_note
FROM k3_ctx x
CROSS JOIN LATERAL (SELECT d::date AS day
                    FROM generate_series(DATE '2026-08-31', x.this_monday + 18, INTERVAL '1 day') AS d) s
LEFT JOIN LATERAL (SELECT h.name FROM school_holidays h
                   WHERE h.school_year_id = x.year_id AND s.day BETWEEN h.start_date AND h.end_date
                   ORDER BY h.start_date LIMIT 1) h ON TRUE
WHERE EXTRACT(ISODOW FROM s.day) < 6;

-- Tuần thực đơn: tuần mẫu xoay vòng 1..4; công bố tới hết tuần sau, tuần sau nữa là nháp
CREATE TEMP TABLE k3_weeks AS
SELECT d.week_start,
       ((d.week_start - DATE '2026-08-31') / 7) % 4 + 1 AS week_no,
       CASE WHEN d.week_start <= x.this_monday + 7 THEN 'PUBLISHED' ELSE 'DRAFT' END AS status,
       jsonb_agg(jsonb_build_object('date', to_char(d.day, 'YYYY-MM-DD'), 'off', d.off_note IS NOT NULL,
                                    'note', coalesce(d.off_note, '')) ORDER BY d.day) AS days
FROM k3_days d
CROSS JOIN k3_ctx x
GROUP BY d.week_start, x.this_monday;

-- 1. Khung giờ đăng ảnh bữa ăn (chung toàn trường, NUT-16): chỉ đặt khi trường chưa cấu hình
UPDATE edu_settings s
SET meal_photo_windows = jsonb_build_array(
        jsonb_build_object('session', 'MORNING', 'startTime', '07:30:00', 'endTime', '08:30:00',
                           'updatedBy', x.principal_id, 'updatedAt', '2026-08-25T02:00:00Z'),
        jsonb_build_object('session', 'LUNCH', 'startTime', '10:30:00', 'endTime', '11:45:00',
                           'updatedBy', x.principal_id, 'updatedAt', '2026-08-25T02:00:00Z'),
        jsonb_build_object('session', 'AFTERNOON', 'startTime', '14:15:00', 'endTime', '15:30:00',
                           'updatedBy', x.principal_id, 'updatedAt', '2026-08-25T02:00:00Z'))
FROM k3_ctx x
WHERE s.id = 1
  AND s.meal_photo_windows = '[]'::jsonb;

-- 2. Thực đơn tuần. Bản nháp do seed tạo mà chưa ai sửa (updated_at = created_at) tới lượt tuần sau thì công bố
UPDATE meal_plan_weeks w
SET status = 'PUBLISHED', published_by = w.created_by, updated_by = w.created_by,
    published_at = LEAST((w.week_start - 3 + TIME '15:30') AT TIME ZONE 'Asia/Ho_Chi_Minh', now() - INTERVAL '1 hour'),
    updated_at = LEAST((w.week_start - 3 + TIME '15:30') AT TIME ZONE 'Asia/Ho_Chi_Minh', now() - INTERVAL '1 hour')
FROM k3_ctx x
WHERE w.status = 'DRAFT'
  AND w.week_start <= x.this_monday + 7
  AND w.created_by = x.pht_main
  AND w.updated_at = w.created_at;

-- PHT dinh dưỡng (cơ sở chính) lập thứ Năm tuần trước nữa, công bố chiều thứ Sáu trước tuần ăn
INSERT INTO meal_plan_weeks (week_start, status, published_by, published_at, created_by, updated_by, created_at,
                             updated_at, days)
SELECT k.week_start, k.status,
       CASE WHEN k.status = 'PUBLISHED' THEN x.pht_main END,
       CASE WHEN k.status = 'PUBLISHED' THEN t.published_at END,
       x.pht_main, x.pht_main, t.created_at,
       CASE WHEN k.status = 'PUBLISHED' THEN t.published_at ELSE t.created_at END,
       k.days
FROM k3_weeks k
CROSS JOIN k3_ctx x
CROSS JOIN LATERAL (
    SELECT LEAST((k.week_start - 11 + TIME '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', now() - INTERVAL '3 hours') AS created_at,
           LEAST((k.week_start - 3 + TIME '15:30') AT TIME ZONE 'Asia/Ho_Chi_Minh', now() - INTERVAL '1 hour') AS published_at) t
ON CONFLICT (week_start) DO NOTHING;

-- Món của tuần: sao từ bộ mẫu theo khối; chỉ điền tuần chưa có món nào (không đè tuần người dùng đã sửa)
INSERT INTO meal_plan_items (week_id, menu_date, session, menu_block, dish_id, sort_order)
SELECT w.id, d.day, t.session, s.menu_block, t.dish_id, t.sort_order
FROM meal_plan_weeks w
JOIN k3_weeks k ON k.week_start = w.week_start
JOIN k3_days d ON d.week_start = w.week_start AND d.off_note IS NULL
JOIN menu_template_sets s ON s.name IN ('Thực đơn 4 tuần năm học 2026-2027 - Nhà trẻ',
                                        'Thực đơn 4 tuần năm học 2026-2027 - Mẫu giáo')
JOIN menu_template_items t ON t.set_id = s.id AND t.week_no = k.week_no
    AND t.weekday = EXTRACT(ISODOW FROM d.day)::int
WHERE NOT EXISTS (SELECT 1 FROM meal_plan_items i WHERE i.week_id = w.id)
ON CONFLICT (week_id, menu_date, session, menu_block, dish_id) DO NOTHING;

-- 3. Sổ báo ăn từ điểm danh thật (ATT-02: có mặt ăn mọi bữa của nhóm tuổi; trẻ có dị ứng trong hồ sơ ăn suất thay thế)
CREATE TEMP TABLE k3_class_day AS
SELECT d.day, cl.id AS classroom_id, cl.campus_id, cl.age_group_id, g.meal_sessions,
       count(e.child_id)::int AS enrolled,
       (count(r.id) FILTER (WHERE r.status = 'PRESENT'))::int AS present,
       (count(r.id) FILTER (WHERE r.status <> 'PRESENT'))::int AS absent,
       (count(r.id) FILTER (WHERE r.status = 'PRESENT'
                              AND EXISTS (SELECT 1 FROM child_allergies a WHERE a.child_id = r.child_id)))::int AS substitute
FROM k3_days d
CROSS JOIN k3_ctx x
JOIN classrooms cl ON cl.school_year_id = x.year_id
JOIN age_groups g ON g.id = cl.age_group_id
LEFT JOIN enrollments e ON e.classroom_id = cl.id AND e.valid_from <= d.day AND (e.valid_to IS NULL OR e.valid_to >= d.day)
LEFT JOIN attendance_records r ON r.child_id = e.child_id AND r.date = d.day AND r.classroom_id = cl.id
WHERE d.off_note IS NULL
  AND d.day BETWEEN x.start_date AND x.today
GROUP BY d.day, cl.id, cl.campus_id, cl.age_group_id, g.meal_sessions;

-- Cơ sở–ngày có sổ: ngày đã qua có điểm danh; hôm nay chỉ cơ sở đã điểm danh đủ mọi trẻ
CREATE TEMP TABLE k3_count_days AS
SELECT c.campus_id, c.day
FROM k3_class_day c
CROSS JOIN k3_ctx x
GROUP BY c.campus_id, c.day, x.today
HAVING sum(c.present) > 0
   AND (c.day < x.today OR sum(c.enrolled - c.present - c.absent) = 0);

-- Tổng hợp lúc chốt 08:30, PHT cơ sở xác nhận một lần cho mọi bữa của cơ sở trong ngày
INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, needs_reconfirmation, aggregated_at,
                         confirmed_by, confirmed_at, confirmed_by_role, created_at, updated_at)
SELECT cd.campus_id, x.year_id, cd.day, s.session, 'CONFIRMED', FALSE, t.aggregated_at, st.vp_id, t.confirmed_at,
       'VICE_PRINCIPAL', t.aggregated_at, t.confirmed_at
FROM k3_count_days cd
CROSS JOIN k3_ctx x
JOIN k3_staff st ON st.campus_id = cd.campus_id
CROSS JOIN LATERAL (SELECT DISTINCT unnest(string_to_array(c.meal_sessions, ',')) AS session
                    FROM k3_class_day c WHERE c.campus_id = cd.campus_id AND c.day = cd.day) s
CROSS JOIN LATERAL (SELECT abs(hashtext('mc:' || st.code || ':' || cd.day::text)) AS v) h
CROSS JOIN LATERAL (
    SELECT (cd.day + TIME '08:30' + (h.v % 4) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS aggregated_at,
           (cd.day + TIME '08:38' + (h.v % 37) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS confirmed_at) t
ON CONFLICT (campus_id, date, session) DO NOTHING;

INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal, substitute)
SELECT mc.id, c.classroom_id, FALSE, c.present, c.absent, c.enrolled - c.present - c.absent,
       c.present - c.substitute, c.substitute
FROM k3_count_days cd
JOIN meal_counts mc ON mc.campus_id = cd.campus_id AND mc.date = cd.day
JOIN k3_class_day c ON c.campus_id = cd.campus_id AND c.day = cd.day
    AND (',' || c.meal_sessions || ',') LIKE '%,' || mc.session || ',%'
WHERE NOT EXISTS (SELECT 1 FROM meal_count_lines l WHERE l.meal_count_id = mc.id);

INSERT INTO meal_count_history (meal_count_id, action, user_id, at)
SELECT mc.id, v.action, CASE v.action WHEN 'CONFIRMED' THEN mc.confirmed_by END,
       CASE v.action WHEN 'CONFIRMED' THEN mc.confirmed_at ELSE mc.aggregated_at END
FROM k3_count_days cd
JOIN meal_counts mc ON mc.campus_id = cd.campus_id AND mc.date = cd.day AND mc.status = 'CONFIRMED'
CROSS JOIN (VALUES ('AGGREGATED'), ('CONFIRMED')) AS v (action)
WHERE NOT EXISTS (SELECT 1 FROM meal_count_history h WHERE h.meal_count_id = mc.id);

-- 4. Nhu cầu thực phẩm theo suất (NUT-07, giống MealDemandCalculator): ACT = suất đã chốt (trừ kho, kiểm nhận lần 2),
-- EXP = sĩ số đang học (định lượng phiếu nhập kho), FC = sĩ số dự kiến của bản dự trù hàng tươi
CREATE TEMP TABLE k3_fc_counts AS
SELECT st.campus_id, k.week_start,
       GREATEST(k.week_start, (SELECT min(d.day) FROM k3_days d WHERE d.off_note IS NULL)) AS period_from,
       k.week_start + 4 AS period_to,
       cl.age_group_id, g.sort_order,
       count(e.child_id)::int AS enrolled
FROM k3_weeks k
CROSS JOIN k3_ctx x
JOIN k3_staff st ON TRUE
JOIN classrooms cl ON cl.campus_id = st.campus_id AND cl.school_year_id = x.year_id
JOIN age_groups g ON g.id = cl.age_group_id
LEFT JOIN enrollments e ON e.classroom_id = cl.id
    AND e.valid_from <= GREATEST(k.week_start, (SELECT min(d.day) FROM k3_days d WHERE d.off_note IS NULL))
    AND (e.valid_to IS NULL OR e.valid_to >= GREATEST(k.week_start, (SELECT min(d.day) FROM k3_days d WHERE d.off_note IS NULL)))
WHERE k.status = 'PUBLISHED'
GROUP BY st.campus_id, k.week_start, cl.age_group_id, g.sort_order;
-- Số dự kiến: bớt khoảng 4% trẻ hay nghỉ
ALTER TABLE k3_fc_counts ADD COLUMN expected int;
UPDATE k3_fc_counts SET expected = enrolled - round(enrolled * 0.04)::int;

CREATE TEMP TABLE k3_srv AS
SELECT 'ACT'::text AS kind, mc.campus_id, mc.date AS day, mc.session, cl.age_group_id,
       sum(l.normal + l.substitute)::int AS portions
FROM meal_counts mc
JOIN k3_count_days cd ON cd.campus_id = mc.campus_id AND cd.day = mc.date
JOIN meal_count_lines l ON l.meal_count_id = mc.id AND NOT l.pending
JOIN classrooms cl ON cl.id = l.classroom_id
WHERE mc.status = 'CONFIRMED'
GROUP BY mc.campus_id, mc.date, mc.session, cl.age_group_id
HAVING sum(l.normal + l.substitute) > 0
UNION ALL
SELECT 'EXP', cl.campus_id, d.day, s.session, cl.age_group_id, count(*)::int
FROM k3_days d
CROSS JOIN k3_ctx x
JOIN classrooms cl ON cl.school_year_id = x.year_id
JOIN age_groups g ON g.id = cl.age_group_id
JOIN enrollments e ON e.classroom_id = cl.id AND e.valid_from <= LEAST(d.day, x.today)
    AND (e.valid_to IS NULL OR e.valid_to >= LEAST(d.day, x.today))
CROSS JOIN LATERAL unnest(string_to_array(g.meal_sessions, ',')) AS s (session)
WHERE d.off_note IS NULL
  AND d.day >= x.start_date
GROUP BY cl.campus_id, d.day, s.session, cl.age_group_id
UNION ALL
SELECT 'FC', f.campus_id, d.day, s.session, f.age_group_id, f.expected
FROM k3_fc_counts f
JOIN age_groups g ON g.id = f.age_group_id
JOIN k3_days d ON d.day BETWEEN f.period_from AND f.period_to AND d.off_note IS NULL
CROSS JOIN LATERAL unnest(string_to_array(g.meal_sessions, ',')) AS s (session)
WHERE f.expected > 0;

-- Lượng mua = Σ gam × suất ÷ (1 − thải bỏ%), làm tròn 2 chữ số như BE; ngày phải có trong days và không nghỉ
CREATE TEMP TABLE k3_need AS
SELECT s.kind, s.campus_id, s.day, f.id AS food_id, f.code AS food_code, f.storage, f.purchase_unit_size AS unit_size,
       f.unit_price,
       split_part(f.purchase_unit, ' ', 1) NOT IN ('kg', 'g', 'gam', 'gram', 'lạng', 'l', 'lít', 'lit', 'ml') AS countable,
       CASE WHEN f.waste_pct <= 0 THEN round(sum(di.grams_per_child * s.portions), 2)
            ELSE round(round(sum(di.grams_per_child * s.portions) / (1 - round(f.waste_pct / 100, 10)), 10), 2)
       END AS qty
FROM k3_srv s
JOIN age_groups g ON g.id = s.age_group_id
JOIN meal_plan_weeks w ON w.week_start = s.day - (EXTRACT(ISODOW FROM s.day)::int - 1)
    AND (s.kind <> 'ACT' OR w.status = 'PUBLISHED')
    AND EXISTS (SELECT 1 FROM jsonb_array_elements(w.days) e
                WHERE (e ->> 'date')::date = s.day AND NOT (e ->> 'off')::boolean)
JOIN meal_plan_items i ON i.week_id = w.id AND i.menu_date = s.day AND i.session = s.session
    AND i.menu_block = CASE WHEN g.age_to_months <= 36 THEN 'NURSERY' ELSE 'KINDERGARTEN' END
JOIN dish_ingredients di ON di.dish_id = i.dish_id AND di.age_group_id = s.age_group_id
JOIN foods f ON f.id = di.food_id
GROUP BY s.kind, s.campus_id, s.day, f.id;

-- 5. Kho khô (NUT-11, P-38): phiếu nhập đầu năm 31/08 rồi thứ Sáu cách 2 tuần, mỗi phiếu đủ dùng ~4 tuần theo sĩ số
-- (trừ tồn trước ngày nhập); trừ kho ngày theo suất đã chốt, lô hết hạn trước. Đi theo thứ tự ngày để tồn lô không âm
CREATE TEMP TABLE k3_stock_days AS
SELECT day, bool_or(is_receipt) AS is_receipt
FROM (SELECT DATE '2026-08-31' AS day, TRUE AS is_receipt
      UNION ALL
      SELECT g::date, TRUE FROM k3_ctx x CROSS JOIN generate_series(DATE '2026-09-18', x.today, INTERVAL '14 days') AS g
      UNION ALL
      SELECT day, FALSE FROM k3_count_days) u
GROUP BY day;

DO $$
DECLARE
    c record;
    d record;
    f record;
    lot record;
    rid bigint;
    sup bigint;
    remaining numeric;
    took numeric;
    at_time timestamptz;
    plan_end date;
BEGIN
    -- Thực đơn xoay vòng 4 tuần nên nhu cầu 4 tuần xấp xỉ một vòng; kỳ nhập vượt quá tuần đã lập thì lấy vòng gần nhất
    SELECT max(day) INTO plan_end FROM k3_need WHERE kind = 'EXP';
    SELECT id INTO sup FROM suppliers WHERE name = 'Công ty TNHH Lương thực Bình Giang' ORDER BY id LIMIT 1;
    FOR c IN SELECT * FROM k3_staff ORDER BY campus_id LOOP
        FOR d IN SELECT * FROM k3_stock_days ORDER BY day LOOP
            IF d.is_receipt AND sup IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM stock_receipts r
                    WHERE r.campus_id = c.campus_id AND r.received_date = d.day AND r.supplier_id = sup) THEN
                INSERT INTO stock_receipts (campus_id, supplier_id, received_date, status, note, confirmed_by, confirmed_at,
                                            created_by, updated_by, created_at, updated_at)
                VALUES (c.campus_id, sup, d.day, 'CONFIRMED',
                        CASE WHEN d.day = DATE '2026-08-31' THEN 'Nhập kho khô đầu năm học'
                             ELSE 'Nhập gạo, dầu ăn, gia vị định kỳ 2 tuần' END,
                        c.vp_id, (d.day + TIME '07:20') AT TIME ZONE 'Asia/Ho_Chi_Minh', c.vp_id, c.vp_id,
                        (d.day + TIME '07:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
                        (d.day + TIME '07:20') AT TIME ZONE 'Asia/Ho_Chi_Minh')
                RETURNING id INTO rid;
                INSERT INTO stock_receipt_lines (receipt_id, food_id, lot_no, expiry_date, quantity, unit_price)
                SELECT rid, t.food_id,
                       'L' || to_char(d.day, 'YYMMDD') || '-' || right(c.code, 1) || '-' || substr(t.food_code, 4),
                       d.day + CASE WHEN t.food_code = 'VDD6010' THEN 365
                                    WHEN t.food_code IN ('VDD12014', 'VDD13005', 'VDD13017') THEN 540
                                    ELSE 180 END,
                       ceil(t.need / t.unit_size) * t.unit_size, t.unit_price
                FROM (SELECT n.food_id, n.food_code, n.unit_size, n.unit_price,
                             sum(n.qty) * 1.1 - coalesce((
                                 SELECT sum(m.quantity) FROM stock_movements m
                                 LEFT JOIN stock_receipts mr ON mr.id = m.receipt_id
                                 WHERE m.campus_id = c.campus_id AND m.food_id = n.food_id
                                   AND coalesce(m.meal_date, mr.received_date) < d.day), 0) AS need
                      FROM k3_need n
                      WHERE n.kind = 'EXP' AND n.storage = 'DRY' AND n.campus_id = c.campus_id
                        AND n.day > LEAST(d.day + 28, plan_end) - 28 AND n.day <= LEAST(d.day + 28, plan_end)
                      GROUP BY n.food_id, n.food_code, n.unit_size, n.unit_price) t
                WHERE t.need > 0;
                IF NOT EXISTS (SELECT 1 FROM stock_receipt_lines l WHERE l.receipt_id = rid) THEN
                    DELETE FROM stock_receipts WHERE id = rid;
                ELSE
                    INSERT INTO stock_movements (lot_id, campus_id, food_id, movement_type, quantity, receipt_id, reason,
                                                 created_by, created_at)
                    SELECT l.id, c.campus_id, l.food_id, 'RECEIPT', l.quantity, rid, '', c.vp_id,
                           (d.day + TIME '07:20') AT TIME ZONE 'Asia/Ho_Chi_Minh'
                    FROM stock_receipt_lines l
                    WHERE l.receipt_id = rid;
                END IF;
            END IF;

            IF EXISTS (SELECT 1 FROM k3_count_days cd WHERE cd.campus_id = c.campus_id AND cd.day = d.day) THEN
                IF NOT EXISTS (SELECT 1 FROM stock_daily_demands x WHERE x.campus_id = c.campus_id AND x.meal_date = d.day) THEN
                    SELECT max(mc.confirmed_at) INTO at_time FROM meal_counts mc
                    WHERE mc.campus_id = c.campus_id AND mc.date = d.day AND mc.status = 'CONFIRMED';
                    FOR f IN SELECT n.food_id, n.qty FROM k3_need n
                             WHERE n.kind = 'ACT' AND n.storage = 'DRY' AND n.campus_id = c.campus_id AND n.day = d.day
                               AND n.qty > 0
                             ORDER BY n.food_id LOOP
                        remaining := f.qty;
                        FOR lot IN SELECT l.id, l.expiry_date, coalesce(sum(m.quantity), 0) AS bal
                                   FROM stock_receipt_lines l
                                   JOIN stock_receipts r ON r.id = l.receipt_id AND r.status = 'CONFIRMED'
                                       AND r.campus_id = c.campus_id
                                   LEFT JOIN stock_movements m ON m.lot_id = l.id
                                   WHERE l.food_id = f.food_id AND l.expiry_date >= d.day
                                   GROUP BY l.id, l.expiry_date
                                   ORDER BY l.expiry_date, l.id LOOP
                            EXIT WHEN remaining <= 0;
                            CONTINUE WHEN lot.bal <= 0;
                            took := LEAST(lot.bal, remaining);
                            INSERT INTO stock_movements (lot_id, campus_id, food_id, movement_type, quantity, meal_date,
                                                         reason, created_at)
                            VALUES (lot.id, c.campus_id, f.food_id, 'DEDUCT', -took, d.day, '', coalesce(at_time, now()));
                            remaining := remaining - took;
                        END LOOP;
                        INSERT INTO stock_daily_demands (campus_id, meal_date, food_id, required_qty, shortfall_qty, updated_at)
                        VALUES (c.campus_id, d.day, f.food_id, f.qty, GREATEST(remaining, 0), coalesce(at_time, now()));
                    END LOOP;
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- Phiếu nháp kỳ nhập tới của cơ sở chính (PHT lập trước, chờ hàng về mới xác nhận)
INSERT INTO stock_receipts (campus_id, supplier_id, received_date, status, note, created_by, updated_by, created_at, updated_at)
SELECT st.campus_id, s.id, nx.day, 'DRAFT', 'Hàng khô kỳ tới, chờ nhà cung cấp báo giá và giao hàng', st.vp_id, st.vp_id,
       LEAST((x.today + TIME '14:30') AT TIME ZONE 'Asia/Ho_Chi_Minh', now()),
       LEAST((x.today + TIME '14:30') AT TIME ZONE 'Asia/Ho_Chi_Minh', now())
FROM k3_ctx x
JOIN k3_staff st ON st.is_main
JOIN suppliers s ON s.name = 'Công ty TNHH Lương thực Bình Giang'
CROSS JOIN LATERAL (SELECT min(g::date) AS day
                    FROM generate_series(DATE '2026-09-18', x.today + 14, INTERVAL '14 days') AS g
                    WHERE g::date > x.today) nx
WHERE NOT EXISTS (SELECT 1 FROM stock_receipts r
                  WHERE r.campus_id = st.campus_id AND r.received_date = nx.day AND r.supplier_id = s.id);

INSERT INTO stock_receipt_lines (receipt_id, food_id, lot_no, expiry_date, quantity, unit_price)
SELECT r.id, n.food_id,
       'L' || to_char(r.received_date, 'YYMMDD') || '-' || right(st.code, 1) || '-' || substr(n.food_code, 4),
       r.received_date + CASE WHEN n.food_code = 'VDD6010' THEN 365
                              WHEN n.food_code IN ('VDD12014', 'VDD13005', 'VDD13017') THEN 540
                              ELSE 180 END,
       ceil(sum(n.qty) * 1.1 / n.unit_size) * n.unit_size, n.unit_price
FROM stock_receipts r
JOIN k3_staff st ON st.campus_id = r.campus_id AND st.is_main
JOIN suppliers s ON s.id = r.supplier_id AND s.name = 'Công ty TNHH Lương thực Bình Giang'
CROSS JOIN k3_ctx x
JOIN k3_need n ON n.kind = 'EXP' AND n.storage = 'DRY' AND n.campus_id = r.campus_id
    AND n.day > x.today AND n.day <= x.today + 14
WHERE r.status = 'DRAFT'
  AND r.received_date > x.today
  AND NOT EXISTS (SELECT 1 FROM stock_receipt_lines l WHERE l.receipt_id = r.id)
GROUP BY r.id, r.received_date, st.code, n.food_id, n.food_code, n.unit_size, n.unit_price
HAVING sum(n.qty) > 0;

-- 6. Dự trù hàng tươi theo tuần (NUT-07): PHT cơ sở lập chiều thứ Năm tuần trước, có cả tuần sau
INSERT INTO fresh_forecasts (campus_id, period_from, period_to, note, counts, created_by, created_at, updated_at)
SELECT f.campus_id, f.period_from, f.period_to,
       'Dự trù hàng tươi ' || to_char(f.period_from, 'DD/MM') || '–' || to_char(f.period_to, 'DD/MM')
           || (ARRAY['', ', sĩ số dự kiến trừ trẻ hay nghỉ', ', đã báo nhà cung cấp', ', theo thực đơn đã công bố'])
              [abs(hashtext('ff:' || st.code || f.week_start::text)) % 4 + 1],
       jsonb_agg(jsonb_build_object('ageGroupId', f.age_group_id, 'enrolledCount', f.enrolled,
                                    'expectedCount', f.expected) ORDER BY f.sort_order),
       st.vp_id, t.at, t.at
FROM k3_fc_counts f
JOIN k3_staff st ON st.campus_id = f.campus_id
CROSS JOIN LATERAL (SELECT LEAST((f.week_start - 4 + TIME '14:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
                                 now() - INTERVAL '30 minutes') AS at) t
WHERE NOT EXISTS (SELECT 1 FROM fresh_forecasts x
                  WHERE x.campus_id = f.campus_id AND x.period_from = f.period_from AND x.period_to = f.period_to)
GROUP BY f.campus_id, f.period_from, f.period_to, f.week_start, st.code, st.vp_id, t.at;

-- Hàng tươi giao theo ngày ⇒ đơn vị mua đếm được (quả, hộp, bìa) làm tròn lên nguyên đơn vị (PurchaseUnits)
INSERT INTO fresh_forecast_lines (forecast_id, menu_date, food_id, quantity)
SELECT ff.id, n.day, n.food_id,
       CASE WHEN n.countable THEN ceil(n.qty / n.unit_size) * n.unit_size ELSE n.qty END
FROM fresh_forecasts ff
JOIN k3_need n ON n.kind = 'FC' AND n.storage = 'FRESH' AND n.campus_id = ff.campus_id
    AND n.day BETWEEN ff.period_from AND ff.period_to AND n.qty > 0
WHERE EXISTS (SELECT 1 FROM k3_fc_counts f
              WHERE f.campus_id = ff.campus_id AND f.period_from = ff.period_from AND f.period_to = ff.period_to)
  AND NOT EXISTS (SELECT 1 FROM fresh_forecast_lines l WHERE l.forecast_id = ff.id)
ON CONFLICT (forecast_id, menu_date, food_id) DO NOTHING;

-- 7. Kiểm nhận hàng tươi (NUT-12): lần 1 sáng sớm theo bản dự trù, lần 2 sau khi chốt suất theo suất đã xác nhận.
-- Hôm nay: cơ sở đã chốt suất mới nhận lần 1, chờ lần 2; cơ sở chưa điểm danh chưa nhận
CREATE TEMP TABLE k3_fresh_check AS
SELECT cd.campus_id, cd.day, r.round, st.vp_id, st.code,
       CASE r.round WHEN 1 THEN (SELECT ff.id FROM fresh_forecasts ff
                                 WHERE ff.campus_id = cd.campus_id AND ff.period_from <= cd.day AND ff.period_to >= cd.day
                                 ORDER BY ff.created_at DESC, ff.id DESC LIMIT 1) END AS forecast_id,
       CASE r.round
           WHEN 1 THEN (ARRAY['Hộ kinh doanh Thực phẩm sạch Hùng Liên', 'Hộ kinh doanh Thủy sản Minh Thu',
                              'Hợp tác xã Rau an toàn Đường An'])[abs(hashtext('fs:' || st.code || cd.day::text)) % 3 + 1]
           ELSE 'Hộ kinh doanh Thực phẩm sạch Hùng Liên' END AS supplier_name,
       (cd.day + CASE r.round WHEN 1 THEN TIME '06:40' ELSE TIME '09:35' END
            + (abs(hashtext('ft:' || st.code || cd.day::text)) % 15) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS checked_at
FROM k3_count_days cd
CROSS JOIN k3_ctx x
JOIN k3_staff st ON st.campus_id = cd.campus_id
CROSS JOIN (VALUES (1), (2)) AS r (round)
WHERE cd.day < x.today OR r.round = 1;

INSERT INTO fresh_checks (campus_id, check_date, round, forecast_id, checked_by, checked_at, note, supplier_id)
SELECT k.campus_id, k.day, k.round, k.forecast_id, k.vp_id, k.checked_at,
       CASE WHEN k.round = 1 THEN (ARRAY['', 'Hàng tươi, đạt cảm quan', 'Thịt cá có phiếu kiểm dịch, rau đạt',
                                         '', 'Nhiệt độ thùng lạnh đạt, bao bì sạch'])
                                  [abs(hashtext('fn:' || k.code || k.day::text)) % 5 + 1]
            ELSE '' END,
       s.id
FROM k3_fresh_check k
LEFT JOIN suppliers s ON s.name = k.supplier_name
WHERE k.round = 2 OR k.forecast_id IS NOT NULL
ON CONFLICT (campus_id, check_date, round) DO NOTHING;

-- Lần 1: ~3% dòng giao thiếu (nhà cung cấp giao bù), ~1% giao dư (trả lại); lần 2 nhận đủ
INSERT INTO fresh_check_lines (check_id, food_id, expected_qty, received_qty, result, note)
SELECT fc.id, e.food_id, e.qty,
       CASE WHEN fc.round = 2 OR e.h >= 4 THEN e.qty
            WHEN e.h < 3 THEN round(e.qty * 0.9, 2)
            ELSE round(e.qty * 1.05, 2) END,
       CASE WHEN fc.round = 2 OR e.h >= 4 THEN 'ENOUGH' WHEN e.h < 3 THEN 'SHORT' ELSE 'OVER' END,
       CASE WHEN fc.round = 2 OR e.h >= 4 THEN ''
            WHEN e.h < 3 THEN 'Giao thiếu, nhà cung cấp giao bù trước 9 giờ'
            ELSE 'Giao dư, đã trả lại phần thừa cho nhà cung cấp' END
FROM fresh_checks fc
JOIN k3_fresh_check k ON k.campus_id = fc.campus_id AND k.day = fc.check_date AND k.round = fc.round
CROSS JOIN LATERAL (
    SELECT l.food_id, l.quantity AS qty, abs(hashtext('fl:' || k.code || fc.check_date::text || l.food_id)) % 100 AS h
    FROM fresh_forecast_lines l
    WHERE fc.round = 1 AND l.forecast_id = fc.forecast_id AND l.menu_date = fc.check_date
    UNION ALL
    SELECT n.food_id, CASE WHEN n.countable THEN ceil(n.qty / n.unit_size) * n.unit_size ELSE n.qty END, 100
    FROM k3_need n
    WHERE fc.round = 2 AND n.kind = 'ACT' AND n.storage = 'FRESH' AND n.campus_id = fc.campus_id
      AND n.day = fc.check_date AND n.qty > 0) e
WHERE NOT EXISTS (SELECT 1 FROM fresh_check_lines x WHERE x.check_id = fc.id)
ON CONFLICT (check_id, food_id) DO NOTHING;

-- 8. Lưu mẫu thức ăn (NUT-21): mỗi bữa một mẫu gồm các món đã nấu; mẫu rắn ≥100 g, mẫu lỏng ≥150 ml, 2–8 °C, giữ
-- ≥24 giờ rồi hủy. Đã qua: PHT duyệt lưu và duyệt hủy. Ngày học gần nhất: Cơ sở 1 chờ duyệt hủy, Cơ sở 2 chờ duyệt
-- lưu mẫu. Hôm nay: bếp cơ sở đã chốt suất đã ghi các bữa tới giờ hiện tại, chờ duyệt
CREATE TEMP TABLE k3_sample AS
SELECT cd.campus_id, cd.day, i.session, st.code, st.is_main, st.vp_id, st.kitchen_id,
       array_agg(DISTINCT i.dish_id ORDER BY i.dish_id) AS dish_ids,
       (cd.day + CASE i.session WHEN 'MORNING' THEN TIME '07:45' WHEN 'LUNCH' THEN TIME '10:25' ELSE TIME '14:15' END
            + (abs(hashtext('sp:' || st.code || cd.day::text || i.session)) % 10) * INTERVAL '1 minute') AS sampled_local
FROM k3_count_days cd
JOIN k3_staff st ON st.campus_id = cd.campus_id
JOIN meal_plan_weeks w ON w.week_start = cd.day - (EXTRACT(ISODOW FROM cd.day)::int - 1) AND w.status = 'PUBLISHED'
JOIN meal_plan_items i ON i.week_id = w.id AND i.menu_date = cd.day
-- Bữa sáng chỉ nhà trẻ ăn; cơ sở có lớp ăn bữa đó mới lưu mẫu
WHERE EXISTS (SELECT 1 FROM k3_class_day c WHERE c.campus_id = cd.campus_id AND c.day = cd.day
                AND (',' || c.meal_sessions || ',') LIKE '%,' || i.session || ',%'
                AND i.menu_block = (SELECT CASE WHEN g.age_to_months <= 36 THEN 'NURSERY' ELSE 'KINDERGARTEN' END
                                    FROM age_groups g WHERE g.id = c.age_group_id))
GROUP BY cd.campus_id, cd.day, i.session, st.code, st.is_main, st.vp_id, st.kitchen_id;

CREATE TEMP TABLE k3_sample_state AS
SELECT s.*,
       s.sampled_local AT TIME ZONE 'Asia/Ho_Chi_Minh' AS sampled_at,
       -- Hủy mẫu vào ngày học kế tiếp, sau giờ lấy mẫu 20 phút (luôn ≥ 24 giờ)
       (coalesce((SELECT min(d.day) FROM k3_days d WHERE d.off_note IS NULL AND d.day > s.day), s.day + 1)
            + s.sampled_local::time + INTERVAL '20 minutes') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS disposal_at,
       (SELECT max(c.day) FROM k3_count_days c CROSS JOIN k3_ctx x WHERE c.day < x.today) AS last_day,
       x.today
FROM k3_sample s
CROSS JOIN k3_ctx x;

INSERT INTO food_samples (campus_id, sample_date, session, sampled_at, recorded_by, status, approved_by, approved_at,
                          disposed_at, disposal_recorded_by, disposal_status, disposal_approved_by, disposal_approved_at,
                          note, dish_ids, dish_details, created_at, updated_at)
SELECT s.campus_id, s.day, s.session, s.sampled_at, s.kitchen_id,
       v.status,
       CASE WHEN v.status = 'APPROVED' THEN s.vp_id END,
       CASE WHEN v.status = 'APPROVED' THEN s.sampled_at + INTERVAL '50 minutes' END,
       CASE WHEN v.disposal <> 'NONE' THEN s.disposal_at END,
       CASE WHEN v.disposal <> 'NONE' THEN s.kitchen_id END,
       v.disposal,
       CASE WHEN v.disposal = 'APPROVED' THEN s.vp_id END,
       CASE WHEN v.disposal = 'APPROVED' THEN s.disposal_at + INTERVAL '45 minutes' END,
       (ARRAY['', '', 'Đã dán nhãn ngày giờ lấy mẫu, niêm phong', '', 'Mẫu để ngăn riêng tủ lạnh lưu mẫu'])
           [abs(hashtext('sn:' || s.code || s.day::text || s.session)) % 5 + 1],
       s.dish_ids,
       (SELECT jsonb_agg(jsonb_build_object(
                   'dishId', d.id,
                   'weight', CASE WHEN d.liquid THEN '150 ml' ELSE '100 g' END,
                   'container', CASE WHEN d.liquid THEN 'Lọ thủy tinh có nắp' ELSE 'Hộp inox có nắp' END,
                   'temperature', '2–8 °C') ORDER BY d.id)
        FROM (SELECT x.id, (x.dish_type = 'SOUP' OR x.name ~ '^(Sữa tươi|Cháo|Súp|Phở|Bún|Miến|Mì)') AS liquid
              FROM dishes x WHERE x.id = ANY (s.dish_ids)) d),
       s.sampled_at,
       coalesce(CASE WHEN v.disposal = 'APPROVED' THEN s.disposal_at + INTERVAL '45 minutes'
                     WHEN v.disposal = 'RECORDED' THEN s.disposal_at
                     WHEN v.status = 'APPROVED' THEN s.sampled_at + INTERVAL '50 minutes' END, s.sampled_at)
FROM k3_sample_state s
CROSS JOIN LATERAL (
    SELECT CASE WHEN s.day = s.today THEN 'RECORDED'
                WHEN s.day = s.last_day AND NOT s.is_main THEN 'RECORDED'
                ELSE 'APPROVED' END AS status,
           CASE WHEN s.day = s.today OR (s.day = s.last_day AND NOT s.is_main) OR s.disposal_at > now() THEN 'NONE'
                WHEN s.day = s.last_day THEN 'RECORDED'
                ELSE 'APPROVED' END AS disposal) v
WHERE s.sampled_at <= now()
  AND NOT EXISTS (SELECT 1 FROM food_samples f
                  WHERE f.campus_id = s.campus_id AND f.sample_date = s.day AND f.session = s.session);

DROP TABLE k3_sample_state;
DROP TABLE k3_sample;
DROP TABLE k3_fresh_check;
DROP TABLE k3_stock_days;
DROP TABLE k3_need;
DROP TABLE k3_srv;
DROP TABLE k3_fc_counts;
DROP TABLE k3_count_days;
DROP TABLE k3_class_day;
DROP TABLE k3_weeks;
DROP TABLE k3_days;
DROP TABLE k3_staff;
DROP TABLE k3_ctx;
