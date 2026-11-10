---
id: BUG-261102-assessment-history-scope
type: bug
modules: [observation, child]
rules: [OBS-03, OBS-07, OBS-20]
status: partial
date: 2026-11-02
keywords: [transfer, withdrawn, EvaluationSources, DEVELOPMENT_CHILD_NO_CLASS, monthly, history]
similar_to: [BUG-261102-review-regressions]
---
# Assessment history selection after transfer or withdrawal

## Symptom and root cause
EvaluationSources selects records by child/date across classes while projecting against one class's approved lesson. A class-specific evaluation can therefore count/show another class's data after transfer. Development profile resolves only enrollment at the range endpoints; a child enrolled solely inside that range is wrongly rejected.

## Attempts and evidence
Read-only audit ran two synthetic Spring/PostgreSQL scenarios against current source: class A attendance 15 September, class B attendance 21 September after transfer => A evaluation summary returned 2 days instead of 1. A child enrolled 10–20 September with data on the 15th => development query 1–30 September threw DEVELOPMENT_CHILD_NO_CLASS. Audit result: 2 tests, 1 failure, 1 error, 0 skipped. Log target/flow-audit.log. Temporary audit fixture and compiled classes removed; product source unchanged.

## Related confirmed gaps
Current teacher profile links to old confirmed evaluations, but direct detail access still checks the original class teacher (new user requirement is read-only child history). BehaviourTicket summary uses today's roster for historical months and ticket board uses end-of-period roster, losing visible historical members after departure. These need dedicated regression tests and scoped fixes, not relaxed class authorization.

## Fix direction
Separate class-scoped aggregation from the child's longitudinal timeline. Resolve source class/enrollment per date, preserve original records, support enrollment overlap for profile history and historical ticket rosters, and grant only read access to confirmed historical results through the currently authorized child. No fix applied in this audit.

## Recheck 2026-11-03
At bbe4895, repeated both scenarios with a temporary copy of the existing transactional PostgreSQL fixture. SQL count assertions after flush/clear pass: both transferred-child attendance records remain stored; withdrawn-child attendance remains stored. Class A still counts 2 instead of 1, and the withdrawn profile still raises DEVELOPMENT_CHILD_NO_CLASS. This distinguishes retained source rows from incorrect aggregation/access. Batch log target/flow-data-audit-20261103.log: 44 tests, 1 failure, 1 error, 0 skipped; the other 42 tests pass. Synthetic Testcontainers database only; no product database changes. Fixture is removed after audit.

## Fix 2026-11-03 (uncommitted, branch feature/G94-236-BE-FEAT-29-daily-attendance)
- EvaluationSources.load is class-scoped: attendance via AttendanceService.findClassDays(cls), observations/activity results/health only for that classroom; flagsOf keeps flags of the evaluation's classroom only.
- Development profile resolves every enrollment overlapping the range (EnrollmentService.findChildStays) and uses the latest one; data loads as a cross-class timeline (EvaluationSources.loadTimeline). Permission still follows that latest class (unchanged; waits for business decision on history access).
- Class overview and behaviour-ticket summary/overview use the roster enrolled during the period and class-scoped records.
- Regression: AssessmentHistoryIntegrationTest (4 tests, all fail at bbe4895, pass after).
Still open: old-class evaluation detail 403 for the new teacher, development list only shows children enrolled today, ticket eligibility counts presence in any class, quality report formulas (no business decision yet).
