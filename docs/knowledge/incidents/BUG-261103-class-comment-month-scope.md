---
id: BUG-261103-class-comment-month-scope
type: bug
modules: [observation]
rules: [OBS-26, CFG-YR-02]
status: fixed
date: 2026-11-03
keywords: [ClassMonthlyComment, month, year, validation]
similar_to: []
---
# Monthly class comment accepts a month outside its school year

## Reproduction
Synthetic PostgreSQL class belongs to active 2026–2027 starting September 2026. Assigned teacher calls ClassMonthlyCommentService.get with month 01/08/2026. Instead of rejecting, service creates a draft for the preceding month. Audit assertion fails (no exception), no infrastructure error/skip. target/deep-flow-review.log; archived .git/DeepFlowAuditTest.java. Source data is synthetic; product source unchanged.

## Root cause / proposal
ClassMonthlyCommentServiceImpl.get:56–72 and editable:117–140 normalize firstOf(month) and check ended, but do not validate intersection with class-year bounds. Add common validation for get/save/confirm and regression cases before/after year plus partial first/last month. A UI dropdown cannot replace BE validation. Also review first-draft concurrent insert separately; optimistic version only protects updates of existing rows. No fix applied.

## Repair and validation — 04/11/2026
Common month/year validation now applies to get/save/confirm after authorization, using existing EVALUATION_MONTH_INVALID. Partial first/last months remain accepted and clipped by existing draft logic. FlowConsistencyIntegrationTest covers all three operations outside the year and valid partial boundary months. First-draft concurrent comment insertion was not part of this fix.

Targeted final regression: 5 tests pass, no failures/errors/skips. Full verification: 1001 tests, zero failures/errors/skips; archunit, API snapshot and spotless PASS. Read-only independent review found no remaining P1/P2 in these repairs.
