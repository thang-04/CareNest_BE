import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

test('opt-in overlay preserves the stock public Web origin when unset', async () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  const directory = resolve(root, 'target', `auth-config-${randomBytes(4).toString('hex')}`);
  await mkdir(directory, { recursive: true });
  const file = resolve(directory, 'test.env');
  const secret = randomBytes(32).toString('hex'); // carenest:allow-secret
  await writeFile(file, ['POSTGRES_PASSWORD', 'KEYCLOAK_DB_PASSWORD', 'KC_BOOTSTRAP_ADMIN_PASSWORD',
    'KEYCLOAK_ADMIN_CLIENT_SECRET', 'KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET', 'KEYCLOAK_DEV_USER_PASSWORD']
    .map((name) => `${name}=${secret}`).join('\n'));
  const env = { ...process.env };
  delete env.CARENEST_WEB_ORIGIN;
  const render = (overlay) => {
    const result = spawnSync('docker', ['compose', '--env-file', file, '-f', 'compose.yaml',
      ...(overlay ? ['-f', 'compose.auth.yaml'] : []), 'config', '--format', 'json'],
    { cwd: root, encoding: 'utf8', windowsHide: true, env });
    assert.equal(result.status, 0, 'Compose config must render without starting services');
    return JSON.parse(result.stdout).services.keycloak.environment.CARENEST_WEB_ORIGIN;
  };
  assert.equal(render(true), render(false));
});
