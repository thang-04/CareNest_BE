# Validation — 012
## Analyze before code — 2026-11-05
FR-1..5 trace to Domain AC-1..8 and T003..T009. User approved optional photos and all design choices. Stable guardian routes do not change the account route meaning. Read scope/no-store, nullable migration and account → child direction are explicit. Existing missing profile GET is recorded in plan.md. BE profile PUT is absent and is not added as a separate feature. No business blocker remains.

## Commands and scenarios
BE/FE: node scripts/verify.mjs; node scripts/check-ai-layer.mjs. Targeted tests cover file signatures/limits/duplicate parts, scope, rollback, JSON compatibility, unactivated guardian/pickup fallback, FE selection/state/mapping/blob cleanup. Browser 1440/375 uses synthetic images: select/replace/remove/steps/save/reopen/pickup, invalid-file feedback.

## Results
Implementation and validation complete; phase awaits user review. Existing BE AI-layer error: unrelated missing FE incident link in BUG-261102-curriculum-reimport-duplicate-headings.md:36; no pass claim for this gate while it remains.

## Evidence — 2026-11-05
- BE targeted clean build: 95/95 (ChildPhotoServiceImplTest, ChildPhotoControllerTest, ChildEnrollmentControllerTest, ChildEnrollmentIntegrationTest, PickupServiceImplTest), PostgreSQL Testcontainers with S3/Keycloak/SMS mocked. Additional review regressions (11 child-photo unit tests), enrollment integration and updated OpenAPI snapshot subsequently pass.
- FE seven new tests pass, including multipart identities/JSON compatibility/private blob URL/pickup adapter/teacher projection and preserving portraits on edit/reorder; 17 existing enrollment/pickup regression tests pass. Full FE gate passes, 799 tests, no failures/skips.
- Chromium actual app/mock 1440 and 375: invalid-size feedback, preview, remove/reselect same file, remove guardian, navigate all steps, save, profile readback and reload, no horizontal overflow or JavaScript errors. Nine object URLs created and nine revoked at each width. Synthetic canvas images only. Browser harness/results/screenshots: FE node_modules/.cache/enrollment-photos/.
- Read-only subagent review found four issues; T010..T012 cover fixes. Re-review reports no remaining blocker.
- Schema snapshot delta is intentional: multipart at enrollment with payload encoding and four optional binary parts, actual HTTP201, three read routes and nullable photoUrl fields. No query parameter named files. APP endpoints and JSON enrollment remain compatible.
- Limits: no live S3 upload and no browser-to-running-BE E2E claim. Current FE children/pickup module source remains demo; adapters are implemented and tested independently. New /profile supplies the existing child view; no new PUT profile feature or fabricated health/class-placement history.

## Converge
Round 1: review gaps traced to T010..T012 and fixed with regression coverage. Round 2: no remaining implementation gap in the approved portrait scope; final gate output and documentation completion recorded in governance plan. No empty task section or renumbering added.

## Final gates
- BE: VERIFY PASS full | tests 1073 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 516s | log target/verify.log
- FE: VERIFY PASS full | npm run verify | log verify.log; 799 tests, zero fail/skip.
- AI layer FE: 0 errors/20 existing warnings. AI layer BE: 1 pre-existing broken incident link/12 warnings; no new AI-layer error. Diff whitespace checks pass.
- T001..T012 complete. Final converge after implementation/re-review finds no remaining gap; tasks unchanged by this final clean converge. Phase awaits user review; no commit/push or deployment.

## Pre-commit documentation check
User explicitly authorized commit/push on 2026-11-05. The existing cross-repo incident link was repaired to its verified immutable FE blob at commit994002c; BE AI-layer now has 0 errors/12 warnings, FE 0 errors/20 warnings. No application source or dependency changed in this preparation.
