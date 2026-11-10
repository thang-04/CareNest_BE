---
id: BUG-261103-attendance-summary-history
type: bug
modules: [attendance, child]
rules: [ATT-11]
status: fixed
date: 2026-11-03
keywords: [transfer, withdrawn, summary, historical roster, missing child]
similar_to: [BUG-261102-assessment-history-scope]
---

# Historical attendance summary omits departed children

## Symptom and reproduction
Synthetic PostgreSQL fixture: child attends class A on 15 September, ends enrollment on 19 September and starts class B on 20 September. Request class A summary for 1–30 September. SQL count after flush/clear confirms the A attendance row remains, but response.children contains only the other current child; historical present/meal totals omit the transferred child.

## Attempts
| # | Approach | Result | Lesson |
| --- | --- | --- | --- |
| 1 | Static review AttendanceServiceImpl.summarize | Roster selected only at range end | Stored source rows and report membership are separate concerns |
| 2 | Temporary FlowDataAuditTest.auditAttendanceSummaryKeepsChildWhoLeftInsideRange on PostgreSQL | 1 assertion failure, 0 errors/skips; SQL source row assertion passed | Reproduced against bbe4895; not an infrastructure failure |

## Root cause
AttendanceServiceImpl.summarize selects findChildrenInClassroom(cls.id(), to), then reads records only for those child IDs. A child who left before to is excluded before aggregation. Conversely, current members can be counted missing on dates before joining.

## Fix direction
Use historical enrollment overlap for candidate children, and membership effective on each day for missing counts. Preserve record class/date identity, access checks and stored records. No product fix applied during this read-only review.

## Evidence
target/attendance-history-audit-20261103.log. Temporary fixture archived under ignored .git/FlowDataAuditTest.java after execution. Synthetic isolated database only; no user data changed. The test uses a database round-trip within a rollback transaction, not a production restart durability test.

## Fix 2026-11-03 (uncommitted, branch feature/G94-236-BE-FEAT-29-daily-attendance)
summarize now lists every child enrolled in the class during the range (EnrollmentService.findChildrenInClassroomDuring) and counts "missing" only on days covered by that child's stay (new EnrollmentService.findStaysInClassroom / ClassStay). Records stay class-scoped. Regression: AttendanceHistoryIntegrationTest.summaryCountsEachDayWithChildrenEnrolledThatDay (fails at bbe4895, passes after). MultiSummary.size still sums class sizes, so a child transferred inside the range is counted in both classes there.
