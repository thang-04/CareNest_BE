---
id: BUG-261104-concurrent-main-teacher-assignment
type: bug
modules: [school-structure, identity-access]
rules: [AUTH-24]
status: fixed
date: 2026-11-04
keywords: [teacher assignment, concurrent, one main class, check then insert]
similar_to: []
---
# Concurrent assignment can bypass the one-main-class check

## Evidence
BE 29e1e5a: TeacherAssignmentServiceImpl loads the class and current assignments without a write lock (100–109), checks other classes at 144 and inserts at 155. TeacherClassRules.checkOneMainClass (61–86) reads other class assignments; no shared lock protects the same teacher/year across the class-assignment and generic staff-assignment writers. StaffAssignmentServiceImpl also calls the same check. Existing DB indexes do not enforce this cross-class condition.

## Trigger / impact
Two permitted users concurrently assign one currently unassigned teacher to two different classes of the same year. Both transactions can see no existing assignment and both insert. Two first assignments to one empty planned class can similarly accumulate incompatible requested teacher sets. This is a source-confirmed interleaving, not a runtime concurrency reproduction in this review.

## Proposal / tests
Use the same stable teacher/year lock across every writer, plus class locking for replacement/count constraints; acquire multiple locks in a consistent order. Test two actual transactions: same teacher/two classes and two replacements/same class. Preserve existing history and substitute semantics. No product changes in this review.

## Fix
TeacherClassRules.lockForAssignment khóa advisory theo lớp rồi từng GV + năm học (thứ tự id tăng dần) trước khi đọc phân công; gọi ở TeacherAssignmentServiceImpl.assignClassTeachers (trước khi đọc GV hiện tại) và StaffAssignmentServiceImpl.checkTeacherAssignment. Test: TeacherAssignmentConcurrencyIntegrationTest (hai lớp cùng lúc xếp một GV ⇒ một OK, một TEACHER_ALREADY_ASSIGNED).
