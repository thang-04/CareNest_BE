# Validation — 013
## Analyze before code
User explicitly approved the presented design and implementation 2026-11-05. FR1..6 map to AC1..8 and T003..T009. Request singleton compatibility, server enforcement at every link route, no old link revocation, deferred import and stable guardian identity are explicit. Existing constitution applies. No business blocker remains.

## Validation matrix
BE unit/controller/PostgreSQL: zero/multiple/one selection, one account only, import zero identity/SMS/link side effects, existing account reuse, direct non-recipient add, stale selection and scope, transaction cleanup and old links. FE: radio/removal/reorder/photos, template/header aliases/file variants/row dropdown and validation, selected account coverage/panel, full gates. Browser desktop1440/mobile375 with synthetic fixtures. Record real results and missing live infrastructure explicitly.

## Commands
BE and FE: node scripts/verify.mjs; node scripts/check-ai-layer.mjs. Intentional snapshot update after reviewing contract delta. No commit/push unless separately authorized.

## Evidence after implementation
- FE full gate: VERIFY PASS full, 808 tests, fail0/skip0. Chromium real app/mock desktop1440/mobile375 passed form selection/one account/portrait persistence, per-row CSV selection, import zero accounts and later selected activation; no JS errors, object URLs12created/12revoked each.
- PostgreSQL targeted cases passed selected enrollment/deferred import/stale recipient/scope/CAS/batch, migration on legacy/singleton/ambiguous/sole-linked and partial unique index. Final BE gate after clean/source: VERIFY PASS full | tests1087 fail0 err0 skip0 | archunit ok | snapshot ok | spotless ok | 586s. Result is recorded in VERIFICATION.md.
- Read-only independent review findings fixed: account identity CAS across all routes, preserve selected guardianId in callbacks, account reference by persisted userId, no lost status updates, pending campus scope, profile edit selection, import batch intersection, optional header groups/aliases, legacy NOT VALID constraint upgrade and explicit audit.
- Limitations: browser used mock repositories; no live FE-to-BE/Keycloak/SMS/S3 end-to-end check. Existing children/pickup source configuration unchanged. XLSX/CSV supported in mock; XLS supported by real BE parser using existing POI. No commit/push/deploy.

## Converge
AC1..8 covered by validator/unit/PostgreSQL/migration/adapter/browser cases. Independent read-only review completed with no remaining direct blocker after corrections. Final source gates: BE1087/FE808 tests, no failures/errors/skips. AI-layer checks: BE0errors12existingwarnings, FE0errors20existingwarnings. Plans moved to completed; implementation awaits user review, no commit/push.
