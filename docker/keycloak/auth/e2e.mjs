import { spawnSync } from 'node:child_process';
import { randomBytes, createHmac, createPublicKey, verify } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createAuthRealm } from './prepare.mjs';
import { adminLogin, configureGateway } from './bootstrap.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const project = `carenest-auth-e2e-${randomBytes(4).toString('hex')}`;
const directory = resolve(root, 'target', project);
const composeFile = resolve(directory, 'compose.json');
const password = () => `Test-${randomBytes(18).toString('hex')}`;
const adminPassword = password(); // carenest:allow-secret
const adminSecret = password(); // carenest:allow-secret
const gatewaySecret = password(); // carenest:allow-secret
const databasePassword = password(); // carenest:allow-secret
const assertionSecret = randomBytes(32); // carenest:allow-secret
let passed = 0;
let started = false;

function docker(args) {
  const result = spawnSync('docker', args, { cwd: root, encoding: 'utf8', windowsHide: true, timeout: 180000 });
  if (result.status !== 0) throw new Error(`Docker ${args[0]} failed; inspect this test project's container logs`);
  return result.stdout.trim();
}
const compose = (args) => docker(['compose', '-p', project, '-f', composeFile, ...args]);
const expect = (condition, label) => { if (!condition) throw new Error(label); };
const pause = (milliseconds) => new Promise((resolvePause) => setTimeout(resolvePause, milliseconds));
const success = (name) => { passed += 1; console.log(`PASS ${passed}: ${name}`); };

async function response(url, options = {}) {
  const result = await fetch(url, { ...options, signal: AbortSignal.timeout(10000) });
  const text = await result.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  return { status: result.status, data, text };
}

try {
  docker(['version', '--format', '{{.Server.Version}}']);
  docker(['image', 'inspect', 'carenest-keycloak-auth:26.8.0', '--format', '{{.Id}}']);
  await mkdir(directory, { recursive: true });
  const realm = createAuthRealm(JSON.parse(await readFile(resolve(root, 'docker/keycloak/import/carenest-realm.json'), 'utf8')));
  const realmFile = resolve(directory, 'carenest-realm.json');
  await writeFile(realmFile, JSON.stringify(realm));
  const definition = { services: {
    postgres: { image: 'postgres:17-alpine', environment: {
      POSTGRES_DB: 'keycloak', POSTGRES_USER: 'keycloak', POSTGRES_PASSWORD: databasePassword, // carenest:allow-secret
    }, tmpfs: ['/var/lib/postgresql/data'], healthcheck: {
      test: ['CMD-SHELL', 'pg_isready -U keycloak -d keycloak'], interval: '2s', timeout: '2s', retries: 30,
    } },
    mailpit: { image: 'axllent/mailpit:v1.31.4', ports: ['127.0.0.1::8025'] },
    keycloak: { image: 'carenest-keycloak-auth:26.8.0', command: ['start-dev', '--import-realm'],
      depends_on: { postgres: { condition: 'service_healthy' }, mailpit: { condition: 'service_started' } },
      ports: ['127.0.0.1::8080'], volumes: [`${realmFile.replaceAll('\\', '/')}:/opt/keycloak/data/import/carenest-realm.json:ro`],
      environment: { KC_DB: 'postgres', KC_DB_URL: 'jdbc:postgresql://postgres:5432/keycloak',
        KC_DB_USERNAME: 'keycloak', KC_DB_PASSWORD: databasePassword, KC_HOSTNAME_STRICT: 'false', // carenest:allow-secret
        KC_BOOTSTRAP_ADMIN_USERNAME: 'test-admin', KC_BOOTSTRAP_ADMIN_PASSWORD: adminPassword, // carenest:allow-secret
        KEYCLOAK_ADMIN_CLIENT_SECRET: adminSecret, KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET: gatewaySecret, // carenest:allow-secret
        KEYCLOAK_AUTH_RECOVERY_ASSERTION_SECRET: assertionSecret.toString('base64'), // carenest:allow-secret
        CARENEST_DEV_CLIENT_ENABLED: 'false', CARENEST_SMTP_HOST: 'mailpit', CARENEST_SMTP_PORT: '1025',
        CARENEST_WEB_ORIGIN: 'http://localhost:3000', CARENEST_AUTH_WEB_ORIGIN: 'http://localhost:5173', CARENEST_MOBILE_REDIRECT_URI: 'carenest://oauth/callback',
      },
    },
  } };
  await writeFile(composeFile, JSON.stringify(definition));
  console.log(`Isolated test project: ${project}`);
  started = true;
  compose(['up', '-d', '--wait', '--wait-timeout', '120']);
  const baseUrl = `http://${compose(['port', 'keycloak', '8080'])}`;
  const mailUrl = `http://${compose(['port', 'mailpit', '8025'])}`;
  let ready = false;
  for (let attempt = 0; attempt < 100; attempt += 1) {
    try { if ((await response(`${baseUrl}/realms/carenest/.well-known/openid-configuration`)).status === 200) { ready = true; break; } } catch {}
    await pause(1000);
  }
  expect(ready, 'Keycloak did not become ready');
  const adminToken = await adminLogin(baseUrl, 'test-admin', adminPassword);
  const admin = async (path, method = 'GET', body) => {
    const result = await response(`${baseUrl}/admin/realms/carenest${path}`, { method, headers: {
      Authorization: `Bearer ${adminToken}`, ...(body ? { 'Content-Type': 'application/json' } : {}),
    }, ...(body ? { body: JSON.stringify(body) } : {}) });
    expect(result.status >= 200 && result.status < 300, `Admin ${method} ${path} failed (${result.status})`);
    return result.data;
  };
  const token = (username, value, clientId = 'carenest-auth-gateway') => response(`${baseUrl}/realms/carenest/protocol/openid-connect/token`, {
    method: 'POST', body: new URLSearchParams({ grant_type: 'password', client_id: clientId,
      ...(clientId === 'carenest-auth-gateway' ? { client_secret: gatewaySecret } : {}), username, password: value }), // carenest:allow-secret
  });
  const createUser = async (name, temporary = false, actions = []) => {
    const value = password();
    await admin('/users', 'POST', { username: name, enabled: true, firstName: 'Demo', lastName: 'Test',
      email: `${name}@example.test`, requiredActions: actions,
      credentials: [{ type: 'password', value, temporary }],
    });
    const [user] = await admin(`/users?username=${encodeURIComponent(name)}&exact=true`);
    const role = await admin('/roles/TEACHER');
    await admin(`/users/${user.id}/role-mappings/realm`, 'POST', [role]);
    return { id: user.id, username: name, password: value };
  };

  const regular = await createUser('auth-e2e-regular');
  const before = await token(regular.username, regular.password);
  expect(before.status !== 200 && !before.data?.access_token, 'Gateway must start disabled');
  success('gateway is disabled by default');
  const flowCount = (await admin('/authentication/flows')).length;
  await configureGateway({ baseUrl, adminToken, gatewaySecret });
  expect((await admin('/authentication/flows')).length === flowCount, 'Preview must not mutate flows');
  success('bootstrap preview is read-only');
  await configureGateway({ baseUrl, adminToken, gatewaySecret, apply: true, enable: true });
  await configureGateway({ baseUrl, adminToken, gatewaySecret, apply: true, enable: true });
  success('bootstrap binds verified native flow and is idempotent');
  const executions = await admin('/authentication/flows/carenest-web-direct-grant/executions');
  const otpParent = executions.find((entry) => entry.authenticationFlow && entry.level === 0);
  await admin('/authentication/flows/carenest-web-direct-grant/executions', 'PUT', { ...otpParent, requirement: 'DISABLED' });
  let rejectedUnsafeFlow = false;
  try { await configureGateway({ baseUrl, adminToken, gatewaySecret, apply: true, enable: true }); }
  catch (error) { rejectedUnsafeFlow = error.message.includes('Unsafe gateway flow'); }
  const [disabledByBootstrap] = await admin('/clients?clientId=carenest-auth-gateway');
  expect(rejectedUnsafeFlow && !disabledByBootstrap.enabled, 'Bootstrap enabled a flow with disabled OTP ancestry');
  await admin('/authentication/flows/carenest-web-direct-grant/executions', 'PUT', { ...otpParent, requirement: 'CONDITIONAL' });
  await configureGateway({ baseUrl, adminToken, gatewaySecret, apply: true, enable: true });
  success('tampered OTP ancestry is rejected and gateway stays disabled');

  const login = await token(regular.username, regular.password);
  expect(login.status === 200 && login.data?.access_token && login.data?.refresh_token, 'Complete account login failed');
  const [header, payload, signature] = login.data.access_token.split('.');
  const claims = JSON.parse(Buffer.from(payload, 'base64url'));
  const keys = (await response(`${baseUrl}/realms/carenest/protocol/openid-connect/certs`)).data.keys;
  const kid = JSON.parse(Buffer.from(header, 'base64url')).kid;
  const key = createPublicKey({ key: keys.find((entry) => entry.kid === kid), format: 'jwk' });
  expect(verify('RSA-SHA256', Buffer.from(`${header}.${payload}`), key, Buffer.from(signature, 'base64url')), 'JWT signature mismatch');
  expect(claims.iss === `${baseUrl}/realms/carenest` && (Array.isArray(claims.aud) ? claims.aud : [claims.aud]).includes('carenest-api')
    && claims.realm_access.roles.includes('TEACHER') && claims.exp - claims.iat === 300, 'JWT issuer/audience/role/TTL mismatch');
  success('regular login issues correctly signed Keycloak JWT with existing audience/role/TTL');
  const wrong = await token(regular.username, password());
  expect(wrong.status === 400 && wrong.data?.error === 'invalid_grant' && !wrong.data?.setup_token, 'Wrong password bypassed native validator');
  success('wrong password never issues setup capability');

  const service = await response(`${baseUrl}/realms/carenest/protocol/openid-connect/token`, {
    method: 'POST', body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'carenest-backend', client_secret: adminSecret }), // carenest:allow-secret
  });
  expect(service.status === 200, 'Backend service account login failed');
  const privileged = service.data.access_token;
  const operation = (path, body, bearer = privileged) => response(`${baseUrl}/realms/carenest/carenest-auth/${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}) },
    body: JSON.stringify(body),
  });
  const first = await createUser('auth-e2e-first', true, ['UPDATE_PASSWORD']);
  const challenge = await token(first.username, first.password);
  expect(challenge.status === 400 && challenge.data?.error === 'carenest_password_change_required'
    && challenge.data?.setup_token && !challenge.data?.access_token && !challenge.data?.refresh_token, 'Temporary password issued unrestricted credentials');
  success('temporary password yields only a verified setup capability');
  const next = password();
  const setup = { token: challenge.data.setup_token, newPassword: next, confirmPassword: next };
  expect((await operation('password/setup', setup, null)).status === 401, 'Anonymous setup must be denied');
  expect((await operation('password/setup', setup, login.data.access_token)).status === 403, 'Normal user cannot invoke privileged operations');
  success('anonymous and ordinary user tokens cannot call privileged extension');
  expect((await operation('password/setup', { userId: first.id, newPassword: next, confirmPassword: next })).status === 400,
    'A userId without a capability must never reset a password');
  success('userId-only password mutation is rejected');
  expect((await operation('password/setup', { ...setup, newPassword: 'short', confirmPassword: 'short' })).status === 400, 'Policy violation must fail');
  expect((await operation('password/setup', { ...setup, confirmPassword: password() })).status === 400, 'Mismatched confirmation must fail'); // carenest:allow-secret
  expect((await operation('password/setup', setup)).status === 200, 'Valid setup failed after rejected inputs');
  expect((await operation('password/setup', setup)).status === 400, 'Setup capability replay succeeded');
  expect((await token(first.username, next)).status === 200, 'New password did not work');
  expect((await token(first.username, first.password)).status === 400, 'Old temporary password still works');
  success('policy/confirmation failure preserves valid proof; success consumes it and replaces temporary password');

  const disabled = await createUser('auth-e2e-disabled', true, ['UPDATE_PASSWORD']);
  const disabledProof = (await token(disabled.username, disabled.password)).data.setup_token;
  await admin(`/users/${disabled.id}`, 'PUT', { enabled: false });
  expect((await operation('password/setup', { ...setup, token: disabledProof })).status === 400, 'Disabled account redeemed proof');
  success('disabling an account invalidates setup authorization');
  const changed = await createUser('auth-e2e-changed', true, ['UPDATE_PASSWORD']);
  const changedProof = (await token(changed.username, changed.password)).data.setup_token;
  await admin(`/users/${changed.id}/reset-password`, 'PUT', { type: 'password', value: password(), temporary: true });
  expect((await operation('password/setup', { ...setup, token: changedProof })).status === 400, 'Proof survived native credential replacement');
  success('native credential replacement invalidates an outstanding capability');
  const unrelated = await createUser('auth-e2e-actions', true, ['UPDATE_PASSWORD', 'VERIFY_EMAIL']);
  const pending = await token(unrelated.username, unrelated.password);
  expect(pending.status === 400 && !pending.data?.setup_token && !pending.data?.access_token, 'Unrelated required action was bypassed');
  success('unrelated required actions are not bypassed');

  const assertion = (userId, purpose = 'RECOVERY_VERIFIED', overrides = {}) => {
    const now = Math.floor(Date.now() / 1000);
    const claims = { userId, realm: 'carenest', purpose, audience: 'carenest-password-mutation',
      issuer: 'carenest-backend', issuedAt: now, expiresAt: now + 60, nonce: randomBytes(32).toString('base64url'), ...overrides };
    const signed = 'v1.' + Buffer.from(JSON.stringify(claims)).toString('base64url');
    return signed + '.' + createHmac('sha256', assertionSecret).update(signed).digest('base64url');
  };
  const recovered = await createUser('auth-e2e-recovery');
  expect((await admin('/users/' + recovered.id)).requiredActions.length === 0, 'Recovery fixture must have no required action');
  const recoveryProof = assertion(recovered.id);
  expect((await operation('password/capability', { assertion: recoveryProof }, null)).status === 401,
    'Anonymous assertion exchange must be denied');
  expect((await operation('password/capability', { assertion: recoveryProof }, login.data.access_token)).status === 403,
    'Ordinary user assertion exchange must be denied');
  expect((await operation('password/capability', { userId: recovered.id })).status === 400,
    'UserId-only assertion exchange must be denied');
  success('assertion exchange requires backend service account and signed proof');
  const now = Math.floor(Date.now() / 1000);
  const invalidProofs = [
    recoveryProof.substring(0, recoveryProof.lastIndexOf('.') + 1) + 'AA',
    assertion(recovered.id, 'RECOVERY_VERIFIED', { realm: 'other' }),
    assertion(recovered.id, 'RECOVERY_VERIFIED', { issuer: 'other' }),
    assertion(recovered.id, 'RECOVERY_VERIFIED', { audience: 'other' }),
    assertion(recovered.id, 'ADMIN_RESET'),
    assertion(recovered.id, 'RECOVERY_VERIFIED', { issuedAt: now - 61, expiresAt: now - 1 }),
    assertion(recovered.id, 'RECOVERY_VERIFIED', { issuedAt: now, expiresAt: now + 120 }),
    assertion(recovered.id, 'RECOVERY_VERIFIED', { issuedAt: now + 30, expiresAt: now + 60 }),
    assertion('../invalid-subject'),
  ];
  for (const proof of invalidProofs) {
    const rejected = await operation('password/capability', { assertion: proof });
    expect(rejected.status === 400 && !rejected.data?.token, 'Invalid signed assertion produced capability');
  }
  success('tampering, expired/future/overlong assertions and incorrect bindings are rejected');
  const exchanged = await operation('password/capability', { assertion: recoveryProof });
  expect(exchanged.status === 200 && exchanged.data?.token && exchanged.data.expiresIn === 600,
    'Verified recovery assertion exchange failed');
  expect((await operation('password/capability', { assertion: recoveryProof })).status === 400,
    'Signed assertion replay issued another capability');
  expect((await token(recovered.username, recovered.password)).status === 200, 'Exchange changed password before capability redemption');
  const recoveredPassword = password(); // carenest:allow-secret
  const recoverySetup = { token: exchanged.data.token, newPassword: recoveredPassword, confirmPassword: recoveredPassword }; // carenest:allow-secret
  expect((await operation('password/setup', recoverySetup)).status === 200, 'Recovery capability did not change regular account password');
  expect((await operation('password/setup', recoverySetup)).status === 400, 'Recovery capability replay succeeded');
  expect((await token(recovered.username, recoveredPassword)).status === 200, 'Recovered password does not authenticate');
  expect((await token(recovered.username, recovered.password)).status === 400, 'Pre-recovery password still authenticates');
  success('one-use recovery proof changes a password without UPDATE_PASSWORD');
  const currentPasswordProof = assertion(recovered.id, 'CURRENT_PASSWORD_VERIFIED');
  const currentCapability = await operation('password/capability', { assertion: currentPasswordProof });
  expect(currentCapability.status === 200 && currentCapability.data?.token, 'Current-password assertion exchange failed');
  const changedPassword = password(); // carenest:allow-secret
  expect((await operation('password/setup', { token: currentCapability.data.token,
    newPassword: changedPassword, confirmPassword: changedPassword })).status === 200, 'Current-password capability mutation failed'); // carenest:allow-secret
  expect((await token(recovered.username, changedPassword)).status === 200, 'Changed password does not authenticate');
  expect((await token(recovered.username, recoveredPassword)).status === 400, 'Prior password still authenticates after change');
  success('current-password assertion changes a password without UPDATE_PASSWORD');
  const raced = await createUser('auth-e2e-assertion-race');
  const raceProof = assertion(raced.id);
  const races = await Promise.all(Array.from({ length: 6 }, () => operation('password/capability', { assertion: raceProof })));
  expect(races.filter((result) => result.status === 200 && result.data?.token).length === 1
    && races.filter((result) => result.status === 400).length === 5, 'Concurrent assertion redemption did not have exactly one winner');
  success('concurrent assertion exchange accepts exactly one request');
  expect((await operation('password/capability', { assertion: assertion(disabled.id) })).status === 400,
    'Disabled account acquired verified password capability');
  success('disabled account cannot exchange signed proof');

  expect((await operation('recovery/email', { userId: regular.id, code: '024681' })).status === 200, 'OTP email failed');
  let mail;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const messages = (await response(`${mailUrl}/api/v1/messages`)).data.messages;
    if (messages?.length) { mail = (await response(`${mailUrl}/api/v1/message/${messages[0].ID}`)).data; break; }
    await pause(200);
  }
  expect(mail?.Text?.includes('024681'), 'Mailpit did not receive the OTP template');
  success('OTP is delivered through the Keycloak email provider to Mailpit');
  expect((await operation('setup/email', { userId: regular.id })).status === 200, 'Setup email failed');
  await pause(200);
  const welcomeMessages = (await response(`${mailUrl}/api/v1/messages`)).data.messages;
  const welcome = (await response(`${mailUrl}/api/v1/message/${welcomeMessages[0].ID}`)).data;
  expect(welcome.Text.includes('http://localhost:5173/forgot-password') && !welcome.Text.includes('login-actions'), 'Setup email does not point to React');
  success('setup email points to React instead of Keycloak required-action UI');

  for (const clientId of ['carenest-web', 'carenest-mobile']) {
    const [client] = await admin(`/clients?clientId=${clientId}`);
    expect(client.standardFlowEnabled && !client.directAccessGrantsEnabled && client.attributes['pkce.code.challenge.method'] === 'S256', 'Public PKCE client changed');
    if (clientId === 'carenest-web') {
      expect(client.redirectUris.includes('http://localhost:3000/*') && client.webOrigins.includes('http://localhost:3000'), 'Public Web origins changed');
    } else expect(client.redirectUris.includes('carenest://oauth/callback'), 'Mobile redirect changed');
    expect((await token(regular.username, regular.password, clientId)).status === 400, 'Public client can use password grant');
  }
  success('existing Web/Mobile public clients retain PKCE and reject password grant');
  const locked = await createUser('auth-e2e-lock');
  for (let attempt = 0; attempt < 5; attempt += 1) await token(locked.username, password());
  await pause(500);
  expect((await token(locked.username, locked.password)).status === 400, 'Native brute-force lock did not apply');
  success('native Keycloak brute-force protection remains active');
  const refresh = await response(`${baseUrl}/realms/carenest/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({
    grant_type: 'refresh_token', client_id: 'carenest-auth-gateway', client_secret: gatewaySecret, refresh_token: login.data.refresh_token, // carenest:allow-secret
  }) });
  expect(refresh.status === 200 && refresh.data.refresh_token !== login.data.refresh_token, 'Refresh rotation failed');
  success('native refresh-token rotation remains active');
  const currentRealm = await admin('');
  await admin('', 'PUT', { smtpServer: { ...currentRealm.smtpServer, port: '1' } });
  expect((await operation('recovery/email', { userId: regular.id, code: '024681' })).status === 503, 'SMTP outage must report unavailable');
  success('SMTP outage is a controlled 503');
  const [gateway] = await admin('/clients?clientId=carenest-auth-gateway');
  await admin(`/clients/${gateway.id}`, 'PUT', { ...gateway, enabled: false });
  expect((await operation('recovery/email', { userId: regular.id, code: '024681' })).status === 503, 'Disabled gateway still permits extension operations');
  success('disabling the gateway also closes extension operations');
  console.log(`E2E PASS ${passed}/${passed}; passwords, OTP bodies and tokens were not printed.`);
} catch (error) {
  console.error(`E2E FAIL after ${passed} scenarios: ${error.message}`);
  if (started) {
    const logs = compose(['logs', '--no-color', '--tail', '160', 'keycloak']);
    await writeFile(resolve(directory, 'keycloak.log'), logs);
    console.error(`Server diagnostics: target/${project}/keycloak.log`);
  }
  process.exitCode = 1;
} finally {
  if (started && /^carenest-auth-e2e-[a-f0-9]{8}$/.test(project)) {
    compose(['down', '--remove-orphans']);
    console.log('Removed only this isolated test project; user containers and volumes were not changed.');
  }
}
