import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Stack dev chạy cạnh production trên cùng VPS: sai cổng/DB/issuer là đụng production
async function renderDevCompose() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
  const directory = resolve(root, 'target', `dev-compose-${randomBytes(4).toString('hex')}`);
  await mkdir(directory, { recursive: true });
  const file = resolve(directory, 'test.env');
  const secret = randomBytes(32).toString('hex'); // carenest:allow-secret
  await writeFile(file, [
    'POSTGRES_PASSWORD', 'KEYCLOAK_DB_PASSWORD', 'KC_BOOTSTRAP_ADMIN_PASSWORD', 'KEYCLOAK_ADMIN_CLIENT_SECRET',
    'KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET', 'KEYCLOAK_AUTH_RECOVERY_ASSERTION_SECRET', 'CARENEST_AUTH_ENCRYPTION_KEY',
    'KEYCLOAK_DEV_USER_PASSWORD',
  ].map((name) => `${name}=${secret}`).concat(['API_IMAGE=example/api:1', 'KEYCLOAK_IMAGE=example/kc:1']).join('\n'));
  const result = spawnSync('docker', ['compose', '--env-file', file, '-f', 'compose.dev.yaml', 'config', '--format', 'json'],
    { cwd: root, encoding: 'utf8', windowsHide: true });
  assert.equal(result.status, 0, `Compose config must render: ${result.stderr}`);
  return JSON.parse(result.stdout);
}

test('dev stack only runs keycloak and api, on host network', async () => {
  const config = await renderDevCompose();
  assert.equal(config.name, 'carenest-be-dev');
  assert.deepEqual(Object.keys(config.services).sort(), ['api', 'keycloak']);
  for (const service of Object.values(config.services)) assert.equal(service.network_mode, 'host');
});

test('dev keycloak uses dev ports, dev database and dev hostnames', async () => {
  const env = (await renderDevCompose()).services.keycloak.environment;
  assert.equal(env.KC_HTTP_HOST, '127.0.0.1');
  assert.equal(env.KC_HTTP_PORT, '18322');
  assert.equal(env.KC_HTTP_MANAGEMENT_PORT, '18323');
  assert.equal(env.KC_DB_URL, 'jdbc:postgresql://127.0.0.1:18330/keycloak_dev');
  assert.equal(env.KC_DB_USERNAME, 'keycloak_dev');
  assert.equal(env.KC_HOSTNAME, 'https://auth-dev.carenest.edu.vn');
  assert.equal(env.CARENEST_WEB_ORIGIN, 'https://dev.carenest.edu.vn');
  assert.equal(env.CARENEST_AUTH_WEB_ORIGIN, 'https://dev.carenest.edu.vn');
});

test('dev api uses dev port, dev database and dev keycloak', async () => {
  const env = (await renderDevCompose()).services.api.environment;
  assert.equal(env.SERVER_ADDRESS, '127.0.0.1');
  assert.equal(env.SERVER_PORT, '18311');
  assert.equal(env.DB_URL, 'jdbc:postgresql://127.0.0.1:18330/carenest_dev');
  assert.equal(env.DB_USERNAME, 'carenest_dev');
  assert.equal(env.KEYCLOAK_ISSUER_URI, 'https://auth-dev.carenest.edu.vn/realms/carenest');
  assert.equal(env.KEYCLOAK_JWK_SET_URI, 'http://127.0.0.1:18322/realms/carenest/protocol/openid-connect/certs');
  assert.equal(env.KEYCLOAK_BASE_URL, 'http://127.0.0.1:18322');
  assert.equal(env.CARENEST_CORS_ALLOWED_ORIGINS, 'https://dev.carenest.edu.vn');
  assert.equal(env.CARENEST_AUTH_WEB_ORIGIN, 'https://dev.carenest.edu.vn');
});
