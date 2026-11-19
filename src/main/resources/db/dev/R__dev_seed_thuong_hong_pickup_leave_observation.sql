-- DỮ LIỆU GIẢ CHỈ CHO DEV/DEMO (profile "dev"): đơn nghỉ, đón trẻ + thông báo, đánh giá hằng ngày của MN Thượng Hồng,
-- 19/10–04/11/2026, dựng trên điểm danh của R__dev_seed_thuong_hong_operations.sql (chạy sau file đó). Không seed đánh giá
-- theo hoạt động (cần giáo án tuần đã duyệt), nhận xét định kỳ, khen thưởng. Số liệu sinh theo hàm băm cố định.
-- Idempotent theo khóa tự nhiên, không ghi đè dữ liệu có sẵn.

-- 1. Đơn nghỉ: mỗi ngày vắng có phép có một đơn một ngày do phụ huynh tạo từ chiều hôm trước (ATT-04, không duyệt)
INSERT INTO leave_requests (child_id, from_date, to_date, reason, created_by, created_at)
SELECT r.child_id, r.date, r.date,
       (ARRAY['Con sốt, xin cho con nghỉ', 'Con ho, sổ mũi, xin nghỉ ở nhà', 'Gia đình có việc đột xuất',
              'Đưa con đi khám theo lịch'])[abs(hashtext('l' || r.child_id || r.date::text)::bigint) % 4 + 1],
       p.user_id, ((r.date - 1) + TIME '19:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM attendance_records r
JOIN LATERAL (SELECT l.user_id FROM child_guardians l WHERE l.child_id = r.child_id AND l.user_id IS NOT NULL
              ORDER BY l.id LIMIT 1) p ON TRUE
WHERE r.status = 'EXCUSED' AND r.date BETWEEN DATE '2026-10-19' AND DATE '2026-11-04'
  AND NOT EXISTS (
      SELECT 1 FROM leave_requests q WHERE q.child_id = r.child_id AND r.date BETWEEN q.from_date AND q.to_date);

-- 2. Đón trẻ: mỗi trẻ có mặt được trả một lần/ngày lúc 16:30–17:15; ~92% phụ huynh khớp ảnh, còn lại người thân đón hộ
-- xác nhận qua điện thoại (PCK-01, PCK-02)
INSERT INTO pickup_records (child_id, classroom_id, campus_id, date, outcome, verification, guardian_position,
                            pickup_person_name, pickup_person_relationship, pickup_person_phone, handed_over_at,
                            notified_parents, recorded_by, recorded_at)
SELECT r.child_id, r.classroom_id, r.campus_id, r.date, 'HANDED_OVER',
       CASE WHEN h.k < 92 THEN 'PHOTO_MATCH' ELSE 'PHONE_CONFIRMED' END,
       CASE WHEN h.k < 92 THEN g.position END,
       CASE WHEN h.k < 92 THEN g.full_name ELSE 'Người thân đón hộ' END,
       CASE WHEN h.k < 92 THEN g.relationship ELSE 'GRANDMOTHER' END,
       CASE WHEN h.k < 92 THEN g.phone ELSE '0903' || lpad(r.child_id::text, 6, '0') END,
       (r.date + TIME '16:30' + (h.m % 45) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh',
       (SELECT count(*) FROM child_guardians l WHERE l.child_id = r.child_id AND l.user_id IS NOT NULL),
       r.recorded_by,
       (r.date + TIME '16:30' + (h.m % 45) * INTERVAL '1 minute') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM attendance_records r
CROSS JOIN LATERAL (SELECT abs(hashtext('k' || r.child_id || r.date::text)::bigint) % 100 AS k,
                           abs(hashtext('p' || r.child_id || r.date::text)::bigint) AS m) h
JOIN LATERAL (
    SELECT x.position, x.full_name, x.relationship, x.phone FROM child_guardians x
    WHERE x.child_id = r.child_id
    ORDER BY CASE WHEN h.k % 3 = 0 THEN -x.position ELSE x.position END
    LIMIT 1) g ON TRUE
WHERE r.status = 'PRESENT' AND r.date BETWEEN DATE '2026-10-19' AND DATE '2026-11-04'
  AND NOT EXISTS (SELECT 1 FROM pickup_records p WHERE p.child_id = r.child_id AND p.date = r.date);

-- Một lần không trả được (người đón không xác nhận qua điện thoại) trước lần trả thành công, ~1% số lượt (ATT-10)
INSERT INTO pickup_records (child_id, classroom_id, campus_id, date, outcome, verification, pickup_person_name,
                            pickup_person_relationship, pickup_person_phone, note, notified_parents, recorded_by, recorded_at)
SELECT p.child_id, p.classroom_id, p.campus_id, p.date, 'NOT_HANDED_OVER', 'PHONE_DECLINED', 'Người lạ',
       'OTHER', '0904' || lpad(p.child_id::text, 6, '0'), 'Phụ huynh không xác nhận qua điện thoại', 0, p.recorded_by,
       p.recorded_at - INTERVAL '10 minutes'
FROM pickup_records p
WHERE p.outcome = 'HANDED_OVER' AND p.date BETWEEN DATE '2026-10-19' AND DATE '2026-11-04'
  AND abs(hashtext('d' || p.child_id || p.date::text)::bigint) % 100 = 0
  AND NOT EXISTS (
      SELECT 1 FROM pickup_records q WHERE q.child_id = p.child_id AND q.date = p.date AND q.outcome = 'NOT_HANDED_OVER');

-- Thông báo "bé đã được đón" cho từng tài khoản phụ huynh liên kết (PCK-04); đã đọc trừ 2 ngày gần nhất
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT l.user_id, 'CHILD_PICKED_UP', 'Bé đã được đón',
       'Bé ' || ch.full_name || ' đã được ' || p.pickup_person_name || ' đón lúc '
           || to_char(p.handed_over_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI') || '.',
       p.child_id, p.handed_over_at,
       CASE WHEN p.date < DATE '2026-11-03' THEN p.handed_over_at + INTERVAL '20 minutes' END
FROM pickup_records p
JOIN children ch ON ch.id = p.child_id
JOIN child_guardians l ON l.child_id = p.child_id AND l.user_id IS NOT NULL
WHERE p.outcome = 'HANDED_OVER' AND p.date BETWEEN DATE '2026-10-19' AND DATE '2026-11-04'
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.user_id = l.user_id AND n.child_id = p.child_id AND n.type = 'CHILD_PICKED_UP'
        AND n.created_at = p.handed_over_at);

-- 3. Đánh giá hằng ngày của trẻ có mặt (OBS-01, OBS-02): ăn đủ các bữa của nhóm tuổi (thỉnh thoảng ăn ít), ngủ, cảm xúc,
-- mức tham gia, hành vi nổi bật; sức khỏe theo sức khỏe trong ngày (không ghi đè)
INSERT INTO daily_observations (child_id, classroom_id, campus_id, date, meals, nap, emotion, participation, behaviors,
                                recorded_by, recorded_at)
SELECT r.child_id, r.classroom_id, r.campus_id, r.date,
       (SELECT jsonb_object_agg(s, CASE WHEN abs(hashtext(s || r.child_id || r.date::text)::bigint) % 10 = 0
                                        THEN 'AN_IT' ELSE 'AN_HET' END)
        FROM unnest(string_to_array(g.meal_sessions, ',')) AS s),
       CASE WHEN h.n % 10 = 0 THEN 'KHONG_NGU' WHEN h.n % 10 = 1 THEN 'NGU_IT' ELSE 'NGU_TOT' END,
       CASE WHEN h.e < 12 THEN 'HAPPY' WHEN h.e < 18 THEN 'CALM' WHEN h.e = 18 THEN 'SAD' ELSE 'UPSET' END,
       CASE WHEN h.q < 5 THEN 'TICH_CUC' WHEN h.q < 9 THEN 'BINH_THUONG' ELSE 'CHUA_TICH_CUC' END,
       CASE WHEN h.q < 3 THEN 'HOP_TAC_TOT' WHEN h.q = 9 THEN 'KHONG_TAP_TRUNG' ELSE '' END,
       r.recorded_by, (r.date + TIME '15:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM attendance_records r
JOIN classrooms cl ON cl.id = r.classroom_id
JOIN age_groups g ON g.id = cl.age_group_id
CROSS JOIN LATERAL (SELECT abs(hashtext('n' || r.child_id || r.date::text)::bigint) AS n,
                           abs(hashtext('e' || r.child_id || r.date::text)::bigint) % 20 AS e,
                           abs(hashtext('q' || r.child_id || r.date::text)::bigint) % 10 AS q) h
WHERE r.status = 'PRESENT' AND r.date BETWEEN DATE '2026-10-19' AND DATE '2026-11-04'
ON CONFLICT (child_id, date) DO NOTHING;

-- Nhận xét chung của lớp mỗi ngày (OBS-19), một bản cho mỗi lớp có trẻ có mặt
INSERT INTO class_daily_comments (classroom_id, campus_id, date, text, recorded_by, updated_at)
SELECT r.classroom_id, r.campus_id, r.date,
       (ARRAY['Cả lớp hào hứng tham gia hoạt động học và hoạt động ngoài trời, các con ăn ngon miệng.',
              'Các con chơi hợp tác tốt ở góc chơi, giờ ngủ trưa đúng giờ, nề nếp lớp ổn định.',
              'Lớp thực hiện tốt giờ vệ sinh và ăn bán trú, có vài bạn cần cô nhắc khi xếp hàng.',
              'Các con tích cực trong hoạt động tạo hình, biết chia sẻ đồ chơi với bạn.'])[abs(hashtext('c' || r.classroom_id || r.date::text)::bigint) % 4 + 1],
       min(r.recorded_by), (r.date + TIME '16:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM attendance_records r
WHERE r.status = 'PRESENT' AND r.date BETWEEN DATE '2026-10-19' AND DATE '2026-11-04'
GROUP BY r.classroom_id, r.campus_id, r.date
ON CONFLICT (classroom_id, date) DO NOTHING;
