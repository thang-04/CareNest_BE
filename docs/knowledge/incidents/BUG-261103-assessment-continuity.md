---
id: BUG-261103-assessment-continuity
type: bug
modules: [observation]
rules: [OBS-03, OBS-07, OBS-10]
status: fixed
date: 2026-11-03
keywords: [transfer, timeline, confirmed history, class flags, historical ticket board]
similar_to: [BUG-261102-assessment-history-scope, BUG-261103-attendance-summary-history]
---

# Preserve assessment continuity without merging class statistics

## Symptom / root cause

Longitudinal results used only the latest class's approved lessons and meal-session configuration. Evaluation lists grouped flags by child despite accepting multiple classes. Confirmed detail authorization did not recognize the currently assigned receiving teacher. Ticket board membership was period-end only, whereas historical summaries had already moved to overlap enrollment.

## Attempts

| # | Approach | Result | Lesson |
| --- | --- | --- | --- |
| 1 | PostgreSQL continuity regressions against prior code | 4 tests: 2 assertion failures, 1 access error, 1 passed; target/continuity-red.log | Existing passing tests did not cover same-child dual-class rows or receiving-teacher history |
| 2 | Original-class lesson projection, child/class flag key, confirmed-only child-history read, historical board | 20 focused tests passed; source/DTO additions covered by updated OpenAPI snapshot | Keep mutation policy independent from read access |
| 3 | Independent review with different meal sessions across years | Found dropped nursery MORNING totals; expanded timeline regression passed after union of source sessions | Day display and aggregate summary require the same historical source |
| 4 | Execute documented manual SQL fixture through real psql | Commit round-trip and invalid-pair rollback pass | psql quit arguments do not reliably signal failure; use ON_ERROR_STOP and a controlled SQL exception |

## Fix

Project activity goals per result classroom/date, preserve source class labels and historical meal sessions. Keep class keys in flag counts. Receiving teacher reads only confirmed old evaluations while currently assigned to the child; no edit/draft grant. Historical ticket rows use overlap enrollment; issuance retains period-end membership. Eligibility/weekly evidence and duplicate prevention follow the child globally, while class summaries count only class-source flags. FE labels old class/read-only state, suppresses wrong-class day links and blocks history-only/issued rows from selection.

## Contract / consumers

Additive fields: EvaluationDay.classId/className; TicketBoardRow.canIssue/issuedElsewhere. Existing fields, endpoints and error codes unchanged; snapshot updated intentionally. FE DailyDaysTable and GoodBehaviourTicketsPage consume optional additions with legacy/demo fallbacks. App is not modified. No migrations or dependencies.

## Regression tests

AssessmentContinuityIntegrationTest: confirmed-only receiving-teacher access, mutation denial/revocation/unrelated teacher denial, separate class flags, historical board, cross-class weekly evidence, membership restriction and global duplicate prevention. EvaluationTimelineSourcesTest: goals and meal sessions retain original class. AssessmentManualFixtureTest: real psql fixture commit/read-back and invalid-input rollback. FE assessment-history-view and ticket-history-view component tests validate source labels, links, actions and cross-class issued labels.

## Limits

Ordinary transfer/withdrawal feature and monthly quality formulas remain unchanged and outside this fix. Existing broader audit questions about formulas, rewards/profile integration and periodic assessment types are not declared resolved here.
