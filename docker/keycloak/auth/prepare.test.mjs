import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createAuthRealm } from './prepare.mjs';

test('opt-in realm preserves base clients, PKCE and native flow creation', async () => {
  const base = JSON.parse(await readFile(new URL('../import/carenest-realm.json', import.meta.url), 'utf8'));
  const before = structuredClone(base);
  const output = createAuthRealm(base);
  assert.deepEqual(base, before);
  assert.deepEqual(output.clients.slice(0, base.clients.length), base.clients);
  assert.equal(output.authenticationFlows, base.authenticationFlows);
  const gateway = output.clients.at(-1);
  assert.equal(gateway.enabled, false);
  assert.equal(gateway.publicClient, false);
  assert.equal(gateway.standardFlowEnabled, false);
  assert.equal(gateway.serviceAccountsEnabled, false);
  assert.equal(output.emailTheme, 'carenest-auth');
});

test('refuses to silently overwrite an existing gateway definition', () => {
  assert.throws(() => createAuthRealm({ clients: [{ clientId: 'carenest-auth-gateway' }] }), /already exists/);
});
