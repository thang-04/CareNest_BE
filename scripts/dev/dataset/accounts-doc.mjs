// Sinh docs/TAI_KHOAN_DEV.md: tài khoản đăng nhập của bộ dữ liệu dev, đại diện cho mọi vai trò và trạng thái.
import { DEV_PASSWORD } from './keycloak-users.mjs';

export function renderAccountsDoc(d) {
  const classByKey = Object.fromEntries(d.classes.map((c) => [c.key, c]));
  const childByCode = Object.fromEntries(d.children.map((c) => [c.code, c]));
  const scope = (s) => s.assignments
    .map((a) => {
      const period = a.to ? ` (tới ${a.to.split('-').reverse().join('/')})` : a.from !== '2026-08-15'
        ? ` (từ ${a.from.split('-').reverse().join('/')})` : '';
      if (a.role === 'PRINCIPAL') return 'Toàn trường';
      if (a.role === 'VICE_PRINCIPAL' || a.role === 'KITCHEN_STAFF') return a.campus === 'CS-A' ? 'Cơ sở 1' : 'Cơ sở 2';
      if (a.role === 'AGE_GROUP_TEAM_LEADER') return `Tổ trưởng ${a.ageGroup}`;
      return `${a.substitute ? 'Dạy thay ' : ''}${classByKey[a.classKey].name}${a.homeroom ? ' (chủ nhiệm)' : ''}${period}`;
    }).join('; ') || '—';
  const flags = (a) => [
    a.lockedBySystem ? 'hệ thống khóa (không còn con đang học)' : a.status === 'INACTIVE' ? 'đã khóa (nghỉ việc)' : null,
    a.pendingPassword ? 'phải đổi mật khẩu lần đầu' : null,
    ...d.permissionGrants.filter((g) => g.user === a)
      .map((g) => `${g.effect === 'GRANT' ? 'được cấp thêm' : 'bị bớt'} quyền ${g.permission}`),
  ].filter(Boolean).join('; ');

  const staffRows = d.staff.map((s) => `| ${s.fullName} | \`${s.username}\` | ${s.email} | ${s.role} | ${s.title} | ${scope(s)} | ${flags(s)} |`);
  const describeParent = (p) => p.childCodes.map((code) => {
    const c = childByCode[code];
    return `${c.fullName} – ${classByKey[c.classKey]?.name ?? 'chờ xếp lớp'}`;
  }).join('; ');
  const relation = (p) => {
    const c = childByCode[p.childCodes[0]];
    return c.guardians.find((g) => g.citizenId === p.citizenId)?.relationship;
  };
  const pick = (label, p) => (p ? `| ${label} | ${p.fullName} | \`${p.username}\` | ${describeParent(p)} | ${flags(p)} |` : null);
  const parents = d.parentAccounts;
  const parentRows = [
    pick('Phụ huynh 1 con (id cố định …0006)', parents.find((p) => p.keycloakId === '00000000-0000-4000-a000-000000000006')),
    pick('Phụ huynh 2 con khác cơ sở (id cố định …0007)', parents.find((p) => p.keycloakId === '00000000-0000-4000-a000-000000000007')),
    ...parents.filter((p) => p.childCodes.length > 1 && p.keycloakId !== '00000000-0000-4000-a000-000000000007').slice(0, 2)
      .map((p) => pick('Phụ huynh 2 con', p)),
    ...parents.filter((p) => ['GRANDMOTHER', 'GRANDFATHER'].includes(relation(p))).slice(0, 2)
      .map((p) => pick(relation(p) === 'GRANDMOTHER' ? 'Bà giám hộ' : 'Ông giám hộ', p)),
    pick('Phụ huynh chưa đổi mật khẩu lần đầu', parents.find((p) => p.pendingPassword)),
    pick('Phụ huynh bị hệ thống khóa (con đã nghỉ học)', parents.find((p) => p.lockedBySystem)),
  ].filter(Boolean);

  return `# Tài khoản bộ dữ liệu dev

> File sinh tự động bởi \`node scripts/dev/gen-dev-dataset.mjs\` — không sửa tay. Mọi tên, SĐT, CCCD là dữ liệu giả.

- Đăng nhập bằng **SĐT** (tên đăng nhập) hoặc email (nhân sự). Mật khẩu:
  - Local (\`compose.yaml\`, file \`docker/keycloak/dev/carenest-users-0.json\`): \`${DEV_PASSWORD}\`.
  - VPS dev/prod (\`docker/keycloak/vps/carenest-users-0.json\`): giá trị \`KEYCLOAK_DEV_USER_PASSWORD\` trong \`.env\` của VPS (không ghi ở repo).
- Keycloak chỉ import user khi realm còn mới: đổi bộ dữ liệu ⇒ tạo lại DB \`keycloak\` (local: \`docker compose down -v\`), xem \`docs/system/KEYCLOAK.md\` mục 5.
- Trường: Trường Mầm non Thượng Hồng, năm học 2026-2027, Cơ sở 1 (\`CS-A\`, điểm chính) và Cơ sở 2 (\`CS-B\`), 10 lớp, ${d.children.length} trẻ, ${d.parentAccounts.length} tài khoản phụ huynh.

## Nhân sự (${d.staff.length} tài khoản)

| Họ tên | Tên đăng nhập | Email | Realm role | Chức danh | Phạm vi | Trạng thái đặc biệt |
| --- | --- | --- | --- | --- | --- | --- |
${staffRows.join('\n')}

## Phụ huynh đại diện

Mọi trẻ đang học đều có 1 phụ huynh nhận tài khoản (tên đăng nhập = SĐT của người đó trên hồ sơ trẻ). Đại diện để thử:

| Trường hợp | Họ tên | Tên đăng nhập | Con | Trạng thái đặc biệt |
| --- | --- | --- | --- | --- |
${parentRows.join('\n')}

Danh sách đầy đủ: \`select u.username, u.full_name, c.full_name from user_accounts u join child_guardians g on g.user_id = u.id join children c on c.id = g.child_id order by 1;\`
`;
}
