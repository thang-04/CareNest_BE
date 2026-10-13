# ADR-0013 — React-owned Web authentication gateway

- Status: ACCEPTED (design approved; implementation gated by phase verification). Phần lưu token server-side thay bởi ADR-0015.
- Date: 2026-10-11
- Source: user selected React-owned forms, all authentication screens, retained Keycloak; requested implementation of the chat plan.
- Related: ADR-0011, AUTH-07, AUTH-09; plan `docs/plans/active/2026-10-11-react-auth-keycloak.md`.

## Decision

Web uses React forms calling a CareNest auth gateway. A dedicated confidential Keycloak client accepts Direct Grant only through the backend. This is an explicit exception to ADR-0011's PKCE-only Web login and prohibition of CareNest password-login endpoints. It is not Authorization Code + PKCE.

Keycloak remains the password/hash/policy/brute-force/token authority. Existing Mobile/public PKCE clients, JWT issuer/audience, roles and backend scope policies remain unchanged. Do not enable Direct Grant on public Web/Mobile clients or reuse the dev client.

The extension is pinned to Keycloak 26.8.0. A checkpoint after native username/password validation issues a typed one-use setup capability when UPDATE_PASSWORD is the only pending action. It never creates a user session or access/refresh token for temporary credentials. Other required actions are not removed or bypassed. Native browser flows remain available to Mobile.

Password changes validate Keycloak policy, consume capabilities atomically, clear only UPDATE_PASSWORD, and revoke the user's sessions. Privileged extension operations authenticate the existing backend service account and check manage-users. Recovery OTP verification and its password-reset capability exchange belong to Phase 2; Phase 1 exposes only capability-bound first-login setup and email operations.

Web access tokens stay in memory. Encrypted server-side token storage is referenced by an opaque HttpOnly cookie. Cookie operations require CSRF and exact credentialed CORS origins. User INACTIVE/provisioning and scope checks remain authoritative.

All changes are opt-in and disabled by default. Email templates use existing Keycloak SMTP; setup emails lead to React. No secret, password or OTP is logged.

## Trade-off

Direct Grant exposes credentials to the application and is explicitly discouraged by current OAuth security guidance. A gateway does not remove this protocol limitation. The chosen direction follows the user's no-redirect requirement, costs a custom security/provider module, and needs security review and real-container verification before real deployment.

Alternative: retain standard PKCE and port the React design to a Keycloak theme. This has less custom security code but was not the chosen UX. Replacing Keycloak with native auth was also not chosen.

References: [Keycloak OIDC guidance](https://www.keycloak.org/securing-apps/oidc-layers), [Keycloak 26.8.0 extensions](https://www.keycloak.org/docs/26.8.0/server_development/#_extensions).

## Rollout and compatibility

Use an opt-in image/Compose overlay and gateway client flag. Existing realms need a narrow client/flow/theme update, not another import or realm replacement. Disable the gateway and return to the stock image to roll back; never remove user accounts/volumes or silently fall back to mock authentication.

The plan has three review gates: extension/contract, backend APIs, React integration. Phase 1 alone does not constitute a working FE integration.
