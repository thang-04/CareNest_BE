---
id: BUG-261103-school-holiday-lifecycle
type: bug
modules: [school-structure, attendance]
rules: [SCH-05, CFG-YR-02]
status: fixed
date: 2026-11-03
keywords: [holidayCache, year bounds, delete, isSchoolDay]
similar_to: []
---
# Holiday bounds and cache do not follow year lifecycle

## Reproduced
Synthetic PostgreSQL at BE 7c90f0a: place holiday 22–23/05/2028 in planned year ending 31/05; update year end to 15/05. Update succeeds instead of rejecting the now-outside holiday. Separate case: place Monday 06/09/2027 holiday, load isSchoolDay=false, delete planned year; database holiday count is zero but isSchoolDay remains false. Both assertions fail. Log target/deep-flow-review.log, archived .git/DeepFlowAuditTest.java. Audit tests use rollback transactions; no production data touched.

## Root cause
SchoolYearServiceImpl.updateSchoolYear:161–172 checks semester bounds but not existing holidays. deleteSchoolYear:236 does not clear holidayCache. replaceHolidays:459 clears cache before transaction commit; a concurrent reader may repopulate it from old committed data. That concurrent variant is source-supported, not runtime-tested here.

## Proposed repair
Validate existing holiday ranges before changing year bounds. Remove the small shared cache or design committed-state invalidation for replacement/deletion/rollback; after-commit invalidation alone needs care for in-flight stale loaders. Test shrink-year rejection, delete after cache load and concurrent replacement/read. No fix in this review.

## Repair and validation — 04/11/2026
SchoolYearServiceImpl rejects bounds excluding stored holidays using HOLIDAY_OUT_OF_YEAR. Removed volatile holiday cache; isSchoolDay queries committed database holiday existence, so year deletion/replacement cannot leave cached stale results. FlowConsistencyIntegrationTest verifies shrinking rejection and deletion after an earlier holiday read. Concurrent replacement/read is no longer cache-dependent; that exact schedule was not separately browser-tested.

Targeted final regression: 5 tests pass, no failures/errors/skips. Full verification: 1001 tests, zero failures/errors/skips; archunit, API snapshot and spotless PASS. Read-only independent review found no remaining P1/P2 in these repairs.

## Attempts
The first full verify ran 1001 tests with one failure: the older SchoolYearServiceImplTest still stubbed findAll() for the removed cache path. Updated its fixture to stub the authoritative existence query and added a second read after holiday removal. The focused unit class then passed all 30 tests, zero errors/skips. The final full run is recorded above; the first log is preserved at target/flow-consistency-full-first.log.
