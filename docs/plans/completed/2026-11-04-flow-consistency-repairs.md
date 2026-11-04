---
title: Repair concurrent meal updates and flow validation
status: done
owner: DinhCan
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [organization, attendance, observation, meals]
rules: [SCH-05, OBS-26, ATT-07, NUT-03, ATT-08, HLT-15]
risk: high
created: 2026-11-04
updated: 2026-11-04
---
# User-approved repair scope

User approved fixing findings from the 03/11 deep review. Keep unrelated dirty files, credentials and staged work unchanged; no commit/push requested this turn. No formula, endpoint, DTO, dependency or migration changes.

## Acceptance and decisions

- Serialize shared meal mutations by campus/date before loading aggregates, including initial aggregation, confirm, adjust and attendance listener. All-campus reads that can aggregate acquire locks in sorted campus order. Remove classroom locks from meal-only paths to avoid inversions with shared aggregate locking. PostgreSQL transaction advisory locks use a namespace/hash and require no new infrastructure.
- Reject changed year bounds excluding existing holidays, using current error contract. Remove volatile holiday cache; query weekday/holiday existence from DB so delete/rollback/concurrent changes do not poison future reads.
- Validate class-comment month intersects class school year on get/save/confirm; preserve partial first/last-month support.
- Health retry same actor/request UUID must serialize before idempotency read. Attendance unlock creation serializes on classroom before checking active grant.
- FE refreshes authoritative attendance after parent leave changes without overwriting unrelated dirty rows; error recovery and periodic safe refresh cover submission/cancellation. Preserve partial attendance/health save semantics.
- Restore real regression fixtures from ignored audit archive, adapt race schedule to test pre-read lock behavior, add concurrency/error regressions, run full verify BE/FE and required independent read-only review. Provide results and test flow guide.

## Progress log

- 04/11: User approved concrete findings with “sửa đi”; plan recorded before product edits. Prior isolated audit reproduced meal pending collision, holiday bounds/cache and outside-year class comments. Two retry/unlock risks will receive focused coverage. Existing source HEAD BE 7c90f0a; FE 0c540dc plus staged/uncommitted unrelated draft-history changes.

- 04/11: Product repairs complete; targeted regression 5/5 BE, 8/8 FE, full BE 1001 tests, zero failures/errors/skips; archunit, API snapshot and spotless PASS, full FE 797 tests (zero skips/failures) including lint/build. Independent read-only review performed; health draft visibility finding repaired and verified in Chrome with mock adapter. No endpoint/DTO/dependency/migration/formula changes. Manual integrated acceptance steps: CareNest_FE/docs/quality/2026-11-04-flow-consistency-test.md. No commit/push performed.
