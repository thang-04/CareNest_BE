---
title: Repair attendance and assessment review regressions
status: in-progress
owner: DinhCan
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [attendance, observation]
rules: [ATT-04, ATT-05, ATT-09, AUTH-03]
risk: high
created: 2026-11-02
updated: 2026-11-02
---
# Review regression repairs

User authorized fixes and detailed tests of the supplied review on 02/11. User confirmed active parent leave blocks teacher edits and meals; cancellation before cutoff permits fresh attendance. No commit/push authorized in this task.

## Decisions and acceptance

- Preserve audit snapshots after cancellation; current attendance may be cleared, past teacher actions must remain. V52 adds and backfills snapshot columns before dropping the cascading record FK.
- Bound leave requests to their active school year before iterating dates; do not invent a new arbitrary maximum number of days.
- Serialize attendance, leave and daily assessment writes through class-row locks, using ClassroomService across module boundaries.
- Preserve distinct old/new class evaluation histories after transfer. V53 scopes uniqueness to child/class/period. Current program totals count only current roster; historical names use overlapping enrollment dates. Year detail only includes monthly rows within the authorized class.
- Aggregate copied goals through the existing read-only lineage projection; do not rewrite saved daily source rows on reads.
- Web scope: block active-leave edits; remove fake persisted VP grants; repair mock reports/rewards, CSV and unlock time. See FE report below. APP source unchanged: server returns new conflict 4027 on active-leave attendance; mobile must display that response.

## Progress log

- Follow-up authorized by user "sửa đi bạn": fix four pre-PR findings. Monthly roster uses enrollment overlap for quality and first monthly evaluation drafts; YEAR roster remains current. Demo tickets use period boundaries; duplicate seed identity is repaired without resetting storage, with conflicting rows archived locally. Regression tests added before fixes: FE three failures reproduced; BE RED run recorded in target/pre-pr-red.log. No API/dependency/migration changes for this follow-up.

- Focused PostgreSQL tests: 37 passed, zero skipped, including two independent concurrent request transactions, transfer MONTH/YEAR, lineage aggregation, audit preservation and authorization.
- Full `node scripts/verify.mjs`: `VERIFY PASS full | tests 729 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 452s | log target/verify.log`.
- Includes upgrade test from schema V51 with an existing teacher history row; migration backfill and deletion survival tested.
- Read-only peer review found missing principal user propagation and incorrect mock return action in FE; both corrected. Reviewer subsequently reached its usage limit; full second peer pass was unavailable.
- Tests run against isolated PostgreSQL containers, not the user's development database. Running IntelliJ instance was not restarted; changes apply on next restart.
- Existing application.yml and local SQL scripts preserved; not included in any commit.

## Remaining verification limits

Follow-up final evidence: full `node scripts/verify.mjs` after final source edits → `VERIFY PASS full | tests 731 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 427s | log target/verify.log`. DailyAssessmentIntegrationTest: 11 tests, zero failures/errors/skips. FE full 487 tests and Chrome 9/9 demo pages pass, including duplicate-key-warning checks. Both repositories' check-ai-layer commands pass with existing warnings. Four pre-PR findings resolved, no commit/push/PR.

APP/browser-to-real-BE end-to-end workflows were not executed. BE integration tests exercise real services and PostgreSQL; Web smoke checks use demo mode. No claim that unrelated flows or production deployment were fully tested. See `../knowledge/incidents/BUG-261102-review-regressions.md`.
