---
title: React-owned authentication with Keycloak
status: in-progress
owner: CareNest team
jira: unassigned
branch: feature/authentication
modules: [identity-access]
rules: [AUTH-07, AUTH-09]
risk: high
created: 2026-10-11
updated: 2026-10-13
---
# React-owned authentication with Keycloak

## Context
FE `src/services/authService.js:15` calls proposed login/me/logout routes; BE currently uses PKCE (`docs/contracts/AUTH_CONTRACT.md:7`). The user approved the complete chat plan by requesting implementation and confirming after leaving Plan Mode on 2026-10-11. No commit/push/new branch is authorized.

## Quyết định đã chốt
1. All end-user auth screens remain React; retain Keycloak (user selection in this conversation).
2. Use a dedicated confidential gateway client; Direct Grant is an explicit Web exception, not PKCE. Mobile retains PKCE.
3. Preserve temporary-password first login; username or email accepted (defaults in the approved chat plan).
4. Keycloak verifies passwords, enforces its policy/brute-force protection, and signs tokens. No password hashes in CareNest.
5. Access token stays in React memory; opaque HttpOnly cookie references encrypted server-side tokens. CSRF required. *(Phần lưu token server-side đã thay bằng cookie mã hóa — Phase 4, ADR-0015.)*
6. OTP defaults: 6 digits, 5 minutes, 60-second resend interval, 5 attempts; password capability 10 minutes.

## Làm rõ nghiệp vụ
- Actor: school-issued account holder; outcome: login/recover/change password in React.
- Rules: AUTH-07 (ACCEPTED), AUTH-09 (CONFIRMED); no new role/scope policy.
- Flow: [B] React form → [S] CareNest auth gateway → Keycloak → existing account/scope checks.
- Conflict: ADR-0011 prohibited password login at CareNest; ADR-0013 records the approved Web exception.
- Consumers: FE login, forgot-password, verify-otp, reset-password, change-password, AuthContext, HTTP client. APP keeps existing PKCE and issuer/audience.
- Old data: preserve accounts, assignments, credentials and migrations; add migrations only.

### Câu hỏi mở
None for the approved scope. TEAM_LEADER is not mapped to a backend role; unsupported Web roles get a clear landing screen.

## Acceptance criteria
- AC-1: Valid credentials issue Keycloak tokens; wrong credentials remain subject to native brute-force handling.
- AC-2: Temporary credentials issue only a short-lived one-use setup capability; never an access/refresh token.
- AC-3: Invalid/expired/replayed capability, disabled account, unrelated required action, and unauthorized service caller cannot change a password.
- AC-4: Policy rejection does not authorize login; successful password mutation removes only UPDATE_PASSWORD and revokes sessions.
- AC-5: OTP never appears in public API/logs; recovery is generic and capability-bound.
- AC-6 (AUTH-07): Browser cookie/refresh/CSRF and account status checks preserve server authorization.
- AC-7 (AUTH-09): Existing provisioning/status and Mobile PKCE continue working.
- AC-8: React keeps the current visual design, uses real auth independently of mock business data, and handles refresh/403/503 correctly.

## Key decisions
| Decision | Chosen | Alternative | Reason |
| --- | --- | --- | --- |
| UI | React-owned forms | Keycloak theme | Explicit user choice |
| IdP | Keycloak retained | Native CareNest auth | Preserve password/token authority |
| First login | Keycloak extension after password validation | Parse human-readable token errors | Typed verified challenge; no error-text authentication |
| Delivery | Keycloak SMTP/email provider | Add Java mail stack | Existing infrastructure |
| Workspace | Existing checkout; preserve dirty files | New branch/worktree | No branch creation authorized; no commit/push |

## Phases
### Phase 1 — Keycloak extension and contract
- Add a standalone Maven extension pinned to Keycloak 26.8.0; native credential validation precedes setup challenge.
- Add authenticated extension operations for capability-bound setup and OTP/setup email; native policy and one-use cache. Recovery reset capability exchange belongs to Phase 2, never a userId-only reset primitive.
- Add custom image, email templates, disabled gateway client/flow, opt-in Compose overlay and realm update documentation.
- Tests: authenticator/capability/security/policy unit tests plus isolated Keycloak/Mailpit integration.
- Exit: extension Maven verify, `node scripts/check-ai-layer.mjs`, BE `node scripts/verify.mjs` (skip 0), E2E matrix.
- Stop for user review per `.ai/workflows/plan-change.md`.

### Phase 2 — Backend auth APIs
- Add gateway integration, session/challenge persistence, new Flyway migration, DTOs and APIs from the approved chat plan.
- Public auth requests omit Bearer; exact permit list, CSRF cookie/header, specific credentialed CORS origins.
- Atomic session refresh and single-use challenge claims; no DB transaction across HTTP. Generic recovery replies.
- Change setup emails to React when the opt-in feature is enabled; retain existing provisioning contract.
- Tests: controller contract, service, PostgreSQL concurrency/migration, Keycloak/Mailpit E2E; snapshot and full verify.
- Stop for user review.

### Phase 3 — React integration
- Separate auth mode from mock data; memory-only real access token and cookie bootstrap/refresh.
- Wire existing auth/password screens, normalize BE envelope/errors, roles and assignments.
- Keep legacy mock role/UI only in mock mode; no invented TEAM_LEADER permissions or mock production fallback.
- Tests: FE verify, auth integration, multiple tabs, responsive/keyboard checks, real login→me→refresh→logout.
- Stop for user review.

## Test matrix
| AC | Tests | Phase | Result |
| --- | --- | --- | --- |
| 1, 2 | SetupRequiredAuthenticatorTest + isolated token requests | 1 | pass, E2E 20/20 |
| 3, 4 | AuthOperationsTest, ServiceAccountAuthorizerTest, service authorization E2E | 1 | pass, 24 Java tests/0 skip |
| 5 | Email operations + OTP/recovery API matrix | 1–2 | pass; extension38 tests, E2E26/26 |
| 6, 7 | Auth controller/service/DB concurrency + existing suite | 2 | full196 tests/0 skip; exact CSRF and snapshot pass |
| 8 | FE contract/client/real browser matrix | 3 | FE40 tests + isolated HTTP9/9 pass; browser UI pending |

## Rủi ro & rollback
- Direct Grant is discouraged by OAuth security guidance; approved exception is documented, feature disabled by default, security review before real deployment.
- Extension depends on Keycloak SPI: pin 26.8.0 and run real-container tests before upgrades.
- Existing realm import is not an updater: compare and apply only gateway client/flow/email theme; never overwrite realm data.
- Rollback: disable new gateway client/feature, return to stock image; existing Mobile clients/accounts remain. Never silently switch to mock.
- Docker was stopped at preflight; start Desktop, use an isolated test project with throwaway data, leave user containers/volumes untouched.

## Validation log
- Code facts checked against current BE/FE source; no runtime auth claims at planning time.
- More than 8 files: split into the three reviewable phases above.
- New dependency/image/API work is within the approved chat plan; no version upgrade to the existing stack.
- No PENDING business rule promoted or invented; defaults above are approved implementation defaults.

## Progress log
### 2026-10-11 — Approval and Phase 1 start
- Approval: user requested “implement đi”, then “ok” after Default mode became active.
- Branch: feature/authentication; pre-existing `.claude/settings.json` and `plans/` preserved; FE pre-existing artifacts preserved.
- Commit: none authorized. Verification: pending.
- Ruling: work in the existing checkout without creating a branch/worktree; preserve user dirty work and use scoped edits.
- TDD: checkpoint RED 5 failures/0 errors → GREEN; operation RED 9 failures/0 errors → GREEN. Extension unit suite expanded to 21 tests/0 skip.
- Baseline BE: `VERIFY PASS full | tests 136 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`.
- Auto-review rejected an initial reset-by-userId primitive. Replaced with capability-only setup and explicit authenticated provider operations; no privileged reset-by-userId implementation was added. The new alternative was accepted by auto-review.
- E2E attempt 1: test fixture missed CARENEST_MOBILE_REDIRECT_URI; fresh import rejected an unresolved redirect URI. Fixture now supplies the same Mobile callback as base Compose; user infrastructure untouched.

### 2026-10-12 — Phase 1 verification, awaiting user review
- Extension Maven verify: 24 tests/0 failures/0 errors/0 skips; Spotless pass. Node bootstrap/config: 8/8 pass.
- E2E: `E2E PASS 20/20`; RS256/JWT, temporary capability, replay/policy/status, service authorization, OTP/setup email, native brute-force/refresh, public clients, unsafe topology and feature-disable behavior.
- Independent reviewer: High topology bug + Medium public-origin bug + capability race gap; each reproduced and fixed with RED→GREEN regression. No second review dispatch; post-fix checks pass.
- Ruling: replace userId-only mutation with capability-only setup; recovery exchange stays in Phase 2. Security boundary preserved; cost is that full recovery is not yet available.
- Ruling: SMTP-refusal test uses a closed port on a resolvable service, replacing stopped-container DNS delay; DNS-outage timing remains unverified.
- Transient Docker context metadata file lock: build retried without killing processes/config changes, then succeeded.
- Engineering memory: RuntimeDelegate test runtime, immutable email attributes, auth flow ancestry and capability concurrency incidents recorded.
- Detailed evidence/limits: `docs/quality/reports/2026-10-12-react-auth-phase1.md`.
- Final BE verify: `VERIFY PASS full | tests 136 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 35s | log target/verify.log`.
- AI layer: 0 errors/0 warnings; `git diff --check` clean. No stubs remain in provider. Test containers removed. Commit: none. FE/APP edits: none.
- Next: user Phase 1 review gate, then Phase 2 APIs/session/OTP and Phase 3 React.

### 2026-10-12 — Phase 1 approved; Phase 2 started
- User approval: “ok tiếp” after the Phase 1 review/result message.
- Implement Phase 2 only; Phase 3 FE integration remains the next review gate.
- Recovery/current-password mutation will use a short-lived, one-use HMAC assertion exchanged by the authenticated backend service for a provider capability. No userId-only mutation endpoint.
- Session refresh: DB lease commits before HTTP; concurrent callers coalesce; an expired/uncertain rotation fails closed rather than reusing a refresh token.
- OTP wrong-attempt counters commit before any outward business exception; no rollback of abuse controls.

### 2026-10-12 — Phase 2 verified, awaiting review
- Gateway APIs, cookie/CSRF, V4 encrypted state, rate/OTP/reset/current password and React setup emails implemented; feature default off. FE/APP edits:none; no commit/push/new branch.
- RED context25 errors →3 context/snapshot pass; error-mapping2 failures →adapter pass; boundary60 tests/8 failures →62 pass. Added concurrency/cleanup/send budget/coalescing/revoked-refresh regressions.
- Review found Medium refresh-invalid_grant mapping and cleanup scheduling gaps; fixed with tests, independent recheck no remaining blocker. Swagger public security/hidden CsrfToken fixed and semantically tested.
- Full verify after final source edits: `VERIFY PASS full | tests 196 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 48s | log target/verify.log`.
- Provider Maven38 tests/0 skip; isolated Keycloak/PostgreSQL/Mailpit E2E26/26; test project cleaned. Provider E2E and gateway Spring tests are distinct; browser React-through-backend flow remains Phase3.
- Snapshot intentional: add `/auth/**`, PUT `/me/password`,428 and schemas; existing business endpoints preserved. FE consumers and unchanged Mobile PKCE recorded in AUTH_CONTRACT/CROSS_REPO_MAP.
- Engineering incidents/module/current-state/schema/contract/operations docs updated. Detailed evidence and operational limits: `docs/quality/reports/2026-10-12-react-auth-phase2.md`.
- Next: user Phase2 review gate, then explicit Phase3 permission to edit React repo.

### 2026-10-13 — Phase 3 React source verified; browser review pending
- User replied “duyệt” to the Phase 2 handoff, authorizing React integration. Existing FE `dev` branch and user UI artifacts preserved; no commit/push/new branch, dependency change, or real realm/.env mutation.
- React login, first-login, recovery, OTP, reset and current-password forms now use the opt-in backend gateway. Auth mode is separate from mock business data. Real access token stays in memory; proof stays outside URL/history/storage. Multi-tab cookie ordering and session-expiry findings were fixed with RED→GREEN tests.
- FE: 40 auth tests pass; full lint/format/build verify pass. Isolated Keycloak/PostgreSQL/Mailpit + Java backend/Vite HTTP smoke 9/9 pass; own test stack cleaned. Details: `CareNest_FE/docs/quality/reports/2026-10-12-react-auth-integration.md`.
- UI browser, keyboard/responsive and actual two-tab observation remain pending: CUA had no browser surface; IAB/Chrome returned `Browser is not available`. FE plan remains `in-progress`; do not report visual or deployed production behavior as verified.

### 2026-10-13 — Auth integration review; session expiry fixes
- User asked to review the auth BE↔FE integration (auth only, not business APIs) and approved fixing BE session bugs first. FE edits: none.
- Fixed `BUG-261013-web-auth-session-absolute-expiry`: absolute expiry no longer derived from `refresh_expires_in` (realm idle 1800 capped sessions at 30 minutes); RED→GREEN regression.
- Ruling (replaces Phase 2 "uncertain rotation fails closed" at BE layer): `ProviderUnavailable` on refresh keeps the session so the contracted 503 retry works. Fail-closed remains at Keycloak via `revokeRefreshToken: true`, `refreshTokenMaxReuse: 0`; stale token ⇒ `invalid_grant` ⇒ session deleted.
- FE (user authorized): `authPost` refetches CSRF and retries once on 403 `desc=Forbidden` (only CSRF can produce it on permitAll `/auth/**`); business 403 not retried. 3 RED→GREEN tests; FE verify 43/43, build pass.
- Noted, not fixed: forgot-password timing may reveal email existence; `awaitRefresh` holds a thread ≤15s; FE remember defaults on; prod SameSite/domain undecided; BE gateway and FE auth work uncommitted, FE auth sits on `feature/FE-FEAT-48-nutrition-management`.
- Real E2E beyond the 30-minute mark not run. Commit: none.

## Phase 4 — Phiên Web không lưu DB (ADR-0015) — done

Thay đề xuất Phase 4 cũ (chỉ bỏ cột `remember`); đề xuất cũ bỏ, không làm. Không sửa source/schema tới khi user duyệt plan và Câu hỏi mở hết `- [ ]`.

### Quyết định user chốt (chat 2026-10-13)
1. Bỏ `web_auth_sessions`: refresh token Keycloak mã hóa AES-GCM nằm trong cookie HttpOnly `CARENEST_SESSION`; Keycloak là nơi duy nhất giữ phiên (idle 30', trần 10h, thu hồi). Thay phần "encrypted server-side tokens" của Quyết định đã chốt #5.
2. Giữ 2 bảng `web_auth_challenges`, `web_auth_rate_limits` trong DB. Không gộp `user_accounts`: forgot với email lạ/INACTIVE/trùng vẫn tạo OTP decoy `user_id NULL` (`WebAuthServiceImpl.java:231-247`); bucket rate theo HMAC email, kể cả email không tồn tại (`:225-230`); tránh secret trên entity đọc mọi request.
3. BE chạy 1 instance Docker ⇒ gộp refresh đồng thời trong bộ nhớ BE; không sửa FE, không đổi rotation của realm.
4. Xóa `idx_web_auth_challenges_lookup`.
5. Chấp nhận mọi user Web đang đăng nhập bị logout 1 lần khi deploy.
6. Ghi ADR-0015 mới; ADR-0013 thêm dòng "phần lưu token server-side thay bởi ADR-0015".
7. Bỏ tiền tố `web_`/`WebAuth` khó hiểu ở cả bảng, class và cấu hình (phương án C): bảng `password_tokens`, `otp_send_limits`; class `WebAuth*` ⇒ `Auth*`; key `carenest.web-auth.*` ⇒ `carenest.auth.*`.

### Kiểm tra code/consumer
- Phiên DB hiện tại: login ghi session `WebAuthServiceImpl.java:91-102`; refresh lease/CACHED/BUSY `:143-210`; logout `:213-217`; cookie opaque không đổi khi refresh `WebAuthController.java:63-68`.
- Khóa tài khoản đã thu hồi qua Keycloak `AccountProvisioningServiceImpl.java:131` (`logoutUser`); refresh vẫn gọi `currentUsers.getCurrentUser` ⇒ 403 inactive/chưa provision (`CurrentUserService.java:57-62`).
- Realm: `revokeRefreshToken: true`, `refreshTokenMaxReuse: 0`, idle 1800, max 36000 (`docker/keycloak/import/carenest-realm.json:18-20`).
- FE `CareNest_FE/src/services/http/authTransport.js:9` `withCredentials: true`, `:75` single-flight trong tab ⇒ nhận `Set-Cookie` từ refresh, không sửa FE. APP dùng PKCE, không ảnh hưởng.
- Contract đổi: `/auth/refresh` trả `Set-Cookie` mới (200) và xóa cookie (401). Body `force` vẫn nhận, không còn tác dụng (luôn gọi Keycloak). Endpoint/DTO/`ApiCode` không đổi.

### Thiết kế
- Cookie = `WebAuthCrypto.seal(json{rt, remember, loginAt}, "session-cookie")`, ~1,2–1,5KB (<4KB). Max-Age: remember ⇒ `loginAt + 10h − now`; không ⇒ session cookie.
- Refresh: mở cookie (lỗi ⇒ 401 + xóa cookie) → quá `loginAt + Auth.SESSION_SECONDS` ⇒ 401 → Keycloak refresh → kiểm `sub` + account → cookie mới + access token.
- Gộp refresh: class mới `AuthRefreshCoalescer` (`service/account/impl`): `ConcurrentHashMap<hash(cookie), CompletableFuture>`; request đến sau chờ ≤15s; kết quả thành công giữ 30s rồi xóa; 503 ⇒ xóa ngay để retry được. `toString` redacted, không log token.
- Logout: mở cookie → `keycloak.logout(rt)`; cookie hỏng vẫn xóa cookie, trả 200.
- Bỏ: `WebAuthSession`, `WebAuthSessionRepository`, `SessionState` + 6 method session của `WebAuthStateStore`, `Auth.IDLE_SECONDS`. `revokeAccountAuth` chỉ còn xóa challenge; cleanup job chỉ còn challenge/rate.
- V5: `DROP TABLE web_auth_sessions; DROP INDEX idx_web_auth_challenges_lookup;`. Không sửa V4.

### Acceptance criteria (thay AC-9–11 của đề xuất cũ)
- AC-9 (AUTH-07): Given login thành công, when refresh, then nhận access token mới + `Set-Cookie` mã hóa mới; DB không có dòng phiên nào.
- AC-10 (AUTH-07): Given cookie bị sửa, mã hóa bằng khóa khác hoặc quá 10h từ login, when refresh, then 401 và cookie bị xóa.
- AC-11: Given 2 refresh đồng thời cùng cookie, when chạy, then Keycloak bị gọi 1 lần và cả 2 nhận cùng kết quả.
- AC-12: Given Keycloak tạm lỗi, when refresh, then 503, cookie giữ nguyên, retry sau thành công.
- AC-13 (AUTH-07): Given account bị INACTIVE, when refresh, then 403.
- AC-14: Given remember bật/tắt, when login và refresh, then Max-Age cookie đúng contract hiện tại.
- AC-15: Given DB V4 có dữ liệu, when V5 chạy, then `web_auth_sessions` và index lookup biến mất; `web_auth_challenges`, `web_auth_rate_limits`, `user_accounts` giữ nguyên dữ liệu.

### Phase con (tách vì >8 file)
**4a — Phiên stateless**
1. V5; xóa entity/repo session; sửa `WebAuthStateStore(+Impl)`, `WebAuthServiceImpl`, `WebAuthController`, `Constants`; thêm `AuthRefreshCoalescer`.
2. Test: `WebAuthServiceImplTest`, `WebAuthStateStoreIntegrationTest`, `WebAuthSchemaIntegrationTest`, test mới cho coalescer; `@DisplayName("AUTH-07: …")` cho AC-9–15.
3. Docs: ADR-0015, ghi chú ADR-0013, `AUTH_CONTRACT.md`, `openapi.yaml` (Set-Cookie refresh), `DATA_DICTIONARY.md`, `CROSS_REPO_MAP.md`, `modules/identity-access.md`.
4. `node scripts/check-ai-layer.mjs`; `node scripts/verify.mjs` cần `VERIFY PASS full`, skip 0. Docker/Testcontainers skip ≠ đã verify migration. Dừng chờ review.

**4b — Đổi tên bảng + entity** (chỉ đổi tên, không đổi hành vi)
1. V6: `web_auth_challenges` ⇒ `password_tokens`, `web_auth_rate_limits` ⇒ `otp_send_limits` (`ALTER TABLE … RENAME`, đổi tên index còn lại theo bảng mới).
2. `WebAuthChallenge` ⇒ `PasswordToken`, `WebAuthChallengeRepository` ⇒ `PasswordTokenRepository`, `WebAuthChallengePurpose` ⇒ `PasswordTokenPurpose` (giá trị `OTP/FIRST_LOGIN/RESET` giữ nguyên), `ChallengeState`/`saveChallenge`… ⇒ `PasswordTokenState`/`savePasswordToken`…; `WebAuthRateLimit(+Repository)` ⇒ `OtpSendLimit(+Repository)`.
3. Test schema: dữ liệu V4/V5 còn nguyên sau V6. Docs: `DATA_DICTIONARY.md`. Verify như 4a; dừng chờ review.

**4c — Đổi tên class + cấu hình** (chỉ đổi tên, không đổi hành vi)
1. Class: `WebAuthController` ⇒ `AuthController`, `WebAuthService(+Impl)` ⇒ `AuthService(+Impl)`, `WebAuthRequests/Responses` ⇒ `AuthRequests/Responses`, `WebAuthCrypto` ⇒ `AuthCrypto`, `WebAuthProperties` ⇒ `AuthProperties`, `WebAuthCleanupConfig` ⇒ `AuthCleanupConfig`, `WebAuthStateStore(+Impl)` ⇒ `AuthStateStore(+Impl)`, `WebAuthClient`/`KeycloakWebAuthClient` ⇒ `AuthGatewayClient`/`KeycloakAuthGatewayClient` (khớp client Keycloak `carenest-auth-gateway`); test đổi tên theo. Không trùng class hiện có (chỉ có nested `Constants.Auth`).
2. Cấu hình (`application.yml:31-37`): `carenest.web-auth.*` ⇒ `carenest.auth.*`; env `CARENEST_WEB_AUTH_ENABLED/ENCRYPTION_KEY/SECURE_COOKIES` ⇒ `CARENEST_AUTH_ENABLED/ENCRYPTION_KEY/SECURE_COOKIES` trong `application.yml`, `.env.example:51-58`, `compose.auth.yaml:16-20`. Giữ `CARENEST_AUTH_WEB_ORIGIN`, `KEYCLOAK_AUTH_*`, cookie `CARENEST_SESSION`, path `/auth/**`. Không alias env cũ (chỉ dev đang dùng).
3. Docs sống (`AUTH_CONTRACT.md`, `modules/identity-access.md`, `CROSS_REPO_MAP.md`, README/setup có env) cập nhật tên; ADR-0013 và Progress log cũ giữ nguyên (lịch sử). Grep `WebAuth|web-auth|WEB_AUTH|web_auth` còn 0 ngoài migration V4/V5, ADR cũ, log.
4. Verify như 4a; dừng chờ review.

### Rủi ro & rollback
- Logout 1 lần khi deploy (đã chấp nhận); đổi khóa `WebAuthCrypto` sau này cũng logout toàn bộ.
- Gộp refresh trong bộ nhớ chỉ đúng với 1 instance; chạy nhiều instance cần sticky session hoặc store chung — ghi trong ADR-0015.
- Không còn danh sách phiên theo user trong DB; "đăng xuất mọi thiết bị" dùng Keycloak `logoutUser`.
- Đổi env ở 4c: ai dùng `.env` cũ phải đổi tên biến, nếu không gateway tắt (`enabled` mặc định false) — ghi rõ khi báo xong.
- Rollback: code về bản trước + migration mới tạo lại `web_auth_sessions`/đổi tên ngược; không sửa ngược V5/V6.
- Login/refresh trên browser thật và migration trên PostgreSQL thật là kiểm tra riêng, ngoài unit test.

### Câu hỏi mở
- [x] Tên bảng: `password_tokens`, `otp_send_limits`; phạm vi đổi tên: bảng + class + cấu hình (user chốt 2026-10-13).

### Phase 4 review gate
- Đề xuất ghi 2026-10-13 sau brainstorm với user. Câu hỏi mở đã hết. **Duyệt: user duyệt trong chat 2026-10-13; bắt đầu 4a.**
- Dừng trước khi code theo `.ai/workflows/plan-change.md`.

### 2026-10-13 — Refactor theo ADR-0014 (ngoài plan này)
- Khác plan: service account/WebAuth tách interface + `impl/*Impl`; `WebAuthKeycloakClient` ⇒ `WebAuthClient` + `KeycloakWebAuthClient`; enum/hằng auth về `utils/`. Chi tiết: `docs/plans/active/2026-10-13-service-interface-impl.md`. Không đổi hành vi/API.

### 2026-10-13 — Phase 4 approved; 4a started
- User duyệt Phase 4 (4a–4c) trong chat và yêu cầu làm 4a. Commit: none.

### 2026-10-13 — 4a verified, awaiting review
- Xong 4a: V5 xóa `web_auth_sessions` + `idx_web_auth_challenges_lookup`; cookie `CARENEST_SESSION` = `seal("s1|loginAt|remember|refreshToken", "session-cookie")`; `AuthRefreshCoalescer` gộp refresh; refresh 200 trả `Set-Cookie` mới, 401 xóa cookie, 503 giữ cookie; logout cookie hỏng vẫn 200. Xóa `WebAuthSession(+Repository)`, `SessionState`, method session của state store, `Auth.IDLE_SECONDS`.
- Test: AC-9–15 có `@DisplayName("AUTH-07: …")` trong `WebAuthServiceImplTest`, `AuthRefreshCoalescerTest`, `WebAuthControllerTest`, `WebAuthSchemaIntegrationTest` (migrate schema riêng tới V4, chèn dữ liệu, chạy V5, kiểm dữ liệu còn). Docker chạy, Testcontainers không skip.
- Verify: `check-ai-layer` 0 lỗi; `VERIFY PASS full | tests 204 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`.
- Khác plan: không sửa `docs/api/openapi.yaml` — snapshot sinh từ code, bề mặt OpenAPI không đổi (`Set-Cookie` ghi trong `AUTH_CONTRACT.md`). Bỏ kiểm `sub` cũ ↔ mới khi refresh vì cookie là AEAD, không bị đổi chủ.
- Chưa kiểm: login/refresh trên browser thật với Keycloak thật. Commit: none. Dừng chờ review trước 4b.

### 2026-10-13 — 4b done, awaiting review
- User yêu cầu làm 4b. V6 đổi `web_auth_challenges` ⇒ `password_tokens`, `web_auth_rate_limits` ⇒ `otp_send_limits` kèm PK/FK/CHECK/index (không còn tên `web_auth` trong schema public). Class: `PasswordToken(+Repository)`, `PasswordTokenPurpose`, `OtpSendLimit(+Repository)`, `PasswordTokenState`, `savePasswordToken`, `Auth.SETUP_TOKEN_SECONDS`. Giá trị purpose, API, extension Keycloak không đổi.
- Test schema: migrate V4 → chèn dữ liệu → V5+V6 → dữ liệu còn ở bảng mới; schema hiện tại không còn index/constraint `web_auth`. Docs: `DATA_DICTIONARY.md`, ADR-0015.
- Commit: none. Dừng chờ review trước 4c.

### 2026-10-13 — 4c done, awaiting review
- User yêu cầu làm 4c. Class `WebAuth*` ⇒ `Auth*` (`AuthController`, `AuthService(+Impl)`, `AuthStateStore(+Impl)`, `AuthRequests/Responses`, `AuthCrypto`, `AuthProperties`, `AuthCleanupConfig`), `WebAuthClient`/`KeycloakWebAuthClient` ⇒ `AuthGatewayClient`/`KeycloakAuthGatewayClient`; test đổi tên theo. Log tag `[webAuth*]` ⇒ `[authGateway*]`.
- Cấu hình `carenest.web-auth.*` ⇒ `carenest.auth.*`; env `CARENEST_WEB_AUTH_{ENABLED,ENCRYPTION_KEY,SECURE_COOKIES}` ⇒ `CARENEST_AUTH_*` trong `application.yml`, `.env.example`, `compose.auth.yaml`, `AUTH_CONTRACT.md`, `TROUBLESHOOTING.md`, `docker/keycloak/auth/README.md`. `.env` cá nhân không sửa (file riêng của dev).
- Khác plan: đổi thêm `WebPasswordController` ⇒ `PasswordController` (cùng tiền tố Web). OpenAPI snapshot regenerate: chỉ đổi tag `web-auth-controller`/`web-password-controller` ⇒ `auth-controller`/`password-controller`; path/schema không đổi; FE/APP không dùng tag (grep `CareNest_FE/src`, `CareNest_APP/src` = 0).
- Giữ: incident ID/ADR/Progress log cũ, `CARENEST_AUTH_WEB_ORIGIN`, `KEYCLOAK_AUTH_*`, cookie, `/auth/**`. Commit: none.

### 2026-10-13 — Phase 4 done
- User review và xác nhận Phase 4 (4a–4c) xong trong chat. Verify cuối: `VERIFY PASS full | tests 204 fail 0 err 0 skip 0`.
- Còn mở ngoài Phase 4: browser review Phase 3 với Keycloak thật (gồm login/refresh/logout sau đổi env `CARENEST_AUTH_*`). Plan giữ `in-progress`. Commit: none.
