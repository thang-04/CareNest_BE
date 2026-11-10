---
title: Preserve child assessment continuity across classes
status: done
owner: DinhCan
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [observation]
rules: [OBS-03, OBS-07, OBS-10]
risk: high
created: 2026-11-03
updated: 2026-11-03
---
# Scope approved by user

User approved the proposed BE/FE repairs and requested screen-by-screen testing instructions. Preserve external uncommitted repairs. Do not implement ordinary transfer/withdrawal or change quality-report formulas.

## Acceptance

- AC1: A child's longitudinal history retains activity goals using each result's original class/date lesson; class-specific evaluations retain only that class's data.
- AC2: Per-class evaluation rows and ticket-summary flags cannot combine another class's flags. A child's weekly ticket eligibility still follows their whole week across transfer, as explicitly requested; one ticket per child/type/period remains unchanged.
- AC3: Historical boards include children enrolled during the period and retain issued tickets. Cross-class issued tickets must not be shown as issued by the selected class, but must prevent duplicates. Preserve the existing eligibility thresholds.
- AC4: Current receiving teacher may read a child's confirmed old assessments, not old drafts or mutation endpoints; unrelated and revoked teachers remain denied. Leaders retain their existing scope.
- AC5: FE identifies historical day class and read-only confirmed history, and avoids linking old-class days to the current class's daily-edit page.
- AC6: Focused regressions, full verification both repos, independent read-only review, and manual GV/PHT/HT test guide with synthetic fixture prerequisites.

## Decisions / impact

No new endpoints, migrations or dependencies. Add nullable classId/className to EvaluationDay so FE can label source; existing fields/consumers remain compatible. FE DailyDaysTable consumes these optional fields; App is not changed. Keep global weekly attendance/flag eligibility and duplicate prevention distinct from class report statistics. Source references: EvaluationSources loadTimeline; ChildEvaluationServiceImpl get/flagsOf; BehaviourTicketServiceImpl childrenOf/boardRows/flagCounts.

## Progress log

- 03/11/2026: User approved the preceding concrete repair scope with “làm đi”; plan recorded before edits. Baseline includes external uncommitted history repairs; previous targeted run passed 62 BE and 192 FE tests. Remaining gaps are documented in FE docs/quality/2026-11-03-current-working-tree-review.md.
- Implemented original-class projection, class flag isolation, confirmed-only receiving-teacher reads and historical ticket board. Issuance still requires period-end membership; cross-class evidence/duplicate checks remain global as approved. Optional TicketBoardRow.canIssue/issuedElsewhere distinguish history-only and other-class issuance.
- Focused 20 tests passed; additional original-meal regression and real psql commit/invalid-parameter rollback fixture passed. Read-only peer review found the historical MORNING omission; fixed and covered. OpenAPI snapshot updated with additive fields only. Full final verification in progress; manual guide and fixture prepared.

- Final verification: node scripts/verify.mjs PASS, 750 tests, 0 failures/errors/skips; ArchUnit, OpenAPI snapshot, Spotless passed. Peer review issue resolved and tested. FE full PASS 499 tests; Chrome source-label/link smoke passed. No commit/push performed in this implementation turn.
