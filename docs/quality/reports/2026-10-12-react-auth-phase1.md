# React auth — Phase 1 verification

Date: 2026-10-12. Plan: `docs/plans/active/2026-10-11-react-auth-keycloak.md`.

## Delivered
- Standalone Java 21 provider pinned to Keycloak 26.8.0, private disabled gateway client, native direct-grant checkpoint, capability-bound first-login setup, protected OTP/setup email operations.
- Custom image and opt-in Compose overlay; stock Compose/realm and existing public client origins are preserved.
- Read-only bootstrap preview; narrowly owned client/flow changes only after explicit apply; unsafe native topology leaves the gateway disabled.
- No Spring auth endpoints, session/OTP database migration or React integration yet. Those remain Phase 2–3.

## Evidence
| Check | Observed result |
| --- | --- |
| Extension Maven verify + Spotless | 24 tests, 0 failures/errors/skips; BUILD SUCCESS |
| Node configuration/bootstrap tests | 8 passed, 0 failed |
| Isolated Keycloak 26.8.0 + PostgreSQL 17 + Mailpit | E2E PASS 20/20 |
| check-ai-layer | 0 errors, 0 warnings |
| BE full verify | VERIFY PASS full: 136 tests, 0 failures/errors/skips; ArchUnit/snapshot/Spotless pass (35s) |

E2E independently verifies RS256 signature/issuer/audience/role/300-second TTL, temporary-password capability only, policy rejection/replay/disabled account/native password replacement, unauthorized caller rejection, native brute-force and refresh rotation, real email delivery, controlled SMTP refusal, unchanged public client redirects/PKCE, unsafe flow rejection and disabled-feature behavior.

Logs: `target/auth-extension-verify.log`, `target/auth-review-node-green.log`, `target/auth-extension-e2e.log`, `target/verify.log`. Throwaway project `carenest-auth-e2e-309b519c` was removed after the successful run. No user volume/stack was changed; Docker Desktop was started for testing.

## Independent review and fixes
Fresh independent reviewer found a High flow-ancestry validation bug, Medium public-origin compatibility bug and a concurrency test gap. These were reproduced before fixes, then addressed with regression tests. No second independent review was run; the post-fix gate is the tests and author verification.

- Disabled/alternative parents or extra OTP conditions cannot skip validators: RED→GREEN and runtime tamper scenario.
- Compose overlay retains stock public Web origin; React email origin has its own environment variable: actual Compose-render RED→GREEN.
- Per-user capability issuance/mutation lock extends through commit/rollback: deterministic interleave RED→GREEN plus transaction completion coverage.
- Clarified that recovery password-reset capability exchange belongs to Phase 2.

## Decisions and practical limits
- Work in the current checkout without creating a branch/worktree or committing. Existing dirty user files were preserved; cost: this change must be staged separately if committed later.
- Automatic approval rejected an initial reset-by-userId primitive. The safer alternative only permits password mutation with a verified one-use capability. Recovery exchange remains for Phase 2; cost: full recovery is not delivered in Phase 1.
- SMTP outage test now uses a refused SMTP port on a resolvable test service; stopping the service caused a DNS wait beyond the test deadline. Cost: DNS outage response timing is not established by the 20-scenario run.
- The per-user lock lease is 600 seconds; Keycloak transaction timeout must remain below that value. Node crash may temporarily block operations until lease expiry.
- Direct Grant remains a documented opt-in exception, not PKCE or a production security sign-off. No real account/child data was used and the new client is disabled by default.
- FE visual/browser integration has not been performed. No FE/APP source was edited in Phase 1.

## Review gate
Phase 1 is ready for user review; final full verify is recorded in the plan log. `.ai/workflows/plan-change.md` requires stopping after each phase; do not claim full authentication integration or start Phase 2 before that review.
