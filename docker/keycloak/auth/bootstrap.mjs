import { pathToFileURL } from 'node:url';

const ALIAS = 'carenest-web-direct-grant';
const MARKER = 'CareNest opt-in verified-password gateway';
const CLIENT = 'carenest-auth-gateway';

export function validateOrigin(value) {
  const url = new URL(value);
  if (url.username || url.password || url.search || url.hash || !['', '/'].includes(url.pathname)
      || (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)))) {
    throw new Error('Use an HTTPS origin, or HTTP loopback for local tests');
  }
  return url.origin;
}

export function assertSafeFlow(executions) {
  const expected = [
    ['direct-grant-validate-username', 'REQUIRED', 0],
    ['direct-grant-validate-password', 'REQUIRED', 0],
    [null, 'CONDITIONAL', 0],
    ['conditional-user-configured', 'REQUIRED', 1],
    ['direct-grant-validate-otp', 'REQUIRED', 1],
    ['carenest-setup-required', 'REQUIRED', 0],
  ];
  if (executions.length !== expected.length || expected.some(([provider, requirement, level], index) => {
    const entry = executions[index];
    return entry.level !== level || entry.requirement !== requirement
      || (provider === null ? entry.authenticationFlow !== true : entry.providerId !== provider || entry.authenticationFlow === true);
  })) {
    throw new Error('Unsafe gateway flow: require exact native username/password/conditional-OTP topology before the setup checkpoint');
  }
}

export async function configureGateway({ baseUrl, realm = 'carenest', adminToken, gatewaySecret, apply = false, enable = false }) {
  baseUrl = validateOrigin(baseUrl);
  if (!/^[A-Za-z0-9_-]+$/.test(realm)) throw new Error('Invalid realm name');
  if (!adminToken) throw new Error('Admin token required');
  if (apply && (!gatewaySecret || gatewaySecret.length < 32)) throw new Error('Gateway secret must contain at least 32 characters');
  const root = `${baseUrl}/admin/realms/${realm}`;
  const request = async (path, method = 'GET', body) => {
    const response = await fetch(`${root}${path}`, { method, headers: {
      Authorization: `Bearer ${adminToken}`, ...(body ? { 'Content-Type': 'application/json' } : {}),
    }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(10000) });
    if (!response.ok) throw new Error(`Keycloak configuration ${method} ${path} returned ${response.status}`);
    return response.status === 204 || response.headers.get('content-length') === '0' ? null : response.text().then((text) => text ? JSON.parse(text) : null);
  };
  const [clients, flows, providers] = await Promise.all([
    request(`/clients?clientId=${CLIENT}`), request('/authentication/flows'), request('/authentication/authenticator-providers'),
  ]);
  let client = clients.find((entry) => entry.clientId === CLIENT);
  let flow = flows.find((entry) => entry.alias === ALIAS);
  if (client && (client.name !== 'CareNest private Web auth gateway' || client.publicClient)) {
    throw new Error('Existing gateway client is not owned by this setup; inspect it manually');
  }
  if (flow && flow.description !== MARKER) throw new Error('Existing gateway flow has no CareNest ownership marker; inspect it manually');
  if (!providers.some((entry) => entry.id === 'carenest-setup-required')) throw new Error('CareNest extension is not installed');
  const preview = { realm, createClient: !client, copyNativeDirectGrant: !flow, configureDedicatedCheckpoint: true,
    setEmailTheme: 'carenest-auth', enableGateway: enable, existingPublicClientsChanged: false };
  if (!apply) return preview;

  if (client) await request(`/clients/${client.id}`, 'PUT', { ...client, enabled: false });
  if (!flow) {
    await request('/authentication/flows/direct%20grant/copy', 'POST', { newName: ALIAS });
    flow = (await request('/authentication/flows')).find((entry) => entry.alias === ALIAS);
    if (!flow) throw new Error('Copied flow was not found; gateway remains disabled');
    await request(`/authentication/flows/${flow.id}`, 'PUT', { ...flow, description: MARKER });
  }
  let executions = await request(`/authentication/flows/${ALIAS}/executions`);
  if (!executions.some((entry) => entry.providerId === 'carenest-setup-required')) {
    await request(`/authentication/flows/${ALIAS}/executions/execution`, 'POST', { provider: 'carenest-setup-required' });
    executions = await request(`/authentication/flows/${ALIAS}/executions`);
  }
  const checkpoint = executions.find((entry) => entry.providerId === 'carenest-setup-required');
  await request(`/authentication/flows/${ALIAS}/executions`, 'PUT', { ...checkpoint, requirement: 'REQUIRED' });
  assertSafeFlow(await request(`/authentication/flows/${ALIAS}/executions`));
  if (!client) {
    await request('/clients', 'POST', { clientId: CLIENT, name: 'CareNest private Web auth gateway', enabled: false,
      protocol: 'openid-connect', publicClient: false, standardFlowEnabled: false, implicitFlowEnabled: false,
      directAccessGrantsEnabled: true, serviceAccountsEnabled: false, redirectUris: [], webOrigins: [],
      protocolMappers: [{ name: 'carenest-api-audience', protocol: 'openid-connect', protocolMapper: 'oidc-audience-mapper',
        consentRequired: false, config: { 'included.custom.audience': 'carenest-api', 'id.token.claim': 'false', 'access.token.claim': 'true' } }],
    });
    client = (await request(`/clients?clientId=${CLIENT}`)).find((entry) => entry.clientId === CLIENT);
  }
  await request(`/clients/${client.id}`, 'PUT', { ...client, enabled: enable, secret: gatewaySecret, // carenest:allow-secret
    standardFlowEnabled: false, implicitFlowEnabled: false, directAccessGrantsEnabled: true, serviceAccountsEnabled: false,
    authenticationFlowBindingOverrides: { ...client.authenticationFlowBindingOverrides, direct_grant: flow.id } });
  await request('', 'PUT', { emailTheme: 'carenest-auth' });
  return { ...preview, applied: true };
}

export async function adminLogin(baseUrl, username, password) {
  baseUrl = validateOrigin(baseUrl);
  if (!username || !password) throw new Error('Admin credentials required through environment variables');
  const response = await fetch(`${baseUrl}/realms/master/protocol/openid-connect/token`, {
    method: 'POST', body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username, password }),
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error(`Admin authentication returned ${response.status}`);
  return (await response.json()).access_token;
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  try {
    const baseUrl = process.env.KEYCLOAK_URL || 'http://localhost:8180';
    const adminToken = await adminLogin(baseUrl, process.env.KC_BOOTSTRAP_ADMIN_USERNAME, process.env.KC_BOOTSTRAP_ADMIN_PASSWORD);
    console.log(JSON.stringify(await configureGateway({ baseUrl, adminToken, realm: process.env.KEYCLOAK_REALM || 'carenest',
      gatewaySecret: process.env.KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET, // carenest:allow-secret
      apply: process.argv.includes('--apply'), enable: process.argv.includes('--enable') }), null, 2));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
