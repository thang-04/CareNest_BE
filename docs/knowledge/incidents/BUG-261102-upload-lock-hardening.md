---
id: BUG-261102-upload-lock-hardening
type: bug
modules: [education-plan, observation, attendance]
rules: [EDU-46]
status: fixed
date: 2026-11-02
keywords: [HTML_CELL, zip, deadlock, class lock]
similar_to: []
---

# Upload resource use and class lock ordering

## Symptom
Malformed HTML cell markup can repeatedly scan suffixes. ZIP readers previously bounded only the selected entry and returned before inspecting trailing entries. Multi-class evaluation acquired locks in display order while leave requests used ascending IDs.

## Attempts
| # | Approach | Result | Lesson |
| --- | --- | --- | --- |
| 1 | Run malicious HTML/ZIP regression cases against original parser | Failed; saved in ignored target/upload-hardening-red.log | Validate skipped and trailing entries too |
| 2 | Forward tag scanner, bounded ZIP reads and sorted locks | 24 targeted tests passed, including PostgreSQL concurrent evaluation/leave scenario | All callers need the same lock order |

## Root cause
GoalFileGrid used body regex matching and repeated string concatenation; zipEntry returned early. ChildEvaluationServiceImpl iterated visible classes by presentation order.

## Fix
Use forward tag spans and joining; reject malformed/nested target blocks. Limit each uncompressed entry to 8 MiB, archive reads to 32 MiB and 256 entries, including ignored/trailing entries. Reject duplicate requested entries. Sort class IDs before ensureDrafts. Keep API, error codes and reward/attendance formulas unchanged.

## Regression test
GoalFileGridHardeningTest covers malformed HTML, spans, text and archive budgets. EvaluationLockOrderTest verifies ascending IDs. ConcurrentDailyAssessmentIntegrationTest races year-end draft generation against a leave request across two classes whose names sort opposite to IDs.

## Impact
No migration or data repair; FE/APP contracts remain unchanged. These are resource protections, not new business eligibility rules.
