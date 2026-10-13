import test from 'node:test';
import assert from 'node:assert/strict';
import { assertSafeFlow, validateOrigin } from './bootstrap.mjs';

const safe = [
  { providerId: 'direct-grant-validate-username', requirement: 'REQUIRED', level: 0 },
  { providerId: 'direct-grant-validate-password', requirement: 'REQUIRED', level: 0 },
  { authenticationFlow: true, requirement: 'CONDITIONAL', level: 0 },
  { providerId: 'conditional-user-configured', requirement: 'REQUIRED', level: 1 },
  { providerId: 'direct-grant-validate-otp', requirement: 'REQUIRED', level: 1 },
  { providerId: 'carenest-setup-required', requirement: 'REQUIRED', level: 0 },
];
test('accepts only native credential validation before the checkpoint', () => assert.doesNotThrow(() => assertSafeFlow(safe)));
test('rejects missing, optional, reordered or nested credential checkpoints', () => {
  assert.throws(() => assertSafeFlow(safe.filter((entry) => entry.providerId !== 'direct-grant-validate-password')));
  assert.throws(() => assertSafeFlow([...safe].reverse()));
  assert.throws(() => assertSafeFlow(safe.map((entry) => ({ ...entry, requirement: 'ALTERNATIVE' }))));
  assert.throws(() => assertSafeFlow(safe.map((entry) => ({ ...entry, level: 1 }))));
  assert.throws(() => assertSafeFlow([...safe, safe.at(-1)]));
});
test('credentials go only to HTTPS or a local loopback origin', () => {
  assert.equal(validateOrigin('http://127.0.0.1:8180'), 'http://127.0.0.1:8180');
  assert.equal(validateOrigin('https://auth.example.test'), 'https://auth.example.test');
  for (const value of ['http://auth.example.test', 'https://user:pass@auth.example.test', 'https://auth.example.test/path']) {
    assert.throws(() => validateOrigin(value));
  }
});

test('rejects native validators nested beneath a disabled parent', () => {
  const bypass = [
    { providerId: 'direct-grant-validate-username', requirement: 'REQUIRED', level: 0 },
    { authenticationFlow: true, requirement: 'DISABLED', level: 0 },
    { providerId: 'direct-grant-validate-password', requirement: 'REQUIRED', level: 1 },
    { providerId: 'direct-grant-validate-otp', requirement: 'REQUIRED', level: 1 },
    { providerId: 'carenest-setup-required', requirement: 'REQUIRED', level: 0 },
  ];
  assert.throws(() => assertSafeFlow(bypass), /Unsafe gateway flow/);
});

test('rejects a disabled/alternative OTP parent or an extra condition that could skip OTP', () => {
  for (const requirement of ['DISABLED', 'ALTERNATIVE']) {
    assert.throws(() => assertSafeFlow(safe.map((entry, index) => index === 2 ? { ...entry, requirement } : entry)));
  }
  assert.throws(() => assertSafeFlow([...safe.slice(0, 4),
    { providerId: 'conditional-user-role', requirement: 'REQUIRED', level: 1 }, ...safe.slice(4)]));
});
