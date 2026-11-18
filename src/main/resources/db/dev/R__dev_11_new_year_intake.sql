-- Dữ liệu dev: đủ trường hợp tiếp nhận vào năm 2027-2028 (CHD-11, CHD-13) để thử trên BE local; chạy lại không nhân đôi.
--   HS26311 CS-A, đã xếp Lớp Mầm 1, bắt đầu đầu năm, mẹ dùng chung tài khoản với HS26001 đang học, bố chưa có tài khoản
--   HS26312 CS-B, đã xếp Lớp Nhà trẻ 2, bắt đầu muộn 01/10/2027
--   HS26313 CS-A, chưa xếp lớp, không báo ăn (khai báo dị ứng, chế độ ăn: chưa cung cấp)
--   HS26314 CS-A, đã đăng ký rồi thôi học (đăng ký CANCELLED)
-- HS26301, HS26302 (R__dev_10 của Cần) được bổ sung phụ huynh, hồ sơ, khai báo để mở được đủ hồ sơ.
INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on, left_on, left_reason,
                      meal_registered)
SELECT v.code, v.full_name, v.dob::date, v.gender, v.status, c.id, v.enrolled_on::date,
       CASE WHEN v.status = 'LEFT' THEN CURRENT_DATE - 2 END, v.left_reason, v.meal_registered
FROM (VALUES
    ('HS26311', 'Phạm Gia Hưng', '2024-04-18', 'MALE', 'PENDING_PLACEMENT', 'CS-A', '2026-11-17', NULL, true),
    ('HS26312', 'Trần Khánh An', '2025-02-11', 'FEMALE', 'PENDING_PLACEMENT', 'CS-B', '2026-11-17', NULL, true),
    ('HS26313', 'Ngô Bảo Châu', '2024-09-05', 'FEMALE', 'PENDING_PLACEMENT', 'CS-A', '2026-11-16', NULL, false),
    ('HS26314', 'Bùi Đăng Khoa', '2024-01-20', 'MALE', 'LEFT', 'CS-A', '2026-11-10',
     'Gia đình chuyển công tác sang tỉnh khác, xin hủy đăng ký học năm sau', true)
) AS v (code, full_name, dob, gender, status, campus, enrolled_on, left_reason, meal_registered)
JOIN campuses c ON c.code = v.campus
ON CONFLICT (code) DO NOTHING;

INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT left(code, 4), max(substring(code FROM 5)::integer) FROM children GROUP BY left(code, 4)
ON CONFLICT (code_prefix) DO UPDATE SET last_number = GREATEST(child_code_sequences.last_number, EXCLUDED.last_number);

-- Đăng ký vào năm sau: lớp lấy theo tên trong cùng điểm trường và năm (R__dev_06 sao lớp từ năm hiện tại)
INSERT INTO planned_admissions (child_id, school_year_id, campus_id, classroom_id, start_date, status, created_by)
SELECT ch.id, y.id, camp.id, cl.id, COALESCE(v.start_date::date, y.start_date), v.status,
       (SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'VICE_PRINCIPAL'
            AND a.campus_id = camp.id ORDER BY a.id LIMIT 1)
FROM (VALUES
    ('HS26311', 'CS-A', 'Lớp Mầm 1', NULL, 'PLACED'),
    ('HS26312', 'CS-B', 'Lớp Nhà trẻ 2', '2027-10-01', 'PLACED'),
    ('HS26313', 'CS-A', NULL, NULL, 'PENDING'),
    ('HS26314', 'CS-A', NULL, NULL, 'CANCELLED')
) AS v (code, campus, class_name, start_date, status)
JOIN children ch ON ch.code = v.code
JOIN campuses camp ON camp.code = v.campus
JOIN school_years y ON y.name = '2027-2028' AND y.status = 'PLANNED'
LEFT JOIN classrooms cl ON cl.campus_id = camp.id AND cl.school_year_id = y.id AND cl.name = v.class_name
ON CONFLICT (child_id, school_year_id) DO NOTHING;

-- Phụ huynh: đúng 1 người nhận tài khoản mỗi trẻ; mẹ HS26311 là tài khoản 0985950454 (cũng nhận cho HS26001)
INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, account_recipient, user_id, linked_at)
SELECT ch.id, v.position::smallint, v.full_name, v.relationship, v.phone, v.citizen_id, v.recipient, u.id,
       CASE WHEN u.id IS NULL THEN NULL ELSE (ch.enrolled_on + time '19:30') AT TIME ZONE 'Asia/Ho_Chi_Minh' END
FROM (VALUES
    ('HS26311', 0, 'Nguyễn Thùy Linh', 'MOTHER', '0985950454', '031192944511', true, '00000000-0000-4000-e000-000000000001'),
    ('HS26311', 1, 'Phạm Quốc Việt', 'FATHER', '0973208114', '031191458263', false, NULL),
    ('HS26312', 0, 'Đinh Thu Hà', 'MOTHER', '0968417725', '030189702514', true, NULL),
    ('HS26312', 1, 'Trần Văn Thắng', 'FATHER', '0944129358', '030392817645', false, NULL),
    ('HS26313', 0, 'Lương Thị Hồng Nhung', 'MOTHER', '0335907846', '030187254096', true, NULL),
    ('HS26313', 1, 'Ngô Đức Minh', 'FATHER', '0378214569', '031190368472', false, NULL),
    ('HS26314', 0, 'Vũ Thị Lan Anh', 'MOTHER', '0861234097', '030088915327', true, NULL),
    ('HS26314', 1, 'Bùi Văn Hải', 'FATHER', '0902718345', '030291673840', false, NULL),
    ('HS26301', 0, 'Hoàng Thị Mai', 'MOTHER', '0919640237', '031087420695', true, NULL),
    ('HS26301', 1, 'Lê Văn Tùng', 'FATHER', '0947385102', '030189356214', false, NULL),
    ('HS26302', 0, 'Phan Thị Quỳnh', 'MOTHER', '0328471960', '030393748105', true, NULL),
    ('HS26302', 1, 'Đỗ Quang Huy', 'FATHER', '0869253014', '031191085236', false, NULL)
) AS v (code, position, full_name, relationship, phone, citizen_id, recipient, keycloak_user_id)
JOIN children ch ON ch.code = v.code
LEFT JOIN user_accounts u ON u.keycloak_user_id = v.keycloak_user_id
WHERE NOT EXISTS (SELECT 1 FROM child_guardians g WHERE g.child_id = ch.id AND g.position = v.position::smallint);

INSERT INTO child_profiles (child_id, id_number, premature, gestation_weeks, ethnicity, beneficiary, blood_type,
                            address_province, address_ward, address_street)
SELECT ch.id, v.id_number, v.premature, v.gestation_weeks::smallint, v.ethnicity, v.beneficiary, v.blood_type,
       v.province, v.ward, v.street
FROM (VALUES
    ('HS26311', '030224591837', 'NO', NULL, 'Kinh', 'Không', 'A', 'Thành phố Hải Phòng', 'Xã Thượng Hồng', 'Số 14, Thôn Ngọc Lâm'),
    ('HS26312', '030325160473', 'NO', NULL, 'Kinh', 'Không', 'B', 'Thành phố Hải Phòng', 'Xã Đường An', 'Số 52, Thôn Bùi Xá'),
    ('HS26313', '030324687025', 'NO', NULL, 'Kinh', 'Không', NULL, 'Thành phố Hải Phòng', 'Xã Thượng Hồng', 'Số 9, Thôn Đồng Tâm'),
    ('HS26314', '030223905718', 'NO', NULL, 'Kinh', 'Không', 'O', 'Thành phố Hải Phòng', 'Xã Thượng Hồng', 'Số 71, Thôn An Thái'),
    ('HS26301', '030224283690', 'NO', NULL, 'Kinh', 'Không', 'A', 'Thành phố Hải Phòng', 'Xã Đường An', 'Số 33, Thôn Bùi Xá'),
    ('HS26302', '030223748206', 'NO', NULL, 'Kinh', 'Không', 'O', 'Thành phố Hải Phòng', 'Xã Thượng Hồng', 'Số 88, Thôn Ngọc Lâm')
) AS v (code, id_number, premature, gestation_weeks, ethnicity, beneficiary, blood_type, province, ward, street)
JOIN children ch ON ch.code = v.code
ON CONFLICT (child_id) DO NOTHING;

-- Khai báo sức khỏe; trẻ không báo ăn (HS26313) để dị ứng, chế độ ăn là "chưa cung cấp" (CHD-13)
INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, diet_text, other_notes_state,
                                       self_history_state, self_history_text, family_history_state, family_history_text,
                                       tb_contact_state, tb_contact_date, declared_by, declared_at)
SELECT ch.id, v.allergies_state, v.diet_state, v.diet_text, v.other_state, v.self_state, v.self_text, v.family_state,
       v.family_text, v.tb_state, v.tb_date::date, (SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'VICE_PRINCIPAL'
        AND a.campus_id = ch.intake_campus_id ORDER BY a.id LIMIT 1), (ch.enrolled_on + time '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM (VALUES
    ('HS26311', 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL),
    ('HS26312', 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL),
    ('HS26313', 'NOT_PROVIDED', 'NOT_PROVIDED', NULL, 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL),
    ('HS26314', 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL),
    ('HS26301', 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL),
    ('HS26302', 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL, 'NONE_REPORTED', NULL)
) AS v (code, allergies_state, diet_state, diet_text, other_state, self_state, self_text, family_state,
        family_text, tb_state, tb_date)
JOIN children ch ON ch.code = v.code
ON CONFLICT (child_id) DO NOTHING;

-- Lịch sử khai báo và hồ sơ như trẻ khác: nhập học, riêng HS26314 có dòng nghỉ học
INSERT INTO child_health_declaration_history (child_id, action, user_id, created_at)
SELECT d.child_id, 'DECLARED', d.declared_by, d.declared_at
FROM child_health_declarations d
JOIN children ch ON ch.id = d.child_id
WHERE ch.code IN ('HS26311', 'HS26312', 'HS26313', 'HS26314', 'HS26301', 'HS26302')
  AND NOT EXISTS (SELECT 1 FROM child_health_declaration_history h WHERE h.child_id = d.child_id);

INSERT INTO child_history (child_id, action, user_id, note, created_at)
SELECT ch.id, 'ENROLLED', d.declared_by, NULL, (ch.enrolled_on + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM children ch
JOIN child_health_declarations d ON d.child_id = ch.id
WHERE ch.code IN ('HS26311', 'HS26312', 'HS26313', 'HS26314', 'HS26301', 'HS26302')
  AND NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = ch.id AND h.action = 'ENROLLED');

INSERT INTO child_history (child_id, action, user_id, note, created_at)
SELECT ch.id, 'WITHDRAWN', d.declared_by, ch.left_reason, (ch.left_on + time '08:15') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM children ch
JOIN child_health_declarations d ON d.child_id = ch.id
WHERE ch.code = 'HS26314' AND ch.status = 'LEFT'
  AND NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = ch.id AND h.action = 'WITHDRAWN');
