---
id: BUG-261102-review-regressions
type: bug
modules: [attendance, observation]
rules: [ATT-04, ATT-09]
status: fixed
date: 2026-11-02
keywords: [leave, audit, concurrency, transfer, lineage]
similar_to: []
---
# Review regressions — 02/11/2026

Status: fixed in working tree; not committed.

Cancellation previously cascaded attendance history deletion; V52 decouples immutable history snapshots. Active parent leave now rejects teacher writes with ApiCode 4027, including after classroom reopening. Cancellation only affects days before their cutoff.

Transferred children previously collided with global child-period evaluation uniqueness. V53 permits class-specific records while preserving historical names and filtering current program totals. Concurrent first daily saves now serialize through database class locks. EvaluationSources uses the same lineage projection as daily reads.

Evidence: `LeaveRequestIntegrationTest`, `DailyAssessmentIntegrationTest`, `ConcurrentDailyAssessmentIntegrationTest`, `AttendanceAuditMigrationTest`; full verify 729 tests, zero failure/error/skip. Focused compile initially exposed test helper/method naming mismatches; corrected to actual interfaces before running tests. Original pre-fix binaries were not rerun for a separate RED capture.

Web fixes and verification: `CareNest_FE/docs/quality/2026-11-02-review-regressions.md`. Mobile must handle conflict 4027; no mobile code edited. Runtime development server must restart to load migrations and compiled changes.

## Follow-up: monthly roster loses transferred/withdrawn children

User authorized all four pre-PR fixes. QualityReportServiceImpl and monthly ChildEvaluationServiceImpl drafted only children enrolled on the period end date. Both now select enrollment overlap with the month, clamped to the school year/today. Annual evaluation still uses current roster. Historical child lookup remains authorized by class and sorted by name/id.

RED: firstMonthlyListingIncludesChildWhoTransferredBeforeMonthEnd and monthlyQualityCountsChildrenWhoLeftAndJoinedDuringMonth both failed against original code (target/pre-pr-red.log). Coverage includes first list only after transfer, both class histories, repeated list, forbidden other teacher access, children leaving/joining in the month, counts and VP scope. First full compile encountered a local variable name collision with the existing age variable; renamed date variable rosterFrom. Full post-fix verify recorded in the plan Progress log.
