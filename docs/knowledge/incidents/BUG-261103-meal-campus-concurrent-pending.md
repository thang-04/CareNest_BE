---
id: BUG-261103-meal-campus-concurrent-pending
type: bug
modules: [attendance, nutrition]
rules: [NUT-03, ATT-07]
status: fixed
date: 2026-11-03
keywords: [uk_meal_count_lines, concurrent, pending, campus, after commit]
similar_to: []
---
# Concurrent class changes collide in one campus meal count

## Reproduction / evidence
BE 7c90f0a. Synthetic PostgreSQL fixture: two classes in one campus, confirmed LUNCH with both class lines at 2; current attendance contains 1 per class. Two independent transactions lock different classes and preload the same confirmed MealCount/lines. A barrier ensures both have read before invoking the actual MealCountService.markAttendanceChanged for their respective class. One commit fails with DataIntegrityViolationException / uk_meal_count_lines. Test expected both pending values 1/1; actual audit: 1 assertion failure, 0 infrastructure errors/skips. Log target/meal-concurrency-audit.log; archived temporary fixture .git/MealConcurrencyAuditTest.java.

## Root cause / impact
MealCountServiceImpl:281 locks only classroomId, although MealCount.workingLine:148 clones every class line when first creating pending. Different classroom locks do not serialize the shared aggregate. No parent @Version. The production listener runs REQUIRES_NEW after attendance commit: failure in its transaction cannot roll attendance back, so attendance may be saved while one class's meal refresh is absent. The test executes the actual update service with controlled independent transactions, not the complete HTTP/after-commit request chain.

## Proposed repair / regression
Coordinate a common campus/date or aggregate lock before loading entity state across listener, aggregate, confirm and adjust paths. Preserve a consistent lock order to avoid introducing inversions with classroom locks. Test two classes changing concurrently after confirmation and confirmation racing with change; both pending updates must survive, confirmed kitchen numbers must remain stable until reconfirmation. No product fix applied in this review.

## Repair and validation — 04/11/2026
MealCountServiceImpl now locks the common campus/day before loading aggregates on aggregation, confirmation, adjustment and attendance-change paths. Multi-campus aggregation locks sorted campus IDs; meal-only paths no longer acquire classroom locks. Confirmed kitchen figures and pending/substitute semantics are unchanged. MealCountConcurrencyIntegrationTest proves two simultaneous classes both retain pending 1/1.

Targeted final regression: 5 tests pass, no failures/errors/skips. Full verification: 1001 tests, zero failures/errors/skips; archunit, API snapshot and spotless PASS. Read-only independent review found no remaining P1/P2 in these repairs.

## Attempts
The initial restored audit fixture preloaded a managed aggregate before entering the service; it remained stale even after adding the correct service lock. The permanent regression now places the barrier before actual service entry, with no external entity preload/classroom lock, so the service owns lock-before-read. The corrected race and the final full suite pass. Existing first audit failure remains documented above.
