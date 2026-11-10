---
id: BUG-261104-reception-concurrency
type: bug
modules: [attendance, health]
rules: [ATT-08, HLT-15]
status: fixed
date: 2026-11-04
keywords: [requestId, idempotency, unlock, concurrent]
similar_to: []
---
# Serialize health retries and attendance unlock creation

## Root cause
DailyHealthServiceImpl previously read request UUID before insertion without serialization; two simultaneous retries could both pass the lookup. AttendanceDayUnlockServiceImpl checked for an active grant before creating one without a common classroom lock, allowing duplicate grants. These were source-supported risks, not separately runtime-reproduced on the old source.

## Fix
Health requests acquire a PostgreSQL transaction advisory lock scoped by recordedBy/requestId before the idempotency lookup. Unlock creation locks the classroom before the active-grant query. Authorization and existing errors remain unchanged; no endpoint, migration or infrastructure is added. Historical duplicate grants are not deleted.

## Attempts and regression
- ConcurrentReceptionIntegrationTest runs two independent transactions per operation against synthetic PostgreSQL data. Same UUID returns the same entry ID with one persisted entry; simultaneous unlock has one winner and one UNLOCK_ALREADY_ACTIVE. Revoking the winner leaves no active new grant.
- Targeted final run: all 5 tests across this test and FlowConsistencyIntegrationTest/MealCountConcurrencyIntegrationTest pass; full verification pending below.

## Lesson
A unique key or read-before-create check alone does not provide a clean idempotent response under concurrent requests. Serialize before the authoritative read, within the transaction that writes.

## Full verification
1001 tests, zero failures/errors/skips; archunit, API snapshot and spotless PASS. Independent read-only lock-order review found no new P1/P2.
