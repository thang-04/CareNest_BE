// Sinh R__dev_01_people_structure.sql từ buildDataset(): idempotent theo khóa tự nhiên để Flyway chạy lại an toàn.
import { SCHOOL_YEAR } from './people.mjs';

export const q = (v) => (v === null || v === undefined ? 'NULL' : typeof v === 'number' || typeof v === 'boolean'
  ? String(v) : `'${String(v).replace(/'/g, "''")}'`);
const rows = (list, fn) => list.map((x) => `    (${fn(x).map(q).join(', ')})`).join(',\n');
const YEAR = `(SELECT id FROM school_years WHERE name = ${q(SCHOOL_YEAR.name)})`;
const USER = (kc) => `(SELECT id FROM user_accounts WHERE keycloak_user_id = ${q(kc)})`;
// Người thao tác hồ sơ trẻ: Phó hiệu trưởng của cơ sở (CHILD_ENROLL theo campus)
const VP_OF = (campusExpr) => `(SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'VICE_PRINCIPAL'
        AND a.campus_id = ${campusExpr} ORDER BY a.id LIMIT 1)`;

export function renderPeopleStructure(d) {
  const classByKey = Object.fromEntries(d.classes.map((c) => [c.key, c]));
  const out = [];
  out.push(`-- GENERATED bởi scripts/dev/dataset/gen-dev-dataset.mjs — sửa generator rồi chạy lại, không sửa tay.
-- Bộ dữ liệu dev (profile "dev"): Trường Mầm non Thượng Hồng, 2 cơ sở, 10 lớp, ${d.staff.length} nhân sự, ${d.children.length} trẻ.
-- Dữ liệu giả nhìn như thật: tên, SĐT, CCCD đúng định dạng nhưng không thuộc về người thật.
-- keycloak_user_id khớp docker/keycloak/{dev,vps}/carenest-users-0.json (cùng generator sinh ra).
-- Vai trò tra theo phân công (staff_assignments), không theo username, để các file seed khác không phụ thuộc tên.
`);

  // ---- Năm học, điểm trường, phòng, lớp ----
  const y = SCHOOL_YEAR;
  out.push(`INSERT INTO school_years (name, start_date, end_date, prep_date, semester1_start, semester1_end, semester2_start, semester2_end)
VALUES (${[y.name, y.start, y.end, y.prep, y.semester1Start, y.semester1End, y.semester2Start, y.semester2End].map(q).join(', ')})
ON CONFLICT (name) DO NOTHING;

INSERT INTO campuses (code, name, short_name, address, phone) VALUES
${rows(d.campuses, (c) => [c.code, c.name, c.shortName, c.address, c.phone])}
ON CONFLICT (code) DO NOTHING;
UPDATE campuses SET is_main = TRUE WHERE code = ${q(d.campuses.find((c) => c.isMain).code)} AND NOT EXISTS (SELECT 1 FROM campuses WHERE is_main);

INSERT INTO rooms (campus_id, code, name, room_type, capacity, status)
SELECT c.id, v.code, v.name, v.room_type, v.capacity::smallint, v.status
FROM (VALUES
${rows(d.rooms, (r) => [r.campus, r.code, r.name, r.type, r.capacity, r.status])}
) AS v (campus_code, code, name, room_type, capacity, status)
JOIN campuses c ON c.code = v.campus_code
WHERE NOT EXISTS (SELECT 1 FROM rooms r WHERE r.campus_id = c.id AND LOWER(r.code) = LOWER(v.code) AND r.status <> 'DELETED');

INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity)
SELECT c.id, ${YEAR}, v.name, g.id, r.id, g.max_class_size
FROM (VALUES
${rows(d.classes, (k) => [k.campus, k.name, k.ageGroup, k.room])}
) AS v (campus_code, name, age_group_code, room_code)
JOIN campuses c ON c.code = v.campus_code
JOIN age_groups g ON g.code = v.age_group_code
JOIN rooms r ON r.campus_id = c.id AND r.code = v.room_code AND r.status = 'ACTIVE'
ON CONFLICT (school_year_id, campus_id, name) DO NOTHING;
`);

  // ---- Tài khoản ----
  const accounts = [
    ...d.staff.map((s) => [s.keycloakId, s.username, s.email, s.fullName, s.role, s.citizenId, s.status, false]),
    ...d.parentAccounts.map((p) => [p.keycloakId, p.username, null, p.fullName, 'PARENT', p.citizenId, p.status,
      p.lockedBySystem]),
  ];
  out.push(`INSERT INTO user_accounts (keycloak_user_id, username, email, full_name, primary_role, citizen_id, status,
                           locked_by_system) VALUES
${rows(accounts, (a) => a)}
ON CONFLICT (keycloak_user_id) DO NOTHING;
`);

  out.push(`-- Năm học đang chạy, do Hiệu trưởng kích hoạt trước ngày khai giảng (trừ khi DB đã có năm ACTIVE khác)
UPDATE school_years SET status = 'ACTIVE', activated_at = TIMESTAMPTZ '2026-08-20 09:00+07',
                        activated_by = ${USER(d.principal.keycloakId)}
WHERE name = ${q(SCHOOL_YEAR.name)} AND status = 'PLANNED'
  AND NOT EXISTS (SELECT 1 FROM school_years WHERE status = 'ACTIVE');
`);

  out.push(`-- Lịch năm học (ngày học = thứ 2–6 trừ các khoảng nghỉ này; các file seed vận hành dùng chung lịch này)
INSERT INTO school_holidays (school_year_id, start_date, end_date, name)
SELECT ${YEAR}, v.start_date::date, v.end_date::date, v.name
FROM (VALUES
    ('2026-09-01', '2026-09-02', 'Nghỉ lễ Quốc khánh'),
    ('2027-01-01', '2027-01-01', 'Nghỉ Tết Dương lịch'),
    ('2027-02-03', '2027-02-12', 'Nghỉ Tết Nguyên đán Đinh Mùi'),
    ('2027-04-16', '2027-04-16', 'Nghỉ Giỗ Tổ Hùng Vương'),
    ('2027-04-30', '2027-05-01', 'Nghỉ lễ 30/4 và 1/5')
) AS v (start_date, end_date, name)
WHERE NOT EXISTS (SELECT 1 FROM school_holidays h WHERE h.school_year_id = ${YEAR} AND h.start_date = v.start_date::date);

-- Giờ chốt: tuần đầu năm chốt 9 giờ cho phụ huynh quen, từ 14/09 chốt 8 giờ 30
INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from, note, created_by, created_at)
SELECT ${YEAR}, v.cutoff_time::time, v.effective_from::date, v.note, ${USER(d.principal.keycloakId)}, v.created_at::timestamptz
FROM (VALUES
    ('09:00', '2026-09-01', 'Tuần đầu năm học chốt điểm danh và báo ăn trước 9 giờ', '2026-08-25 09:00+07'),
    ('08:30', '2026-09-14', 'Chốt điểm danh và báo ăn trước 8 giờ 30', '2026-09-11 15:00+07')
) AS v (cutoff_time, effective_from, note, created_at)
WHERE NOT EXISTS (SELECT 1 FROM attendance_cutoffs c WHERE c.school_year_id = ${YEAR} AND c.effective_from = v.effective_from::date);
`);

  // ---- Phân công: Hiệu trưởng phân công cho mọi người ----
  const assignments = d.staff.flatMap((s) => s.assignments.map((a) => [
    s.keycloakId, a.role, a.campus ?? null, a.classKey ? classByKey[a.classKey].campus : null,
    a.classKey ? classByKey[a.classKey].name : null, a.ageGroup ?? null, a.homeroom ?? false, a.from, a.to ?? null,
    a.substitute ?? false,
    a.replacedKey ? d.staff.find((x) => x.assignments.some((y) => y.classKey === a.replacedKey && !y.homeroom && !y.substitute && !y.to)).keycloakId : null,
  ]));
  out.push(`INSERT INTO staff_assignments (user_id, staff_role, campus_id, classroom_id, age_group_id, school_year_id, is_homeroom,
                               valid_from, valid_to, assigned_by, is_substitute, replaced_user_id)
SELECT u.id, v.staff_role, c.id, cl.id, g.id, CASE WHEN v.age_group_code IS NULL THEN NULL ELSE ${YEAR} END, v.homeroom,
       v.valid_from::date, v.valid_to::date, ${USER(d.principal.keycloakId)}, v.substitute, ru.id
FROM (VALUES
${rows(assignments, (a) => a)}
) AS v (keycloak_user_id, staff_role, campus_code, class_campus_code, class_name, age_group_code, homeroom, valid_from, valid_to,
        substitute, replaced_keycloak_id)
JOIN user_accounts u ON u.keycloak_user_id = v.keycloak_user_id
LEFT JOIN user_accounts ru ON ru.keycloak_user_id = v.replaced_keycloak_id
LEFT JOIN campuses c ON c.code = v.campus_code
LEFT JOIN campuses cc ON cc.code = v.class_campus_code
LEFT JOIN classrooms cl ON cl.campus_id = cc.id AND cl.name = v.class_name AND cl.school_year_id = ${YEAR}
LEFT JOIN age_groups g ON g.code = v.age_group_code
WHERE NOT EXISTS (
    SELECT 1 FROM staff_assignments a
    WHERE a.user_id = u.id AND a.staff_role = v.staff_role AND a.valid_from = v.valid_from::date
      AND a.campus_id IS NOT DISTINCT FROM c.id AND a.classroom_id IS NOT DISTINCT FROM cl.id
      AND a.age_group_id IS NOT DISTINCT FROM g.id
);
`);

  // AUTH-15: cơ sở công tác GV = cơ sở lớp dạy chính gần nhất (như migration V168, seed chạy sau migration)
  out.push(`INSERT INTO teacher_work_campuses (user_id, campus_id)
SELECT DISTINCT ON (a.user_id) a.user_id, cl.campus_id
FROM staff_assignments a
JOIN classrooms cl ON cl.id = a.classroom_id
JOIN user_accounts u ON u.id = a.user_id
WHERE a.staff_role = 'TEACHER' AND NOT a.is_substitute AND u.primary_role = 'TEACHER'
ORDER BY a.user_id, a.valid_from DESC, a.id DESC
ON CONFLICT (user_id) DO NOTHING;
`);

  out.push(`INSERT INTO permission_grants (user_id, permission_code, effect, scope, reason, granted_by)
SELECT u.id, v.permission_code, v.effect, v.scope, v.reason, gb.id
FROM (VALUES
${rows(d.permissionGrants, (g) => [g.user.keycloakId, g.permission, g.effect, g.scope, g.reason, g.grantedBy.keycloakId])}
) AS v (keycloak_user_id, permission_code, effect, scope, reason, granted_by)
JOIN user_accounts u ON u.keycloak_user_id = v.keycloak_user_id
JOIN user_accounts gb ON gb.keycloak_user_id = v.granted_by
WHERE NOT EXISTS (SELECT 1 FROM permission_grants p WHERE p.user_id = u.id AND p.permission_code = v.permission_code);

-- AUTH-20: chỉ mã sửa theo mảng mới có phạm vi cả trường; sửa dòng seed cũ đã ghi SCHOOL cho mã khác
UPDATE permission_grants SET scope = 'CAMPUS'
WHERE effect = 'GRANT' AND scope = 'SCHOOL' AND permission_code IN ('NUTRITION_MANAGE', 'MENU_MANAGE');
UPDATE permission_change_log SET after_scope = 'CAMPUS'
WHERE after_state = 'GRANT' AND after_scope = 'SCHOOL' AND permission_code IN ('NUTRITION_MANAGE', 'MENU_MANAGE');

-- Lịch sử đổi quyền tương ứng (màn lịch sử phân quyền): trước đó kế thừa vai trò
INSERT INTO permission_change_log (batch_id, actor_user_id, target_user_id, permission_code, before_state, after_state,
                                   after_scope, reason, changed_at)
SELECT md5('perm:' || p.user_id || ':' || p.permission_code)::uuid, p.granted_by, p.user_id, p.permission_code, 'INHERIT',
       p.effect, p.scope, p.reason, TIMESTAMPTZ '2026-09-10 14:00+07'
FROM permission_grants p
WHERE p.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM permission_change_log l WHERE l.target_user_id = p.user_id AND l.permission_code = p.permission_code);
`);

  // ---- Trẻ ----
  out.push(`INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on, left_on, left_reason)
SELECT v.code, v.full_name, v.dob::date, v.gender, v.status, c.id, v.enrolled_on::date, v.left_on::date, v.left_reason
FROM (VALUES
${rows(d.children, (c) => [c.code, c.fullName, c.dob, c.gender, c.status, c.intakeCampus ?? null, c.enrolledOn,
    c.leftOn ?? null, c.leftReason ?? null])}
) AS v (code, full_name, dob, gender, status, intake_campus, enrolled_on, left_on, left_reason)
LEFT JOIN campuses c ON c.code = v.intake_campus
ON CONFLICT (code) DO NOTHING;

INSERT INTO child_code_sequences (code_prefix, last_number)
SELECT left(code, 4), max(substring(code FROM 5)::integer) FROM children GROUP BY left(code, 4)
ON CONFLICT (code_prefix) DO UPDATE SET last_number = GREATEST(child_code_sequences.last_number, EXCLUDED.last_number);
`);

  const enrollments = d.children.flatMap((c) => c.enrollments.map((e) => [
    c.code, classByKey[e.classKey].campus, classByKey[e.classKey].name, e.from, e.to ?? null, e.reason ?? null]));
  out.push(`INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to, reason, created_by)
SELECT ch.id, cl.id, v.valid_from::date, v.valid_to::date, v.reason, ${VP_OF('cl.campus_id')}
FROM (VALUES
${rows(enrollments, (e) => e)}
) AS v (code, campus_code, class_name, valid_from, valid_to, reason)
JOIN children ch ON ch.code = v.code
JOIN campuses c ON c.code = v.campus_code
JOIN classrooms cl ON cl.campus_id = c.id AND cl.name = v.class_name AND cl.school_year_id = ${YEAR}
WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.valid_from = v.valid_from::date);
`);

  const transfers = d.children.filter((c) => c.transferAt);
  out.push(`-- Chuyển lớp giữa năm (lịch sử xếp lớp)
INSERT INTO child_placements (child_id, from_classroom_id, to_classroom_id, placed_by, reason, placed_at)
SELECT ch.id, fe.classroom_id, te.classroom_id, ${VP_OF('tcl.campus_id')}, te.reason, (te.valid_from + time '07:45') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM children ch
JOIN enrollments te ON te.child_id = ch.id AND te.valid_to IS NULL
JOIN classrooms tcl ON tcl.id = te.classroom_id
JOIN enrollments fe ON fe.child_id = ch.id AND fe.valid_to = te.valid_from - 1
WHERE ch.code IN (${transfers.map((c) => q(c.code)).join(', ')})
  AND NOT EXISTS (SELECT 1 FROM child_placements p WHERE p.child_id = ch.id);
`);

  // ---- Phụ huynh trên hồ sơ + liên kết tài khoản ----
  const guardians = d.children.flatMap((c) => c.guardians.map((g, i) => [
    c.code, i, g.fullName, g.relationship, g.phone, g.citizenId, i === c.recipientIndex,
    i === c.recipientIndex && c.recipientAccount ? c.recipientAccount.keycloakId : null]));
  out.push(`INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id, account_recipient, user_id, linked_at)
SELECT ch.id, v.position::smallint, v.full_name, v.relationship, v.phone, v.citizen_id, v.recipient, u.id,
       CASE WHEN u.id IS NULL THEN NULL ELSE (ch.enrolled_on + time '19:30') AT TIME ZONE 'Asia/Ho_Chi_Minh' END
FROM (VALUES
${rows(guardians, (g) => g)}
) AS v (code, position, full_name, relationship, phone, citizen_id, recipient, keycloak_user_id)
JOIN children ch ON ch.code = v.code
LEFT JOIN user_accounts u ON u.keycloak_user_id = v.keycloak_user_id
WHERE NOT EXISTS (SELECT 1 FROM child_guardians g WHERE g.child_id = ch.id AND g.position = v.position::smallint);
`);

  out.push(`INSERT INTO child_profiles (child_id, id_number, premature, gestation_weeks, ethnicity, beneficiary, blood_type,
                            address_province, address_ward, address_street)
SELECT ch.id, v.id_number, v.premature, v.gestation_weeks::smallint, v.ethnicity, v.beneficiary, v.blood_type,
       v.province, v.ward, v.street
FROM (VALUES
${rows(d.children, (c) => [c.code, c.profile.idNumber, c.profile.premature ? 'YES' : 'NO',
    c.profile.premature ? 35 : null, c.profile.ethnicity, c.profile.beneficiary, c.profile.bloodType,
    c.profile.province, c.profile.ward, c.profile.street])}
) AS v (code, id_number, premature, gestation_weeks, ethnicity, beneficiary, blood_type, province, ward, street)
JOIN children ch ON ch.code = v.code
ON CONFLICT (child_id) DO NOTHING;
`);

  // ---- Khai báo sức khỏe khi tiếp nhận ----
  const st = (v) => (v ? 'REPORTED' : 'NONE_REPORTED');
  out.push(`INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, diet_text, other_notes_state,
                                       self_history_state, self_history_text, family_history_state, family_history_text,
                                       tb_contact_state, tb_contact_date, declared_by, declared_at)
SELECT ch.id, v.allergies_state, v.diet_state, v.diet_text, v.other_state, v.self_state, v.self_text, v.family_state,
       v.family_text, v.tb_state, v.tb_date::date, ${VP_OF('camp.id')}, (ch.enrolled_on + time '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM (VALUES
${rows(d.children, (c) => [c.code, c.campus, st(c.health.allergies.length), st(c.health.diet), c.health.diet,
    c.health.otherNotesState, st(c.health.selfHistory), c.health.selfHistory, st(c.health.familyHistory),
    c.health.familyHistory, st(c.health.tbContactDate), c.health.tbContactDate])}
) AS v (code, campus_code, allergies_state, diet_state, diet_text, other_state, self_state, self_text, family_state,
        family_text, tb_state, tb_date)
JOIN children ch ON ch.code = v.code
JOIN campuses camp ON camp.code = v.campus_code
ON CONFLICT (child_id) DO NOTHING;

INSERT INTO child_allergies (child_id, name)
SELECT ch.id, v.name
FROM (VALUES
${rows(d.children.flatMap((c) => c.health.allergies.map((a) => [c.code, a])), (a) => a)}
) AS v (code, name)
JOIN children ch ON ch.code = v.code
WHERE NOT EXISTS (SELECT 1 FROM child_allergies a WHERE a.child_id = ch.id AND a.name = v.name);

INSERT INTO child_health_declaration_history (child_id, action, user_id, created_at)
SELECT d.child_id, 'DECLARED', d.declared_by, d.declared_at
FROM child_health_declarations d
WHERE NOT EXISTS (SELECT 1 FROM child_health_declaration_history h WHERE h.child_id = d.child_id);

-- Lịch sử hồ sơ: nhập học (Phó hiệu trưởng cơ sở) và nghỉ học
INSERT INTO child_history (child_id, action, user_id, note, created_at)
SELECT ch.id, 'ENROLLED', d.declared_by, NULL, (ch.enrolled_on + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM children ch
JOIN child_health_declarations d ON d.child_id = ch.id
WHERE NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = ch.id AND h.action = 'ENROLLED');

INSERT INTO child_history (child_id, action, user_id, note, created_at)
SELECT ch.id, 'WITHDRAWN', d.declared_by, ch.left_reason, (ch.left_on + time '08:15') AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM children ch
JOIN child_health_declarations d ON d.child_id = ch.id
WHERE ch.status = 'LEFT'
  AND NOT EXISTS (SELECT 1 FROM child_history h WHERE h.child_id = ch.id AND h.action = 'WITHDRAWN');
`);
  return out.join('\n');
}
