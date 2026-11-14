// Cơ cấu trường + con người của bộ dữ liệu dev: điểm trường, phòng, lớp, nhân sự (đủ ma trận vai trò), trẻ, phụ huynh.
// Toàn bộ là dữ liệu giả nhìn như thật (tên, SĐT, CCCD đúng định dạng nhưng không thuộc về ai).
import { createRng } from './rng.mjs';
import {
  ADULT_GIVEN, ADULT_MIDDLE, CHILD_GIVEN, CHILD_MIDDLE, MOBILE_PREFIXES, PROVINCE, SURNAMES, VILLAGES, WARDS,
} from './names.mjs';

export const SCHOOL_YEAR = {
  name: '2026-2027', start: '2026-09-01', end: '2027-05-31', prep: '2026-08-15',
  semester1Start: '2026-09-01', semester1End: '2027-01-15', semester2Start: '2027-01-18', semester2End: '2027-05-31',
};
export const EMAIL_DOMAIN = 'thuonghong.carenest.edu.vn';

export const CAMPUSES = [
  { code: 'CS-A', name: 'Cơ sở 1 – Trường Mầm non Thượng Hồng', shortName: 'Cơ sở 1',
    address: 'Thôn Ngọc Lâm, xã Thượng Hồng, thành phố Hải Phòng', phone: '02253876125', isMain: true },
  { code: 'CS-B', name: 'Cơ sở 2 – Trường Mầm non Thượng Hồng', shortName: 'Cơ sở 2',
    address: 'Thôn Hòa Bình, xã Thượng Hồng, thành phố Hải Phòng', phone: '02253876348', isMain: false },
];

const ROOM_TEMPLATE = [
  ['P101', 'Phòng 101', 'CLASS', 25], ['P102', 'Phòng 102', 'CLASS', 25], ['P103', 'Phòng 103', 'CLASS', 30],
  ['P201', 'Phòng 201', 'CLASS', 35], ['P202', 'Phòng 202', 'CLASS', 35],
  ['BEP', 'Bếp ăn', 'KITCHEN', null], ['KHO', 'Kho thực phẩm khô', 'STORAGE', null],
  ['YTE', 'Phòng y tế', 'DEPARTMENT', null], ['AMN', 'Phòng âm nhạc', 'FUNCTION_ROOM', 40],
  ['P203', 'Phòng 203 (đang sửa chữa)', 'CLASS', 30, 'INACTIVE'],
];
export const ROOMS = CAMPUSES.flatMap((c) =>
  ROOM_TEMPLATE.map(([code, name, type, capacity, status = 'ACTIVE']) => ({ campus: c.code, code, name, type, capacity, status })));

// Mỗi cơ sở 5 lớp: nhà trẻ 1, mầm 1, chồi 1, lá 2 (quy mô 10 lớp user chốt); size = sĩ số đầu năm
export const CLASSES = [
  { key: 'NT1', campus: 'CS-A', name: 'Lớp Nhà trẻ 1', ageGroup: 'ag-2', room: 'P101', size: 19 },
  { key: 'M1', campus: 'CS-A', name: 'Lớp Mầm 1', ageGroup: 'ag-3', room: 'P102', size: 22 },
  { key: 'C1', campus: 'CS-A', name: 'Lớp Chồi 1', ageGroup: 'ag-4', room: 'P103', size: 22 },
  { key: 'L1', campus: 'CS-A', name: 'Lớp Lá 1', ageGroup: 'ag-5', room: 'P201', size: 22 },
  { key: 'L2', campus: 'CS-A', name: 'Lớp Lá 2', ageGroup: 'ag-5', room: 'P202', size: 22 },
  { key: 'NT2', campus: 'CS-B', name: 'Lớp Nhà trẻ 2', ageGroup: 'ag-2', room: 'P101', size: 19 },
  { key: 'M2', campus: 'CS-B', name: 'Lớp Mầm 2', ageGroup: 'ag-3', room: 'P102', size: 22 },
  { key: 'C2', campus: 'CS-B', name: 'Lớp Chồi 2', ageGroup: 'ag-4', room: 'P103', size: 21 },
  { key: 'L3', campus: 'CS-B', name: 'Lớp Lá 3', ageGroup: 'ag-5', room: 'P201', size: 21 },
  { key: 'L4', campus: 'CS-B', name: 'Lớp Lá 4', ageGroup: 'ag-5', room: 'P202', size: 21 },
];
const AGE_MONTHS = { 'ag-2': [24, 36], 'ag-3': [36, 48], 'ag-4': [48, 60], 'ag-5': [60, 72] };

// 8 id Keycloak cố định giữ từ bộ cũ để liên kết/test không gãy; còn lại id tất định theo nhóm
const fixedId = (n) => `00000000-0000-4000-a000-${String(n).padStart(12, '0')}`;
// 8 tài khoản cố định giữ tên đăng nhập quen thuộc của team (đăng nhập nhanh khi thử); còn lại đăng nhập bằng SĐT
const FIXED_USERNAMES = { 1: 'admin', 2: 'principal', 3: 'viceprincipal', 4: 'teacher1', 5: 'teacher2', 6: 'parent1',
  7: 'parent2', 8: 'kitchen' };
const fixedUsername = (id) => (id.startsWith('00000000-0000-4000-a000-') ? FIXED_USERNAMES[Number(id.slice(-12))] : null);
const staffId = (n) => `00000000-0000-4000-b000-${String(n).padStart(12, '0')}`;
const parentId = (n) => `00000000-0000-4000-e000-${String(n).padStart(12, '0')}`;

const ASCII = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();

export function buildDataset(seed = 20260901) {
  const rng = createRng(seed);
  const usedPhones = new Set();
  const usedCitizen = new Set();
  const usedEmails = new Set();

  const phone = () => {
    for (;;) {
      const p = rng.weighted(MOBILE_PREFIXES) + rng.digits(7);
      // Tránh 0900xxxxxx: trùng mẫu SĐT giả của bộ seed cũ, dễ nhầm là dữ liệu chưa thay
      if (!usedPhones.has(p) && !p.startsWith('0900')) { usedPhones.add(p); return p; }
    }
  };
  // CCCD: mã tỉnh (030 Hải Dương cũ / 031 Hải Phòng) + mã giới tính-thế kỷ + 2 số năm sinh + 6 số ngẫu nhiên
  const citizenId = (gender, birthYear) => {
    const century = birthYear >= 2000 ? 2 : 0;
    const genderDigit = century + (gender === 'FEMALE' ? 1 : 0);
    for (;;) {
      const id = rng.pick(['030', '030', '031']) + genderDigit + String(birthYear % 100).padStart(2, '0') + rng.digits(6);
      if (!usedCitizen.has(id)) { usedCitizen.add(id); return id; }
    }
  };
  const adultName = (gender, surname = rng.weighted(SURNAMES)) => {
    const middle = rng.weighted(ADULT_MIDDLE[gender]);
    const given = rng.pick(ADULT_GIVEN[gender]);
    return { fullName: `${surname} ${middle} ${given}`, surname, given };
  };
  const email = ({ surname, fullName, given }) => {
    const base = `${ASCII(given)}.${ASCII(fullName.slice(0, fullName.length - given.length)).replace(/\s+/g, '')}`;
    let candidate = base;
    for (let i = 2; usedEmails.has(candidate); i += 1) candidate = `${base}${i}`;
    usedEmails.add(candidate);
    return `${candidate}@${EMAIL_DOMAIN}`;
  };

  // ---- Nhân sự ----
  const staff = [];
  let staffSeq = 0;
  const addStaff = ({ id, role, gender = 'FEMALE', title, born, status = 'ACTIVE', pendingPassword = false }) => {
    const name = adultName(gender);
    const year = born ?? rng.int(1975, 1998);
    const p = phone();
    const person = {
      key: `S${staff.length + 1}`, keycloakId: id ?? staffId(++staffSeq), role, title, gender,
      fullName: name.fullName, username: fixedUsername(id ?? '') ?? p, phone: p, email: email(name), citizenId: citizenId(gender, year),
      status, pendingPassword, assignments: [],
    };
    staff.push(person);
    return person;
  };

  const admin = addStaff({ id: fixedId(1), role: 'ADMIN', gender: 'MALE', title: 'Quản trị hệ thống', born: 1992 });
  const principal = addStaff({ id: fixedId(2), role: 'PRINCIPAL', title: 'Hiệu trưởng', born: 1974 });
  const vpA = addStaff({ id: fixedId(3), role: 'VICE_PRINCIPAL', title: 'Phó hiệu trưởng phụ trách dinh dưỡng – Cơ sở 1', born: 1980 });
  const vpB = addStaff({ role: 'VICE_PRINCIPAL', title: 'Phó hiệu trưởng phụ trách chuyên môn – Cơ sở 2', born: 1982 });
  principal.assignments.push({ role: 'PRINCIPAL', from: SCHOOL_YEAR.prep });
  vpA.assignments.push({ role: 'VICE_PRINCIPAL', campus: 'CS-A', from: SCHOOL_YEAR.prep });
  vpB.assignments.push({ role: 'VICE_PRINCIPAL', campus: 'CS-B', from: SCHOOL_YEAR.prep });

  // Phân công hiệu lực từ ngày chuẩn bị năm học (soạn kế hoạch năm/tổ/học kỳ trước khai giảng)
  // Giáo viên: 2 GV/lớp (1 chủ nhiệm), cố định id a..4 = GV chủ nhiệm Chồi 1 kiêm tổ trưởng MG nhỡ, a..5 = GV Mầm 1
  const teacherFor = {};
  for (const cls of CLASSES) {
    const homeroomId = cls.key === 'C1' ? fixedId(4) : undefined;
    const secondId = cls.key === 'C1' ? fixedId(5) : undefined;
    const homeroom = addStaff({ id: homeroomId, role: 'TEACHER', title: `Giáo viên chủ nhiệm ${cls.name}` });
    const second = addStaff({ id: secondId, role: 'TEACHER', title: `Giáo viên ${cls.name}`,
      pendingPassword: cls.key === 'NT2' });
    homeroom.assignments.push({ role: 'TEACHER', classKey: cls.key, homeroom: true, from: SCHOOL_YEAR.prep });
    if (cls.key === 'L1') {
      // GV thứ hai Lá 1 thay từ 02/11 cho GV nghỉ việc (tài khoản đã khóa, phân công kết thúc 31/10)
      second.assignments.push({ role: 'TEACHER', classKey: cls.key, homeroom: false, from: '2026-11-02' });
    } else {
      second.assignments.push({ role: 'TEACHER', classKey: cls.key, homeroom: false, from: SCHOOL_YEAR.prep });
    }
    teacherFor[cls.key] = { homeroom, second };
  }
  const leftTeacher = addStaff({ role: 'TEACHER', title: 'Giáo viên (đã nghỉ việc)', status: 'INACTIVE' });
  leftTeacher.assignments.push({ role: 'TEACHER', classKey: 'L1', homeroom: false, from: SCHOOL_YEAR.prep, to: '2026-10-31' });
  const newTeacher = addStaff({ role: 'TEACHER', title: 'Giáo viên mới tuyển (chưa phân lớp)', born: 1999 });
  newTeacher.assignments.push({ role: 'TEACHER', classKey: 'M2', homeroom: false, from: '2026-10-12', to: '2026-10-16',
    substitute: true, replacedKey: 'M2' });

  // Tổ trưởng chuyên môn: 1 người/nhóm tuổi, là GV chủ nhiệm một lớp của nhóm đó
  const leaders = { 'ag-2': 'NT1', 'ag-3': 'M2', 'ag-4': 'C1', 'ag-5': 'L3' };
  for (const [ageGroup, classKey] of Object.entries(leaders)) {
    const t = teacherFor[classKey].homeroom;
    t.assignments.push({ role: 'AGE_GROUP_TEAM_LEADER', ageGroup, from: SCHOOL_YEAR.prep });
    t.title = `${t.title}, tổ trưởng chuyên môn`;
  }

  // Bếp: mỗi cơ sở 1 tài khoản dùng chung của tổ bếp (đứng tên bếp trưởng)
  const kitchenA = addStaff({ id: fixedId(8), role: 'KITCHEN_STAFF', title: 'Tổ bếp Cơ sở 1', born: 1978 });
  const kitchenB = addStaff({ role: 'KITCHEN_STAFF', title: 'Tổ bếp Cơ sở 2', born: 1981 });
  kitchenA.assignments.push({ role: 'KITCHEN_STAFF', campus: 'CS-A', from: SCHOOL_YEAR.prep });
  kitchenB.assignments.push({ role: 'KITCHEN_STAFF', campus: 'CS-B', from: SCHOOL_YEAR.prep });

  // Quyền ngoại lệ theo người: 1 GV được cấp thêm, 1 GV bị bớt so với mặc định vai trò
  const permissionGrants = [
    { user: teacherFor.L4.homeroom, permission: 'CHILD_PROFILE_EDIT', effect: 'GRANT', scope: 'CAMPUS',
      reason: 'Hỗ trợ Phó hiệu trưởng cập nhật hồ sơ trẻ Cơ sở 2 đầu năm học', grantedBy: principal },
    { user: teacherFor.C2.second, permission: 'ASSESSMENT_CONFIRM', effect: 'REVOKE', scope: null,
      reason: 'Giáo viên tập sự, giáo viên chủ nhiệm xác nhận đánh giá thay', grantedBy: principal },
    // AUTH-20: quyền bán trú mặc định tắt với PHT; HT cấp cho PHT dinh dưỡng (cả trường) và kho/lưu mẫu từng cơ sở
    { user: vpA, permission: 'NUTRITION_MANAGE', effect: 'GRANT', scope: 'SCHOOL',
      reason: 'Phó hiệu trưởng phụ trách dinh dưỡng quản lý danh mục bán trú toàn trường', grantedBy: principal },
    { user: vpA, permission: 'MENU_MANAGE', effect: 'GRANT', scope: 'SCHOOL',
      reason: 'Phó hiệu trưởng phụ trách dinh dưỡng lập và công bố thực đơn chung hai cơ sở', grantedBy: principal },
    ...[vpA, vpB].flatMap((vp) => [
      { user: vp, permission: 'STOCK_MANAGE', effect: 'GRANT', scope: 'CAMPUS',
        reason: 'Phó hiệu trưởng nhập kho khô tại cơ sở phụ trách', grantedBy: principal },
      { user: vp, permission: 'FOOD_SAFETY_APPROVE', effect: 'GRANT', scope: 'CAMPUS',
        reason: 'Phó hiệu trưởng duyệt lưu mẫu, hủy mẫu thức ăn tại cơ sở phụ trách', grantedBy: principal },
    ]),
  ];

  // ---- Trẻ + phụ huynh ----
  const children = [];
  let childSeq = 0;
  const childCode = () => `HS26${String(++childSeq).padStart(3, '0')}`;
  const dobFor = (ageGroup) => {
    const [from, to] = AGE_MONTHS[ageGroup];
    // Ngày sinh sao cho tuổi tại 01/09/2026 nằm trong [from, to) tháng
    const start = new Date(Date.UTC(2026, 8, 1));
    const maxDays = Math.floor(((to - from) * 30.4)) - 3;
    const d = new Date(start);
    d.setUTCMonth(d.getUTCMonth() - to);
    d.setUTCDate(d.getUTCDate() + 2 + rng.int(0, maxDays));
    return d.toISOString().slice(0, 10);
  };
  const childName = (gender, surname, taken) => {
    for (;;) {
      const middle = rng.pick(CHILD_MIDDLE[gender]);
      const given = rng.pick(CHILD_GIVEN[gender]);
      if (middle === given) continue;
      const full = `${surname} ${middle} ${given}`;
      if (!taken.has(full)) { taken.add(full); return full; }
    }
  };
  const makeParents = (childSurname) => {
    const fatherYear = rng.int(1984, 1997);
    const motherYear = Math.min(1999, fatherYear + rng.int(0, 4));
    const f = adultName('MALE', childSurname);
    const m = adultName('FEMALE');
    return {
      father: { relationship: 'FATHER', fullName: f.fullName, phone: phone(), citizenId: citizenId('MALE', fatherYear) },
      mother: { relationship: 'MOTHER', fullName: m.fullName, phone: phone(), citizenId: citizenId('FEMALE', motherYear) },
    };
  };
  const profile = () => ({
    idNumber: null, // gán sau khi có năm sinh
    premature: rng.chance(0.04),
    bloodType: rng.chance(0.45) ? rng.weighted([['O', 42], ['A', 21], ['B', 31], ['AB', 6]]) : null,
    ethnicity: rng.chance(0.97) ? 'Kinh' : rng.pick(['Tày', 'Mường']),
    beneficiary: rng.chance(0.03) ? 'Hộ cận nghèo' : 'Không',
    ward: rng.weighted(WARDS), street: `Số ${rng.int(1, 120)}, ${rng.pick(VILLAGES)}`, province: PROVINCE,
  });
  const health = () => {
    const allergic = rng.chance(0.065);
    const allergies = allergic
      ? [rng.weighted([['Tôm', 30], ['Sữa bò', 25], ['Trứng', 20], ['Đậu phộng', 15], ['Cua', 10]])]
      : [];
    if (allergic && rng.chance(0.25)) {
      const extra = rng.pick(['Cá biển', 'Mực', 'Đậu nành']);
      if (!allergies.includes(extra)) allergies.push(extra);
    }
    const selfHistory = rng.chance(0.05)
      ? rng.pick(['Viêm phế quản lúc 2 tuổi, đã khỏi', 'Hen phế quản nhẹ, có thuốc xịt dự phòng',
        'Sốt co giật lúc 18 tháng tuổi', 'Viêm da cơ địa'])
      : null;
    return {
      allergies,
      diet: allergies.includes('Sữa bò') ? 'Không dùng sữa bò, thay bằng sữa đậu nành hoặc sữa hạt' : null,
      selfHistory,
      familyHistory: rng.chance(0.03) ? rng.pick(['Bố bị hen phế quản', 'Mẹ bị dị ứng hải sản', 'Ông nội tiểu đường']) : null,
      otherNotesState: rng.chance(0.1) ? 'NOT_PROVIDED' : 'NONE_REPORTED',
      tbContactDate: null,
    };
  };

  for (const cls of CLASSES) {
    const taken = new Set();
    for (let i = 0; i < cls.size; i += 1) {
      const gender = rng.chance(0.5) ? 'MALE' : 'FEMALE';
      const surname = rng.weighted(SURNAMES);
      const dob = dobFor(cls.ageGroup);
      const parents = makeParents(surname);
      const motherFirst = rng.chance(0.68);
      const hasSecond = rng.chance(0.88);
      const guardians = motherFirst
        ? [parents.mother, ...(hasSecond ? [parents.father] : [])]
        : [parents.father, ...(hasSecond ? [parents.mother] : [])];
      const p = profile();
      p.idNumber = citizenId(gender, Number(dob.slice(0, 4)));
      children.push({
        code: childCode(), fullName: childName(gender, surname, taken), surname, gender, dob, status: 'ACTIVE',
        enrolledOn: SCHOOL_YEAR.start, classKey: cls.key, campus: cls.campus, enrollments: [{ classKey: cls.key, from: SCHOOL_YEAR.start }],
        guardians, recipientIndex: 0, profile: p, health: health(), taken,
      });
    }
  }

  // Ông/bà là người giám hộ nhận tài khoản (5 trẻ): ông/bà đứng đầu hồ sơ, bố mẹ phía sau
  const grandChildren = children.filter((c) => c.classKey !== 'NT1').filter((_, i) => i % 41 === 7).slice(0, 5);
  for (const c of grandChildren) {
    const gender = rng.chance(0.7) ? 'FEMALE' : 'MALE';
    const year = rng.int(1955, 1966);
    const name = adultName(gender, gender === 'MALE' ? c.surname : undefined);
    c.guardians = [{ relationship: gender === 'FEMALE' ? 'GRANDMOTHER' : 'GRANDFATHER', fullName: name.fullName,
      phone: phone(), citizenId: citizenId(gender, year) }, ...c.guardians].slice(0, 3);
  }

  // Anh chị em ruột (10 cặp, khác nhóm tuổi, có cặp khác cơ sở): dùng chung bố mẹ ⇒ 1 tài khoản phụ huynh 2 con
  const special = new Set(grandChildren);
  const take = (pred) => {
    const c = children.find((x) => !special.has(x) && pred(x));
    special.add(c);
    return c;
  };
  const ageOf = (c) => CLASSES.find((k) => k.key === c.classKey).ageGroup;
  // Cặp đầu cố định cho tài khoản a..7: một bé Lá 3 (Cơ sở 2) và một bé Mầm 1 (Cơ sở 1)
  const pairs = [[take((c) => c.classKey === 'L3'), take((c) => c.classKey === 'M1')]];
  const olderKeys = ['C1', 'L1', 'L2', 'C2', 'L4', 'L3', 'C1', 'L1', 'L4'];
  const youngerKeys = ['NT1', 'M1', 'NT2', 'M2', 'M2', 'NT2', 'M1', 'NT1', 'M2'];
  for (let i = 0; i < olderKeys.length; i += 1) {
    const big = take((c) => c.classKey === olderKeys[i] && rng.chance(0.3)) || take((c) => c.classKey === olderKeys[i]);
    const small = take((c) => c.classKey === youngerKeys[i] && ageOf(c) !== ageOf(big) && rng.chance(0.3))
      || take((c) => c.classKey === youngerKeys[i] && ageOf(c) !== ageOf(big));
    pairs.push([big, small]);
  }
  for (const [big, small] of pairs) {
    small.taken.delete(small.fullName);
    small.surname = big.surname;
    small.fullName = childName(small.gender, big.surname, small.taken);
    small.guardians = big.guardians;
    small.siblingOf = big.code;
  }

  // Trường hợp vòng đời hồ sơ: 1 trẻ chuyển lớp Chồi 1 → Chồi 2 (từ 09/11), 1 trẻ đã nghỉ học (05/10), 2 trẻ chờ xếp lớp
  const transfer = children.filter((c) => c.classKey === 'C1' && !special.has(c)).at(-1);
  special.add(transfer);
  transfer.enrollments = [{ classKey: 'C1', from: SCHOOL_YEAR.start, to: '2026-11-08' },
    { classKey: 'C2', from: '2026-11-09', reason: 'Gia đình chuyển nhà sang thôn Hòa Bình, gần Cơ sở 2' }];
  transfer.classKey = 'C2';
  transfer.campus = 'CS-B';
  transfer.transferAt = '2026-11-09';
  const leavers = [
    ['L2', '2026-10-05', 'Gia đình chuyển nơi ở, xin cho trẻ nghỉ học'],
    ['NT2', '2026-09-25', 'Gia đình chuyển vào Thành phố Hồ Chí Minh làm ăn'],
    ['M1', '2026-10-20', 'Gia đình cho trẻ học gần nơi làm việc của bố mẹ'],
  ].map(([classKey, leftOn, reason]) => {
    const c = children.filter((x) => x.classKey === classKey && !special.has(x)).at(-1);
    special.add(c);
    c.status = 'LEFT';
    c.leftOn = leftOn;
    c.leftReason = reason;
    // Như ChildService.withdraw: lớp kết thúc ngày trước ngày nghỉ học
    const lastDay = new Date(`${leftOn}T00:00:00Z`); lastDay.setUTCDate(lastDay.getUTCDate() - 1);
    c.enrollments = [{ classKey, from: SCHOOL_YEAR.start, to: lastDay.toISOString().slice(0, 10) }];
    c.lockedParent = true;
    return c;
  });
  const intakes = [['CS-A', 'ag-3', '2026-11-03'], ['CS-A', 'ag-2', '2026-11-10'], ['CS-A', 'ag-5', '2026-11-12'],
    ['CS-B', 'ag-2', '2026-11-05'], ['CS-B', 'ag-4', '2026-11-10'], ['CS-B', 'ag-3', '2026-11-13']];
  for (const [campus, ageGroup, enrolledOn] of intakes) {
    const gender = rng.chance(0.5) ? 'MALE' : 'FEMALE';
    const surname = rng.weighted(SURNAMES);
    const dob = dobFor(ageGroup);
    const parents = makeParents(surname);
    const p = profile();
    p.idNumber = citizenId(gender, Number(dob.slice(0, 4)));
    children.push({
      code: childCode(), fullName: childName(gender, surname, new Set()), surname, gender, dob,
      status: 'PENDING_PLACEMENT', intakeCampus: campus, enrolledOn, campus, enrollments: [],
      guardians: [parents.mother, parents.father], recipientIndex: 0, profile: p, health: health(), noAccount: true,
    });
  }
  // 1 trẻ khai có tiếp xúc người mắc lao (khai báo đầu năm)
  children.find((c) => c.classKey === 'L4').health.tbContactDate = '2026-08-12';

  // ---- Tài khoản phụ huynh: người nhận tài khoản của mỗi trẻ (trừ trẻ đã nghỉ, chờ xếp lớp) ----
  const parentAccounts = [];
  const accountByCitizen = new Map();
  let parentSeq = 0;
  const fixedParent = { a6: children.find((c) => c.classKey === 'C1' && !special.has(c)), a7: pairs[0][0] };
  for (const c of children) {
    if (c.noAccount) continue;
    const g = c.guardians[c.recipientIndex];
    let account = accountByCitizen.get(g.citizenId);
    if (!account) {
      let id;
      if (c === fixedParent.a6) id = fixedId(6);
      else if (c === fixedParent.a7 || c.siblingOf === fixedParent.a7.code) id = fixedId(7);
      else id = parentId(++parentSeq);
      account = { keycloakId: id, role: 'PARENT', username: fixedUsername(id) ?? g.phone, phone: g.phone, fullName: g.fullName,
        citizenId: g.citizenId, status: c.lockedParent ? 'INACTIVE' : 'ACTIVE', lockedBySystem: !!c.lockedParent,
        pendingPassword: false, childCodes: [] };
      accountByCitizen.set(g.citizenId, account);
      parentAccounts.push(account);
    }
    account.childCodes.push(c.code);
    c.recipientAccount = account;
  }
  // 1 phụ huynh chưa đổi mật khẩu lần đầu
  parentAccounts.find((a) => a.keycloakId.startsWith('00000000-0000-4000-e000') && a.childCodes.length === 1).pendingPassword = true;

  for (const c of children) delete c.taken;
  return { campuses: CAMPUSES, rooms: ROOMS, classes: CLASSES, staff, children, parentAccounts, permissionGrants,
    principal, admin };
}
