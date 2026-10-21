import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

export function createAuthRealm(base) {
  const realm = structuredClone(base);
  if (realm.clients.some((client) => client.clientId === 'carenest-auth-gateway')) {
    throw new Error('Gateway client already exists in the base realm; inspect before replacing it');
  }
  realm.emailTheme = 'carenest-auth';
  realm.clients.push({
    clientId: 'carenest-auth-gateway', name: 'CareNest private Web auth gateway',
    enabled: false, protocol: 'openid-connect', publicClient: false,
    clientAuthenticatorType: 'client-secret', secret: '${KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET}',
    standardFlowEnabled: false, implicitFlowEnabled: false,
    directAccessGrantsEnabled: true, serviceAccountsEnabled: false,
    redirectUris: [], webOrigins: [],
    protocolMappers: [{
      name: 'carenest-api-audience', protocol: 'openid-connect', protocolMapper: 'oidc-audience-mapper',
      consentRequired: false, config: {
        'included.custom.audience': 'carenest-api', 'id.token.claim': 'false',
        'access.token.claim': 'true', 'introspection.token.claim': 'true',
      },
    }],
  });
  // Flow được sao chép sau khi Keycloak đã tạo đầy đủ built-in flows, không khai báo thay toàn bộ danh sách.
  return realm;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const here = dirname(fileURLToPath(import.meta.url));
  const base = JSON.parse(await readFile(resolve(here, '../import/carenest-realm.json'), 'utf8'));
  const output = resolve(here, 'generated/carenest-realm.json');
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(createAuthRealm(base), null, 2)}\n`, 'utf8');
  console.log('Prepared opt-in realm; gateway remains DISABLED until bootstrap verifies its dedicated flow.');
}
