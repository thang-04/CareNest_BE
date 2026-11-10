-- DỮ LIỆU GIẢ CHỈ CHO DEV/DEMO (profile "dev"): dữ liệu thử các màn của nhánh G94-180 trên cơ cấu MN Thượng Hồng — trẻ chờ
-- xếp lớp, trẻ đã nghỉ học, chuyển lớp (V86), yêu cầu đổi SĐT phụ huynh (V84), khai báo tiếp xúc lao (CHD-07), 2 kỳ cân đo
-- đã đóng + giải thích xu hướng (V85) và thông báo đi kèm. Chạy sau các seed Thượng Hồng khác (Flyway sắp repeatable theo
-- mô tả). Giữ khớp điểm danh 19/10–04/11 của seed vận hành (mọi trẻ đang học có đủ điểm danh):
--   - trẻ mới không vào lớp đang học (chờ xếp lớp, hoặc đã nghỉ trước 19/10);
--   - chuyển lớp dùng trẻ có sẵn, hiệu lực 09/11/2026 (sau khoảng điểm danh).
-- Không có đợt khám (phiếu khám Phụ lục II): tạo ở màn Sức khỏe. Duyệt đổi SĐT gọi Keycloak: tài khoản phụ huynh Thượng Hồng
-- không có user Keycloak ⇒ chỉ xem/từ chối được; thử duyệt bằng yêu cầu mới cho parent1/parent2.
-- Idempotent theo khóa tự nhiên, không ghi đè dữ liệu đã đổi.

-- 1. Trẻ mới: 3 trẻ chờ xếp lớp (Vũ Thảo Nhi chưa khai báo sức khỏe ⇒ thử lỗi xếp lớp khi chưa khai báo) và 1 trẻ đã nghỉ
-- học từ 05/10/2026. Mã cấp tiếp theo child_code_sequences như tiếp nhận; khóa tự nhiên (họ tên, ngày sinh, ngày tiếp nhận)
CREATE TEMP TABLE g180_children AS
SELECT *
FROM (VALUES (1, 'Lê Gia Huy', DATE '2023-01-25', 'MALE', 'CS-B', DATE '2026-09-01', 'Lớp Mẫu giáo bé B1', 'LEFT', TRUE),
             (2, 'Phạm Khánh Linh', DATE '2023-04-18', 'FEMALE', 'CS-A', DATE '2026-11-03', NULL, 'PENDING_PLACEMENT', TRUE),
             (3, 'Hoàng Đức Anh', DATE '2024-02-20', 'MALE', 'CS-B', DATE '2026-11-05', NULL, 'PENDING_PLACEMENT', TRUE),
             (4, 'Vũ Thảo Nhi', DATE '2022-06-30', 'FEMALE', 'CS-A', DATE '2026-11-09', NULL, 'PENDING_PLACEMENT', FALSE))
         AS v (n, full_name, date_of_birth, gender, campus_code, enrolled_on, class_name, status, declared);

INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on, left_on, left_reason)
SELECT 'HS26' || lpad(x.num::text, GREATEST(3, length(x.num::text)), '0'), x.full_name, x.date_of_birth, x.gender,
       x.status, CASE WHEN x.status = 'PENDING_PLACEMENT' THEN x.campus_id END, x.enrolled_on,
       CASE WHEN x.status = 'LEFT' THEN DATE '2026-10-05' END,
       CASE WHEN x.status = 'LEFT' THEN 'Gia đình chuyển nơi ở, xin cho trẻ nghỉ học' END
FROM (
    SELECT t.*, c.id AS campus_id, s.last_number + row_number() OVER (ORDER BY t.n) AS num
    FROM g180_children t
    JOIN campuses c ON c.code = t.campus_code
    JOIN child_code_sequences s ON s.code_prefix = 'HS26'
    WHERE NOT EXISTS (
        SELECT 1 FROM children ch
        WHERE ch.full_name = t.full_name AND ch.date_of_birth = t.date_of_birth AND ch.enrolled_on = t.enrolled_on)
) x;

UPDATE child_code_sequences s
SET last_number = m.max_number
FROM (SELECT max(substring(code FROM 5)::integer) AS max_number FROM children WHERE code ~ '^HS26[0-9]+$') m
WHERE s.code_prefix = 'HS26' AND m.max_number > s.last_number;

CREATE TEMP TABLE g180_child_ids AS
SELECT t.*, ch.id AS child_id, ch.code
FROM g180_children t
JOIN children ch ON ch.full_name = t.full_name AND ch.date_of_birth = t.date_of_birth AND ch.enrolled_on = t.enrolled_on;

INSERT INTO child_profiles (child_id, premature, ethnicity, beneficiary, address_province, address_ward, address_street)
SELECT t.child_id, 'NO', 'Kinh', 'Không', 'Thành phố Hải Phòng', 'Thôn ' || t.n, 'Số ' || (t.n * 7) || ' ngõ ' || (t.n + 2)
FROM g180_child_ids t
ON CONFLICT (child_id) DO NOTHING;

-- Phụ huynh trên hồ sơ, chưa kích hoạt tài khoản; SĐT dải 0909180xxx, CCCD 99909180xxxx
INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id)
SELECT t.child_id, g.position, g.full_name, g.relationship, '0909180' || lpad((t.n * 10 + g.position)::text, 3, '0'),
       '99909180' || lpad((t.n * 10 + g.position)::text, 4, '0')
FROM (VALUES (1, 0, 'Lê Văn Tùng', 'FATHER'), (1, 1, 'Nguyễn Thị Hằng', 'MOTHER'),
             (2, 0, 'Phạm Quốc Bảo', 'FATHER'), (2, 1, 'Đỗ Thị Thu', 'MOTHER'),
             (3, 0, 'Trần Thị Yến', 'MOTHER'),
             (4, 0, 'Vũ Đình Khoa', 'FATHER'), (4, 1, 'Lê Thị Mỹ', 'MOTHER')) AS g (n, position, full_name, relationship)
JOIN g180_child_ids t ON t.n = g.n
WHERE NOT EXISTS (SELECT 1 FROM child_guardians x WHERE x.child_id = t.child_id AND x.position = g.position);

-- Trẻ đã nghỉ học (G94-180/CHD-03): học Mẫu giáo bé B1 từ 01/09, nghỉ từ 05/10 ⇒ lớp kết thúc 04/10; PHT Cơ sở 2 cho nghỉ
INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to, created_by)
SELECT t.child_id, cl.id, t.enrolled_on, DATE '2026-10-04', u.id
FROM g180_child_ids t
JOIN campuses c ON c.code = t.campus_code
JOIN classrooms cl ON cl.campus_id = c.id AND cl.name = t.class_name
    AND cl.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027')
JOIN user_accounts u ON u.username = 'principal'
WHERE t.status = 'LEFT' AND NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = t.child_id);

INSERT INTO child_placements (child_id, from_classroom_id, to_classroom_id, placed_by, placed_at)
SELECT t.child_id, NULL, e.classroom_id, e.created_by, (t.enrolled_on + TIME '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM g180_child_ids t
JOIN enrollments e ON e.child_id = t.child_id AND e.valid_from = t.enrolled_on
WHERE t.status = 'LEFT' AND NOT EXISTS (SELECT 1 FROM child_placements p WHERE p.child_id = t.child_id);

INSERT INTO child_history (child_id, action, user_id, note, created_at)
SELECT t.child_id, v.action, u.id, v.note, v.at
FROM g180_child_ids t
CROSS JOIN LATERAL (
    VALUES ('ENROLLED', 'principal', NULL, (t.enrolled_on + TIME '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
           ('WITHDRAWN', 'th.pht.b', 'Gia đình chuyển nơi ở, xin cho trẻ nghỉ học', TIMESTAMPTZ '2026-10-05 08:15+07')
) AS v (action, username, note, at)
JOIN user_accounts u ON u.username = v.username
WHERE (v.action = 'ENROLLED' OR t.status = 'LEFT')
  AND NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = t.child_id AND h.action = v.action);

-- GV lớp nhận "Lớp có trẻ mới" khi tiếp nhận có lớp và "Trẻ nghỉ học" khi cho nghỉ (mục 6.2, G94-180/CHD-03)
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT a.user_id, v.type, v.title, v.body, t.child_id, v.at, v.at + INTERVAL '2 hours'
FROM g180_child_ids t
JOIN enrollments e ON e.child_id = t.child_id AND e.valid_from = t.enrolled_on
JOIN classrooms cl ON cl.id = e.classroom_id
CROSS JOIN LATERAL (
    VALUES ('CHILD_PLACED', 'Lớp có trẻ mới', t.full_name || ' đã được xếp vào ' || cl.name || '.',
            (t.enrolled_on + TIME '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'),
           ('CHILD_WITHDRAWN', 'Trẻ nghỉ học', t.full_name || ' nghỉ học từ ngày 05/10/2026.',
            TIMESTAMPTZ '2026-10-05 08:15+07')
) AS v (type, title, body, at)
JOIN staff_assignments a ON a.classroom_id = cl.id AND a.staff_role = 'TEACHER'
    AND a.valid_from <= (v.at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
    AND (a.valid_to IS NULL OR a.valid_to >= (v.at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
WHERE t.status = 'LEFT'
  AND NOT EXISTS (
      SELECT 1 FROM notifications n WHERE n.user_id = a.user_id AND n.child_id = t.child_id AND n.type = v.type);

-- Khai báo sức khỏe khi tiếp nhận: không có gì đặc biệt (trừ Vũ Thảo Nhi chưa khai)
INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, other_notes_state, self_history_state,
                                       family_history_state, tb_contact_state, declared_by, declared_at)
SELECT t.child_id, 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED', 'NONE_REPORTED',
       u.id, (t.enrolled_on + TIME '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM g180_child_ids t
JOIN user_accounts u ON u.username = 'principal'
WHERE t.declared
ON CONFLICT (child_id) DO NOTHING;

INSERT INTO child_health_declaration_history (child_id, action, user_id, created_at)
SELECT d.child_id, 'DECLARED', d.declared_by, d.declared_at
FROM g180_child_ids t
JOIN child_health_declarations d ON d.child_id = t.child_id
WHERE NOT EXISTS (SELECT 1 FROM child_health_declaration_history h WHERE h.child_id = d.child_id);

-- Sửa khai báo (G94-180/CHD-06): PHT Cơ sở 1 bổ sung dị ứng tôm cho Phạm Khánh Linh ⇒ lịch sử UPDATED kèm allergyChange
CREATE TEMP TABLE g180_allergy AS
SELECT d.child_id, u.id AS user_id
FROM g180_child_ids t
JOIN child_health_declarations d ON d.child_id = t.child_id AND d.allergies_state = 'NONE_REPORTED'
    AND d.declared_at = (t.enrolled_on + TIME '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
JOIN user_accounts u ON u.username = 'viceprincipal'
WHERE t.n = 2;

UPDATE child_health_declarations d
SET allergies_state = 'REPORTED', declared_by = a.user_id, declared_at = TIMESTAMPTZ '2026-11-06 10:00+07'
FROM g180_allergy a
WHERE d.child_id = a.child_id;

INSERT INTO child_allergies (child_id, name)
SELECT a.child_id, 'Tôm' FROM g180_allergy a;

INSERT INTO child_health_declaration_history (child_id, action, user_id, note, allergy_change, created_at)
SELECT a.child_id, 'UPDATED', a.user_id, 'Phụ huynh báo trẻ nổi mẩn đỏ sau khi ăn tôm',
       '{"from": {"state": "NONE_REPORTED", "items": []}, "to": {"state": "REPORTED", "items": ["Tôm"]}}'::jsonb,
       TIMESTAMPTZ '2026-11-06 10:00+07'
FROM g180_allergy a;

-- 2. Chuyển lớp (CHD-10, G94-180/CHD-02), hiệu lực 09/11/2026: HS26231 Mẫu giáo lớn A1 → A2 (PHT Cơ sở 1, cùng điểm
-- trường) và HS26271 Mẫu giáo nhỡ B1 → Lớp A1 (HT, sang điểm trường khác, kèm sửa địa chỉ). Lớp cũ kết thúc 08/11; lịch sử
-- xếp lớp gồm lần xếp khi tiếp nhận 01/09 và lần chuyển. Chỉ làm khi trẻ còn học lớp ban đầu từ 01/09, chưa có lịch sử
CREATE TEMP TABLE g180_transfers AS
SELECT ch.id AS child_id, ch.full_name, e.id AS enrollment_id, src.id AS from_id, dst.id AS to_id, dst.name AS to_name,
       u.id AS actor_id, v.reason, v.move_address
FROM (VALUES ('HS26231', 'CS-A', 'Lớp Mẫu giáo lớn A1', 'CS-A', 'Lớp Mẫu giáo lớn A2', 'viceprincipal',
              'Cân đối sĩ số hai lớp Mẫu giáo lớn', FALSE),
             ('HS26271', 'CS-B', 'Lớp Mẫu giáo nhỡ B1', 'CS-A', 'Lớp A1', 'principal',
              'Gia đình chuyển nhà, xin cho trẻ học tại Cơ sở 1', TRUE))
         AS v (code, from_campus, from_class, to_campus, to_class, actor, reason, move_address)
JOIN school_years y ON y.name = '2026-2027'
JOIN children ch ON ch.code = v.code AND ch.status = 'ACTIVE'
JOIN campuses fc ON fc.code = v.from_campus
JOIN classrooms src ON src.campus_id = fc.id AND src.school_year_id = y.id AND src.name = v.from_class
JOIN campuses tc ON tc.code = v.to_campus
JOIN classrooms dst ON dst.campus_id = tc.id AND dst.school_year_id = y.id AND dst.name = v.to_class
JOIN enrollments e ON e.child_id = ch.id AND e.classroom_id = src.id AND e.valid_from = DATE '2026-09-01'
    AND e.valid_to IS NULL
JOIN user_accounts u ON u.username = v.actor
WHERE NOT EXISTS (SELECT 1 FROM child_placements p WHERE p.child_id = ch.id);

INSERT INTO child_placements (child_id, from_classroom_id, to_classroom_id, placed_by, reason, placed_at)
SELECT t.child_id, NULL, t.from_id, p.id, NULL, TIMESTAMPTZ '2026-09-01 08:00+07'
FROM g180_transfers t
JOIN user_accounts p ON p.username = 'principal'
UNION ALL
SELECT t.child_id, t.from_id, t.to_id, t.actor_id, t.reason, TIMESTAMPTZ '2026-11-09 07:45+07'
FROM g180_transfers t;

UPDATE enrollments e
SET valid_to = DATE '2026-11-08', updated_at = TIMESTAMPTZ '2026-11-09 07:45+07'
FROM g180_transfers t
WHERE e.id = t.enrollment_id;

INSERT INTO enrollments (child_id, classroom_id, valid_from, created_by, created_at, updated_at)
SELECT t.child_id, t.to_id, DATE '2026-11-09', t.actor_id, TIMESTAMPTZ '2026-11-09 07:45+07',
       TIMESTAMPTZ '2026-11-09 07:45+07'
FROM g180_transfers t;

-- Sửa hồ sơ trước khi chuyển điểm trường: lịch sử UPDATED ghi từng ô đổi (CHD-05)
INSERT INTO child_history (child_id, action, user_id, note, changes, created_at)
SELECT t.child_id, 'UPDATED', t.actor_id, 'Gia đình chuyển nhà, cập nhật địa chỉ',
       jsonb_build_array(
           jsonb_build_object('field', 'profile.address.ward', 'label', 'Phường/xã', 'from', p.address_ward,
                              'to', 'Thôn 1'),
           jsonb_build_object('field', 'profile.address.street', 'label', 'Địa chỉ', 'from', p.address_street,
                              'to', 'Số 18 ngõ 5')),
       TIMESTAMPTZ '2026-11-09 07:40+07'
FROM g180_transfers t
JOIN child_profiles p ON p.child_id = t.child_id
WHERE t.move_address;

UPDATE child_profiles p
SET address_ward = 'Thôn 1', address_street = 'Số 18 ngõ 5'
FROM g180_transfers t
WHERE p.child_id = t.child_id AND t.move_address;

INSERT INTO notifications (user_id, type, title, body, child_id, created_at)
SELECT a.user_id, 'CHILD_PLACED', 'Lớp có trẻ mới', t.full_name || ' đã được xếp vào ' || t.to_name || '.', t.child_id,
       TIMESTAMPTZ '2026-11-09 07:45+07'
FROM g180_transfers t
JOIN staff_assignments a ON a.classroom_id = t.to_id AND a.staff_role = 'TEACHER'
    AND a.valid_from <= DATE '2026-11-09' AND (a.valid_to IS NULL OR a.valid_to >= DATE '2026-11-09');

-- 3. Yêu cầu đổi SĐT phụ huynh có tài khoản (G94-180/AUTH-21): 3 chờ duyệt (HT duyệt yêu cầu PHT gửi, PHT Cơ sở 1 duyệt
-- yêu cầu HT gửi), 1 đã duyệt, 1 bị từ chối, 1 bị yêu cầu sau thay thế (CANCELLED). Tài khoản = phụ huynh thứ nhất (đã kích
-- hoạt) của trẻ Thượng Hồng; số mới dải 0909180xxx. Đã duyệt: tên đăng nhập hiện tại là số mới, số cũ là số trước khi đổi
CREATE TEMP TABLE g180_phone AS
SELECT v.status, v.other_phone, v.reason, v.requested_at, v.decided_at, v.decision_note, ch.id AS child_id,
       ch.full_name AS child_name, g.id AS guardian_id, acc.id AS user_id, acc.username AS phone,
       acc.full_name AS account_name, rq.id AS requested_by, rq.full_name AS requester_name, dc.id AS decided_by,
       dc.full_name AS decider_name, cl.campus_id
FROM (VALUES ('HS26103', 'viceprincipal', '0909180101', 'CANCELLED', TIMESTAMPTZ '2026-11-08 09:00+07', NULL::text,
              NULL::timestamptz, NULL::text, 'Phụ huynh báo đổi số điện thoại'),
             ('HS26103', 'viceprincipal', '0909180102', 'PENDING', TIMESTAMPTZ '2026-11-09 10:15+07', NULL, NULL, NULL,
              'Phụ huynh báo nhầm số ở lần trước, đổi sang số đúng'),
             ('HS26121', 'th.pht.b', '0909180103', 'PENDING', TIMESTAMPTZ '2026-11-10 08:00+07', NULL, NULL, NULL,
              'Phụ huynh mất SIM cũ, đăng ký số mới'),
             ('HS26105', 'principal', '0909180104', 'PENDING', TIMESTAMPTZ '2026-11-10 08:45+07', NULL, NULL, NULL,
              'Phụ huynh chuyển nhà mạng, dùng số mới'),
             ('HS26107', 'viceprincipal', '0909180105', 'APPROVED', TIMESTAMPTZ '2026-10-20 14:00+07', 'principal',
              TIMESTAMPTZ '2026-10-21 08:30+07', NULL, 'Phụ huynh đổi số điện thoại'),
             ('HS26123', 'th.pht.b', '0909180106', 'REJECTED', TIMESTAMPTZ '2026-10-27 15:00+07', 'principal',
              TIMESTAMPTZ '2026-10-28 09:00+07', 'Chưa liên lạc được với phụ huynh qua số mới, đề nghị xác minh lại',
              'Phụ huynh báo đổi số điện thoại'))
         AS v (child_code, requester, other_phone, status, requested_at, decider, decided_at, decision_note, reason)
JOIN children ch ON ch.code = v.child_code AND ch.status = 'ACTIVE'
JOIN child_guardians g ON g.child_id = ch.id AND g.position = 0
JOIN user_accounts acc ON acc.id = g.user_id AND acc.username = g.phone AND acc.primary_role = 'PARENT'
JOIN enrollments e ON e.child_id = ch.id AND e.valid_to IS NULL
JOIN classrooms cl ON cl.id = e.classroom_id
JOIN user_accounts rq ON rq.username = v.requester
LEFT JOIN user_accounts dc ON dc.username = v.decider
WHERE NOT EXISTS (
    SELECT 1 FROM guardian_phone_change_requests r WHERE r.user_id = acc.id AND r.requested_at = v.requested_at);

INSERT INTO guardian_phone_change_requests (user_id, child_id, guardian_id, old_phone, new_phone, status, reason,
                                            requested_by, requested_at, decided_by, decided_at, decision_note,
                                            sms_status, created_at, updated_at)
SELECT p.user_id, p.child_id, p.guardian_id,
       CASE WHEN p.status = 'APPROVED' THEN p.other_phone ELSE p.phone END,
       CASE WHEN p.status = 'APPROVED' THEN p.phone ELSE p.other_phone END,
       p.status, p.reason, p.requested_by, p.requested_at, p.decided_by, p.decided_at, p.decision_note,
       CASE WHEN p.status = 'APPROVED' THEN 'SENT' END, p.requested_at,
       COALESCE(p.decided_at, CASE WHEN p.status = 'CANCELLED' THEN TIMESTAMPTZ '2026-11-09 10:15+07' END,
                p.requested_at)
FROM g180_phone p
WHERE p.status <> 'PENDING'
   OR NOT EXISTS (
       SELECT 1 FROM guardian_phone_change_requests x WHERE x.user_id = p.user_id AND x.status = 'PENDING');

-- Người duyệt (HT + PHT điểm trường của trẻ, trừ người gửi) nhận yêu cầu; người gửi nhận kết quả duyệt/từ chối
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT r.user_id, 'GUARDIAN_PHONE_CHANGE_REQUESTED', 'Yêu cầu đổi số điện thoại phụ huynh',
       p.requester_name || ' đề nghị đổi số điện thoại đăng nhập của ' || p.account_name || ' (phụ huynh của '
           || p.child_name || '). Duyệt ở màn Tài khoản phụ huynh.',
       p.child_id, p.requested_at, CASE WHEN p.status <> 'PENDING' THEN p.requested_at + INTERVAL '1 hour' END
FROM g180_phone p
CROSS JOIN LATERAL (
    SELECT u.id AS user_id FROM user_accounts u WHERE u.username = 'principal'
    UNION
    SELECT a.user_id FROM staff_assignments a
    WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = p.campus_id
      AND a.valid_from <= (p.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
      AND (a.valid_to IS NULL OR a.valid_to >= (p.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
) r
WHERE r.user_id <> p.requested_by
  AND EXISTS (
      SELECT 1 FROM guardian_phone_change_requests x WHERE x.user_id = p.user_id AND x.requested_at = p.requested_at)
UNION ALL
SELECT p.requested_by, 'GUARDIAN_PHONE_CHANGE_DECIDED',
       CASE WHEN p.status = 'APPROVED' THEN 'Đã duyệt đổi số điện thoại phụ huynh'
            ELSE 'Không duyệt đổi số điện thoại phụ huynh' END,
       CASE WHEN p.status = 'APPROVED'
                THEN p.decider_name || ' đã duyệt đổi số điện thoại đăng nhập của ' || p.account_name
                    || '. Tài khoản mới đã gửi qua SMS tới số mới.'
            ELSE p.decider_name || ' không duyệt đổi số điện thoại đăng nhập của ' || p.account_name || ': '
                    || p.decision_note
       END,
       p.child_id, p.decided_at, p.decided_at + INTERVAL '2 hours'
FROM g180_phone p
WHERE p.status IN ('APPROVED', 'REJECTED') AND p.decided_by <> p.requested_by
  AND EXISTS (
      SELECT 1 FROM guardian_phone_change_requests x WHERE x.user_id = p.user_id AND x.requested_at = p.requested_at);

-- 4. Tiếp xúc người bệnh lao (G94-180/CHD-07): PHT Cơ sở 1 sửa khai báo của HS26102 (Lớp A1) ⇒ lịch sử UPDATED kèm
-- historyChange; HT, PHT điểm trường, GV lớp, người lưu nhận thông báo. Chỉ làm khi khai báo còn như lúc tiếp nhận
CREATE TEMP TABLE g180_tb AS
SELECT ch.id AS child_id, ch.full_name, u.id AS saver_id
FROM children ch
JOIN child_health_declarations d ON d.child_id = ch.id AND d.tb_contact_state = 'NONE_REPORTED'
    AND d.declared_at = TIMESTAMPTZ '2026-09-01 08:00+07'
JOIN user_accounts u ON u.username = 'viceprincipal'
WHERE ch.code = 'HS26102' AND ch.status = 'ACTIVE';

INSERT INTO child_health_declaration_history (child_id, action, user_id, note, history_change, created_at)
SELECT t.child_id, 'UPDATED', t.saver_id, 'Phụ huynh báo ông nội đang được theo dõi lao phổi tại trạm y tế',
       jsonb_build_object(
           'from', jsonb_build_object(
               'self', jsonb_build_object('state', d.self_history_state, 'text', d.self_history_text),
               'family', jsonb_build_object('state', d.family_history_state, 'text', d.family_history_text),
               'tbContact', jsonb_build_object('state', d.tb_contact_state, 'contactDate', d.tb_contact_date)),
           'to', jsonb_build_object(
               'self', jsonb_build_object('state', d.self_history_state, 'text', d.self_history_text),
               'family', jsonb_build_object('state', d.family_history_state, 'text', d.family_history_text),
               'tbContact', jsonb_build_object('state', 'REPORTED', 'contactDate', '2026-10-20'))),
       TIMESTAMPTZ '2026-10-28 09:30+07'
FROM g180_tb t
JOIN child_health_declarations d ON d.child_id = t.child_id;

INSERT INTO notifications (user_id, type, title, body, child_id, created_at)
SELECT r.user_id, 'CHILD_TB_CONTACT', 'Trẻ có tiền sử tiếp xúc với người bệnh lao',
       'Phụ huynh khai ' || t.full_name || ' có tiền sử tiếp xúc với người bệnh lao (gần nhất ngày 20/10/2026). Nhà'
           || ' trường trao đổi với phụ huynh về việc đưa trẻ đi khám sàng lọc; bác sĩ xem lại ở đợt khám tới.',
       t.child_id, TIMESTAMPTZ '2026-10-28 09:30+07'
FROM g180_tb t
CROSS JOIN LATERAL (
    SELECT u.id AS user_id FROM user_accounts u WHERE u.username = 'principal'
    UNION
    SELECT t.saver_id
    UNION
    SELECT a.user_id
    FROM enrollments e
    JOIN classrooms cl ON cl.id = e.classroom_id
    JOIN staff_assignments a ON a.valid_from <= DATE '2026-10-28' AND (a.valid_to IS NULL OR a.valid_to >= DATE '2026-10-28')
        AND ((a.staff_role = 'TEACHER' AND a.classroom_id = cl.id)
             OR (a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = cl.campus_id))
    WHERE e.child_id = t.child_id AND e.valid_from <= DATE '2026-10-28'
      AND (e.valid_to IS NULL OR e.valid_to >= DATE '2026-10-28')
) r;

UPDATE child_health_declarations d
SET tb_contact_state = 'REPORTED', tb_contact_date = DATE '2026-10-20', declared_by = t.saver_id,
    declared_at = TIMESTAMPTZ '2026-10-28 09:30+07'
FROM g180_tb t
WHERE d.child_id = t.child_id;

-- 5. Hai kỳ cân đo đã đóng mỗi điểm trường (HLT-01, HLT-17 hệ thống tự đóng sau ngày kết thúc): đầu năm 07–11/09 và tháng 11
-- 02–06/11/2026. Mỗi lớp đo một ngày trong kỳ; số đo gần trung vị WHO theo tuổi, giới + độ lệch cố định của từng trẻ; ~2,5%
-- vắng. Ca cần chú ý: Trẻ Demo B1-01 (con parent2) sụt cân giữa hai lần đo; HS26106 nhẹ cân so với tuổi
CREATE TEMP TABLE g180_campaigns AS
SELECT *
FROM (VALUES (1, 'Kỳ cân đo đầu năm học 2026-2027', DATE '2026-09-07', DATE '2026-09-11', TIMESTAMPTZ '2026-09-03 08:00+07'),
             (2, 'Kỳ cân đo tháng 11/2026', DATE '2026-11-02', DATE '2026-11-06', TIMESTAMPTZ '2026-10-28 08:00+07'))
         AS v (round, name, start_date, end_date, created_at);

INSERT INTO health_check_campaigns (kind, campus_id, name, start_date, end_date, created_by, created_at, updated_at,
                                    closed_at)
SELECT 'MEASUREMENT', c.id, v.name, v.start_date, v.end_date, u.id, v.created_at, v.created_at,
       (v.end_date + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM g180_campaigns v
CROSS JOIN campuses c
JOIN user_accounts u ON u.username = 'principal'
WHERE c.code IN ('CS-A', 'CS-B')
  AND NOT EXISTS (
      SELECT 1 FROM health_check_campaigns x
      WHERE x.campus_id = c.id AND x.kind = 'MEASUREMENT' AND x.start_date = v.start_date);

CREATE TEMP TABLE g180_rounds AS
SELECT x.id AS campaign_id, x.campus_id, x.name, x.start_date, x.end_date, x.created_by, x.created_at, x.closed_at,
       x.closed_by, v.round
FROM health_check_campaigns x
JOIN g180_campaigns v ON v.name = x.name AND v.start_date = x.start_date
WHERE x.kind = 'MEASUREMENT';

INSERT INTO health_check_campaign_history (campaign_id, action, user_id, created_at)
SELECT r.campaign_id, 'CREATED', r.created_by, r.created_at
FROM g180_rounds r
WHERE NOT EXISTS (
    SELECT 1 FROM health_check_campaign_history h WHERE h.campaign_id = r.campaign_id AND h.action = 'CREATED')
UNION ALL
SELECT r.campaign_id, 'CLOSED', NULL, r.closed_at
FROM g180_rounds r
WHERE r.closed_at IS NOT NULL AND r.closed_by IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM health_check_campaign_history h WHERE h.campaign_id = r.campaign_id AND h.action = 'CLOSED');

-- Lớp thứ k (theo tên) của điểm trường đo vào ngày thứ k % 5 của kỳ; trẻ thuộc lớp theo enrollment tại ngày đo
CREATE TEMP TABLE g180_measure AS
SELECT d.campaign_id, d.round, d.classroom_id, d.day, ch.id AS child_id, ch.code, ch.gender,
       (d.day - ch.date_of_birth) / 30.4375 AS age_months,
       (SELECT a.user_id FROM staff_assignments a
        WHERE a.classroom_id = d.classroom_id AND a.staff_role = 'TEACHER' AND a.valid_from <= d.day
          AND (a.valid_to IS NULL OR a.valid_to >= d.day)
        ORDER BY a.is_homeroom DESC, a.id
        LIMIT 1) AS teacher_id,
       ch.code NOT IN ('HS26001', 'HS26003', 'HS26106', 'HS26231', 'HS26271')
           AND abs(hashtext('g180-vang-' || ch.code || '-' || d.round)::bigint) % 40 = 0 AS absent
FROM (
    SELECT r.campaign_id, r.round, cl.id AS classroom_id,
           r.start_date + ((row_number() OVER (PARTITION BY r.campaign_id ORDER BY cl.name) - 1) % 5)::int AS day
    FROM g180_rounds r
    JOIN classrooms cl ON cl.campus_id = r.campus_id
    JOIN school_years y ON y.id = cl.school_year_id AND y.name = '2026-2027'
) d
JOIN enrollments e ON e.classroom_id = d.classroom_id AND e.valid_from <= d.day
    AND (e.valid_to IS NULL OR e.valid_to >= d.day)
JOIN children ch ON ch.id = e.child_id;

INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date, height_cm, weight_kg, measured_by, published_at,
                                 published_by, created_at, updated_at)
SELECT m.child_id, m.campaign_id, m.classroom_id, m.day,
       round(CASE WHEN m.gender = 'MALE' THEN 88.0 ELSE 87.3 END + (m.age_months - 24) * 0.59 + o.dh + o.nh, 1),
       round(CASE WHEN m.gender = 'MALE' THEN 12.2 ELSE 11.6 END + (m.age_months - 24) * 0.17 + o.dw + o.nw, 1),
       m.teacher_id, o.at, m.teacher_id, o.at, o.at
FROM g180_measure m
CROSS JOIN LATERAL (
    SELECT CASE WHEN m.code IN ('HS26001', 'HS26003') THEN 0
                ELSE (abs(hashtext('g180-cao-' || m.code)::bigint) % 81 - 40) / 10.0 END AS dh,
           CASE WHEN m.code IN ('HS26001', 'HS26003') THEN 0
                WHEN m.code = 'HS26106' THEN -4.0
                ELSE (abs(hashtext('g180-nang-' || m.code)::bigint) % 31 - 15) / 10.0 END
               - CASE WHEN m.code = 'HS26003' AND m.round = 2 THEN 0.6 ELSE 0 END AS dw,
           CASE WHEN m.code IN ('HS26001', 'HS26003') THEN 0
                ELSE (abs(hashtext('g180-sc-' || m.code || '-' || m.round)::bigint) % 3 - 1) / 10.0 END AS nh,
           CASE WHEN m.code IN ('HS26001', 'HS26003') THEN 0
                ELSE (abs(hashtext('g180-sn-' || m.code || '-' || m.round)::bigint) % 3 - 1) / 10.0 END AS nw,
           (m.day + TIME '08:30' + (abs(hashtext('g180-gio-' || m.code)::bigint) % 150) * INTERVAL '1 minute')
               AT TIME ZONE 'Asia/Ho_Chi_Minh' AS at
) o
WHERE NOT m.absent AND m.teacher_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Đóng kỳ: trẻ chưa có kết quả thành vắng, kèm lớp lúc đó (HLT-17)
INSERT INTO health_check_absences (campaign_id, child_id, classroom_id)
SELECT m.campaign_id, m.child_id, m.classroom_id
FROM g180_measure m
WHERE m.absent
  AND NOT EXISTS (
      SELECT 1 FROM health_measurements h WHERE h.child_id = m.child_id AND h.campaign_id = m.campaign_id)
ON CONFLICT DO NOTHING;

-- Mở kỳ: GV lớp + PHT điểm trường nhận "Kỳ cân đo mới"; lưu số đo: phụ huynh liên kết nhận "Kết quả sức khỏe mới"
-- (HLT-11, câu không ghi số đo); kết quả kỳ tháng 11 chưa đọc
INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT DISTINCT a.user_id, 'HEALTH_CHECK_OPENED', 'Kỳ cân đo mới',
       'Kỳ cân đo "' || r.name || '" đã mở, từ ngày ' || to_char(r.start_date, 'DD/MM/YYYY') || ' đến ngày '
           || to_char(r.end_date, 'DD/MM/YYYY') || '.',
       NULL::bigint, r.created_at, r.created_at + INTERVAL '2 hours'
FROM g180_rounds r
JOIN staff_assignments a ON a.valid_from <= (r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
    AND (a.valid_to IS NULL OR a.valid_to >= (r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
    AND ((a.staff_role = 'VICE_PRINCIPAL' AND a.campus_id = r.campus_id)
         OR (a.staff_role = 'TEACHER'
             AND a.classroom_id IN (SELECT cl.id FROM classrooms cl WHERE cl.campus_id = r.campus_id)))
WHERE NOT EXISTS (
    SELECT 1 FROM notifications n
    WHERE n.user_id = a.user_id AND n.type = 'HEALTH_CHECK_OPENED' AND n.created_at = r.created_at);

INSERT INTO notifications (user_id, type, title, body, child_id, created_at, read_at)
SELECT l.user_id, 'HEALTH_RESULT_PUBLISHED', 'Kết quả sức khỏe mới',
       'Đã có kết quả cân đo, khám sức khỏe mới của ' || ch.full_name || '. Mở mục Sức khỏe để xem.',
       h.child_id, h.published_at, CASE WHEN r.round = 1 THEN h.published_at + INTERVAL '3 hours' END
FROM health_measurements h
JOIN g180_rounds r ON r.campaign_id = h.campaign_id
JOIN children ch ON ch.id = h.child_id
JOIN guardian_child_links l ON l.child_id = h.child_id
WHERE NOT EXISTS (
    SELECT 1 FROM notifications n
    WHERE n.user_id = l.user_id AND n.child_id = h.child_id AND n.type = 'HEALTH_RESULT_PUBLISHED'
      AND n.created_at = h.published_at);

-- 6. Giải thích xu hướng (HLT-02..05, AI-01, AI-02): Trẻ Demo A1-01 (con parent1) có bản đã duyệt ⇒ phụ huynh xem được;
-- Trẻ Demo B1-01 (con parent2) có bản nháp kèm cảnh báo sụt cân ⇒ chờ nhân sự duyệt. Câu cảnh báo như HealthGrowth.warnings
INSERT INTO health_interpretations (child_id, status, summary, warnings, measurement_ids, source, model, generated_by,
                                    generated_at, approved_by, approved_at, created_at, updated_at)
SELECT ch.id, v.status,
       replace(replace(v.summary, '{d1}', to_char(m.d1, 'DD/MM/YYYY')), '{d2}', to_char(m.d2, 'DD/MM/YYYY')),
       CASE WHEN m.w1 - m.w2 >= 0.2
                THEN jsonb_build_array(jsonb_build_object(
                    'level', 'warning',
                    'text', 'Cân nặng lần đo ngày ' || to_char(m.d2, 'DD/MM/YYYY') || ' giảm '
                        || rtrim(rtrim((m.w1 - m.w2)::text, '0'), '.') || ' kg so với lần đo ngày '
                        || to_char(m.d1, 'DD/MM/YYYY') || '.'))
            ELSE '[{"level": "info", "text": "Các chỉ số lần đo gần nhất nằm trong khoảng bình thường theo chuẩn WHO."}]'::jsonb
       END,
       m.ids, 'AI', 'gemini-2.0-flash', u.id, v.generated_at,
       CASE WHEN v.status = 'APPROVED' THEN u.id END,
       CASE WHEN v.status = 'APPROVED' THEN v.generated_at + INTERVAL '5 minutes' END,
       v.generated_at,
       v.generated_at + CASE WHEN v.status = 'APPROVED' THEN INTERVAL '5 minutes' ELSE INTERVAL '0' END
FROM (VALUES ('HS26001', 'APPROVED', 'teacher1', TIMESTAMPTZ '2026-11-07 09:00+07',
              'Hai lần cân đo ngày {d1} và {d2} cho thấy chiều cao và cân nặng của bé tăng đều theo thời gian, các chỉ số'
                  || ' nằm trong khoảng bình thường theo chuẩn WHO cho trẻ cùng tuổi và giới. Gia đình và nhà trường tiếp'
                  || ' tục duy trì bữa ăn đủ chất, giấc ngủ đủ giờ và cho bé vận động hằng ngày.'),
             ('HS26003', 'DRAFT', 'teacher2', TIMESTAMPTZ '2026-11-08 14:00+07',
              'Ở lần cân đo ngày {d2}, cân nặng của bé giảm nhẹ so với lần đo ngày {d1}, trong khi chiều cao vẫn tăng.'
                  || ' Nhà trường nên trao đổi với gia đình về bữa ăn, giấc ngủ của bé ở nhà và theo dõi cân nặng ở lần'
                  || ' cân đo tới; nếu cân nặng tiếp tục giảm, gia đình nên đưa bé đến cơ sở y tế để được tư vấn.'))
         AS v (code, status, username, generated_at, summary)
JOIN children ch ON ch.code = v.code
JOIN user_accounts u ON u.username = v.username
JOIN LATERAL (
    SELECT max(h.date) FILTER (WHERE h.rn = 2) AS d1, max(h.date) FILTER (WHERE h.rn = 1) AS d2,
           max(h.weight_kg) FILTER (WHERE h.rn = 2) AS w1, max(h.weight_kg) FILTER (WHERE h.rn = 1) AS w2,
           jsonb_agg(h.id ORDER BY h.date) AS ids, count(*) AS n
    FROM (SELECT x.id, x.date, x.weight_kg, row_number() OVER (ORDER BY x.date DESC) AS rn
          FROM health_measurements x
          WHERE x.child_id = ch.id AND x.published_at IS NOT NULL) h
    WHERE h.rn <= 2
) m ON m.n = 2
WHERE NOT EXISTS (SELECT 1 FROM health_interpretations i WHERE i.child_id = ch.id);

DROP TABLE g180_children, g180_child_ids, g180_allergy, g180_transfers, g180_phone, g180_tb, g180_campaigns, g180_rounds,
    g180_measure;
