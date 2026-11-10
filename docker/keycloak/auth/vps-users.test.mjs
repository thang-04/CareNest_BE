import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const read = (path) => readFileSync(resolve(root, path), 'utf8');

// VPS ra Internet: user demo không được dùng hash mật khẩu công khai của máy dev (demo123)
test('VPS demo users take their password from KEYCLOAK_DEV_USER_PASSWORD', () => {
  const vps = JSON.parse(read('docker/keycloak/vps/carenest-users-0.json'));
  const dev = JSON.parse(read('docker/keycloak/dev/carenest-users-0.json'));
  assert.deepEqual(vps.users.map((u) => [u.id, u.username, u.realmRoles]),
    dev.users.map((u) => [u.id, u.username, u.realmRoles]), 'same users/ids as db/dev seed');
  for (const user of vps.users) {
    assert.deepEqual(user.credentials,
      [{ type: 'password', value: '${KEYCLOAK_DEV_USER_PASSWORD}', temporary: false }], user.username);
  }
  const compose = read('compose.vps.yaml');
  assert.match(compose, /docker\/keycloak\/vps\/carenest-users-0\.json:/);
  assert.doesNotMatch(compose, /docker\/keycloak\/dev\//);
});
