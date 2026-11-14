// Sinh file user Keycloak cho realm import: dev (hash mật khẩu dev cố định) và vps (mật khẩu lấy từ biến môi trường).
import { pbkdf2Sync } from 'node:crypto';

// Mật khẩu chung của mọi tài khoản trên máy dev/local; VPS dùng KEYCLOAK_DEV_USER_PASSWORD (không commit)
export const DEV_PASSWORD = 'Carenest@2026'; // carenest:allow-secret — mật khẩu giả chỉ dùng local, VPS lấy từ biến môi trường
const SALT = Buffer.from('carenest-dev-dataset-salt').subarray(0, 16);
const ITERATIONS = 210000;

function devCredential() {
  const value = pbkdf2Sync(DEV_PASSWORD, SALT, ITERATIONS, 64, 'sha512').toString('base64');
  return {
    type: 'password',
    secretData: JSON.stringify({ value, salt: SALT.toString('base64'), additionalParameters: {} }),
    credentialData: JSON.stringify({ hashIterations: ITERATIONS, algorithm: 'pbkdf2-sha512', additionalParameters: {} }),
  };
}
const VPS_CREDENTIAL = { type: 'password', value: '${KEYCLOAK_DEV_USER_PASSWORD}', temporary: false };

export function keycloakAccounts(d) {
  return [...d.staff, ...d.parentAccounts].map((a) => ({
    id: a.keycloakId, username: a.username, fullName: a.fullName, email: a.email ?? null, role: a.role,
    enabled: a.status === 'ACTIVE', pendingPassword: a.pendingPassword, // carenest:allow-secret — cờ, không phải mật khẩu
  }));
}

export function renderKeycloakUsers(d, target) {
  const credential = target === 'dev' ? devCredential() : VPS_CREDENTIAL;
  const users = keycloakAccounts(d).map((a) => ({
    id: a.id,
    username: a.username,
    enabled: a.enabled,
    firstName: a.fullName,
    ...(a.email ? { email: a.email, emailVerified: true } : {}),
    realmRoles: [a.role],
    ...(a.pendingPassword ? { requiredActions: ['UPDATE_PASSWORD'] } : {}),
    credentials: [credential],
  }));
  return `${JSON.stringify({ realm: 'carenest', users }, null, 2)}\n`;
}
