# Domain Model

Tổng quan cho task L3/L4. Task trong 1 module: đọc `docs/modules/<module>.md` thay vì file này.

## Bản đồ domain

```text
 PLATFORM      common/utils · audit · notification · ai-assistance
 FOUNDATION    identity-access → school-structure → child
 OPERATIONS    attendance (leave, attendance, meal participation)
               nutrition (food, dish, menu, meal count, food quantity, kitchen view)
               facility-issue
 CHILD RECORD  health
               learning-observation (activity context, observation, assessment,
                                     follow-up, summary, development profile)
 READ          reporting (chỉ đọc)
```

## Entity theo module

Scope cột: dữ liệu thuộc cấp nào để áp access scope.

| Module | Entity / aggregate | Scope | Ghi chú |
| --- | --- | --- | --- |
| identity-access | UserAccount, Role, Permission | SYSTEM | Không chứa assignment |
| school-structure | School, Campus, SchoolYear, Classroom | SCHOOL/CAMPUS | 1 School duy nhất |
| school-structure | StaffAssignment (userId, scopeType, scopeId, from, to) | — | Nguồn scope nhân sự |
| child | Child, Enrollment (childId, classroomId, from, to) | CLASS | Campus suy từ classroom |
| child | GuardianChildLink (childId, userId, relation) | CHILD | Nguồn scope phụ huynh |
| child | ChildAllergy / basic info | CHILD | Nguồn khai báo PENDING P-17 |
| attendance | LeaveRequest (childId, from, to, status) | CHILD | Người tạo/duyệt PENDING |
| attendance | AttendanceRecord (childId, date, status, arrivalTime?) | CLASS | 1 bản ghi/trẻ/ngày |
| attendance | MealParticipation (childId, date, meal, participating) | CLASS | Nhập cùng điểm danh |
| nutrition | Food, NutrientValue (source, sourceVersion) | SCHOOL | Không do AI tạo |
| nutrition | Dish, Recipe (định lượng/suất), ingredient type Fresh/Stored | SCHOOL | |
| nutrition | MealPlan (date, meal, ageGroup?, campus?, status, source) | SCHOOL/CAMPUS (P-07) | DRAFT → APPROVED |
| nutrition | MealCount (campus, date, meal, count, status, confirmedBy) | CAMPUS | chờ xác nhận → CONFIRMED (không sửa ngầm; đổi ⇒ xác nhận lại) |
| nutrition | MealConfirmation (lịch sử xác nhận/thay đổi số suất) | CAMPUS | Chi tiết PENDING P-04 |
| nutrition | FoodQuantityPlan (date, campus, lines) | CAMPUS | Dẫn xuất |
| facility-issue | FacilityReport (campus, location, type, status, history) | CAMPUS/CLASS | |
| health | HealthRecord (childId, date, height, weight, status, note) | CHILD | |
| health | HealthInterpretation (childId, period, text, source, status) | CHILD | AI DRAFT → APPROVED |
| learning-observation | ObservationCriteria, CriteriaOption | SCHOOL | Cấu hình |
| learning-observation | Observation (childId, date, values[], note) | CLASS | |
| learning-observation | ActivityContext (classroom/ageGroup, date, description, objective, source) | CLASS | Nguồn OPEN (ADR-0006) |
| learning-observation | ActivityParticipation, Assessment, FollowUp | CHILD | Biểu mẫu PENDING |
| learning-observation | Summary (childId/classId, period, text, source, status) | CLASS/CHILD | DRAFT → APPROVED |
| learning-observation | DevelopmentProfile | CHILD | Read model, không lưu bản sao nguồn |
| notification | Notification, DeliveryStatus | user | Không chứa dữ liệu nhạy cảm vượt mức |
| audit | AuditEvent | SYSTEM | Append-only |

## Quan hệ chính

```text
School 1─* Campus 1─* Classroom *─1 SchoolYear
Classroom 1─* Enrollment *─1 Child 1─* GuardianChildLink *─1 UserAccount(Parent)
UserAccount 1─* StaffAssignment ─→ School | Campus | Classroom
Child 1─* AttendanceRecord, MealParticipation, LeaveRequest
MealParticipation ──(tổng hợp)──→ MealCount ──×Recipe──→ FoodQuantityPlan
MealPlan *─* Dish 1─* Recipe line ─→ Food 1─* NutrientValue
Child 1─* HealthRecord, Observation, Assessment, FollowUp, Summary
Child ──(read model)──→ DevelopmentProfile
```

Tham chiếu xuyên module bằng **ID**; không dùng JPA relationship xuyên module (xem `docs/architecture/PACKAGE_STRUCTURE.md`).
