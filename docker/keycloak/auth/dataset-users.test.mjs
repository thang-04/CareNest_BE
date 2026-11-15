import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { generate } from '../../../scripts/dev/gen-dev-dataset.mjs';
import { buildDataset } from '../../../scripts/dev/dataset/people.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const read = (path) => readFileSync(resolve(root, path), 'utf8').replace(/\r\n/g, '\n');

// File seed/user đã commit phải đúng là output của generator (không ai sửa tay lệch đi)
test('committed dataset files equal generator output', () => {
  for (const [path, content] of Object.entries(generate())) {
    assert.equal(read(path), content, `${path} lệch generator: chạy node scripts/dev/gen-dev-dataset.mjs`);
  }
});

test('every seeded account has a matching Keycloak user with the same id, username and role', () => {
  const sql = read('src/main/resources/db/dev/R__dev_01_people_structure.sql');
  const block = sql.slice(sql.indexOf('INSERT INTO user_accounts'), sql.indexOf('ON CONFLICT (keycloak_user_id)'));
  const seeded = [...block.matchAll(/\('([0-9a-f-]{36})', '(\d{10}|[a-z0-9]+)', (?:'[^']*'|NULL), '[^']*', '([A-Z_]+)'/g)]
    .map(([, id, username, role]) => [id, username, role]);
  const users = JSON.parse(read('docker/keycloak/dev/carenest-users-0.json')).users
    .map((u) => [u.id, u.username, u.realmRoles[0]]);
  assert.ok(seeded.length > 200);
  assert.deepEqual(seeded, users);
});

test('dataset covers every role combination and account state', () => {
  const d = buildDataset();
  const staffWith = (pred) => d.staff.filter(pred).length;
  const has = (s, role) => s.assignments.some((a) => a.role === role && !a.to);
  assert.equal(staffWith((s) => s.role === 'ADMIN'), 1);
  assert.equal(staffWith((s) => has(s, 'PRINCIPAL')), 1);
  assert.deepEqual(d.staff.filter((s) => has(s, 'VICE_PRINCIPAL')).map((s) => s.assignments[0].campus).sort(), ['CS-A', 'CS-B']);
  assert.equal(staffWith((s) => has(s, 'AGE_GROUP_TEAM_LEADER') && has(s, 'TEACHER')), 4);
  assert.ok(staffWith((s) => has(s, 'TEACHER') && !has(s, 'AGE_GROUP_TEAM_LEADER')) >= 16);
  assert.equal(staffWith((s) => s.role === 'TEACHER' && s.status === 'ACTIVE' && s.assignments.every((a) => a.to)), 1);
  assert.ok(d.staff.some((s) => s.assignments.some((a) => a.substitute && a.to)));
  assert.equal(d.children.filter((c) => c.status === 'PENDING_PLACEMENT').length, 6);
  assert.ok(d.parentAccounts.filter((p) => p.lockedBySystem && p.status === 'INACTIVE').length >= 3);
  assert.equal(staffWith((s) => s.status === 'INACTIVE' && s.assignments.every((a) => a.to)), 1);
  assert.equal(staffWith((s) => has(s, 'KITCHEN_STAFF')), 2);
  assert.ok(d.parentAccounts.some((p) => p.childCodes.length > 1));
  assert.ok(d.children.some((c) => c.recipientAccount && ['GRANDMOTHER', 'GRANDFATHER'].includes(c.guardians[0].relationship)));
  assert.ok(d.staff.some((s) => s.pendingPassword) && d.parentAccounts.some((p) => p.pendingPassword));
  assert.deepEqual([...new Set(d.permissionGrants.map((g) => g.effect))].sort(), ['GRANT', 'REVOKE']);
  // Mỗi lớp đúng 2 GV đang dạy, 1 chủ nhiệm
  for (const cls of d.classes) {
    const active = d.staff.flatMap((s) => s.assignments).filter((a) => a.role === 'TEACHER' && a.classKey === cls.key && !a.to);
    assert.equal(active.length, 2, cls.name);
    assert.equal(active.filter((a) => a.homeroom).length, 1, cls.name);
  }
});

test('phones and citizen ids are unique and well formed', () => {
  const d = buildDataset();
  const people = [...d.staff, ...d.children.flatMap((c) => c.guardians)];
  const phones = new Set(people.map((p) => p.phone));
  const ids = new Set([...people.map((p) => p.citizenId), ...d.children.map((c) => c.profile.idNumber)]);
  assert.equal(phones.size, new Set(d.staff.map((s) => s.phone)).size + new Set(d.children.flatMap((c) => c.guardians.map((g) => g.phone))).size);
  for (const p of phones) assert.match(p, /^0(3[2-9]|7[06-9]|8[1-689]|9[0134678])\d{7}$/);
  // 8 tài khoản cố định giữ tên đăng nhập cũ; còn lại tên đăng nhập = SĐT
  const usernames = [...d.staff, ...d.parentAccounts].map((a) => a.username);
  assert.deepEqual(usernames.filter((u) => !/^0\d{9}$/.test(u)).sort(),
    ['admin', 'kitchen', 'parent1', 'parent2', 'principal', 'teacher1', 'teacher2', 'viceprincipal']);
  for (const id of ids) assert.match(id, /^03[01][0-3]\d{8}$/);
});

test('seed and Keycloak files carry no demo placeholders', () => {
  const files = [
    ...readdirSync(resolve(root, 'src/main/resources/db/dev')).map((f) => `src/main/resources/db/dev/${f}`),
    'docker/keycloak/dev/carenest-users-0.json', 'docker/keycloak/vps/carenest-users-0.json',
  ];
  for (const f of files) {
    assert.doesNotMatch(read(f), /demo|DEV_SAMPLE|Cơ sở A|Lớp A1|@gmail\.com|@carenest\.test|'0900\d{6}'|'999\d{9}'/i, f);
  }
});
