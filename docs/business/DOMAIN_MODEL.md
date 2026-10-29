# Domain Model

Tổng quan cho task L3/L4. Task trong 1 module: đọc `docs/modules/<module>.md` thay vì file này.

## Bản đồ domain

```text
 PLATFORM      common/utils · audit · notification · ai-assistance
 FOUNDATION    identity-access → school-structure → child
 OPERATIONS    attendance (leave, attendance, pickup)
               nutrition (food, dish, menu, meal count, food quantity, kitchen view)
               education-plan (year goals, sub-theme, weekly theme, daily plan)
 CHILD RECORD  health
               learning-observation (daily assessment, periodic summary, reward,
                                     development profile)
 READ          reporting (chỉ đọc)
```

## Entity theo module

Scope cột: dữ liệu thuộc cấp nào để áp access scope.

| Module | Entity / aggregate | Scope | Ghi chú |
| --- | --- | --- | --- |
| identity-access | UserAccount, Role, Permission | SYSTEM | Không chứa assignment |
| school-structure | School, Campus, SchoolYear, Classroom | SCHOOL/CAMPUS | 1 School duy nhất |
| school-structure | StaffAssignment (userId, staffRole, campusId hoặc classroomId, validFrom, validTo) | — | Nguồn scope nhân sự; vai trò quyết định cột scope (PRINCIPAL: toàn trường, VP/bếp: campus, GV: lớp) |
| child | Child, Enrollment (childId, classroomId, from, to) | CLASS | Campus suy từ classroom |
| child | GuardianChildLink (childId, userId, relation) | CHILD | Nguồn scope phụ huynh |
| child | ChildAllergy (childId, allergen, mức nhẹ/nặng) / basic info | CHILD | PH khai khi nhập học, nhà trường ghi (HLT-06); danh mục chất cố định + PHT thêm |
| attendance | LeaveRequest (childId, from, to, reason, status) | CHILD | Phụ huynh gửi, không duyệt: SUBMITTED → CANCELLED (ATT-05) |
| attendance | AttendanceRecord (childId, date, status Có mặt/Vắng, excused, handedOver) | CLASS | 1 bản ghi/trẻ/ngày; vắng có phép theo đơn (ATT-09); xác nhận trả trẻ (ATT-10). Không có MealParticipation riêng (ATT-02) |
| attendance | AttendanceReopen (classroom, date, reason, by) | CLASS | PHT mở lại sau giờ chốt (ATT-08) |
| attendance | Pickup (childId, date, pickedUpBy, guardianId?, time, earlyLeave, reason) | CHILD | Xác nhận trả trẻ (ATT-10, PCK-02, PCK-04); lưu trên AttendanceRecord hay bảng riêng: quyết định khi thiết kế bảng |
| health / attendance | Kiểm tra đầu ngày (childId, date, temperature, symptoms) | CLASS | Nhập cùng màn điểm danh (HLT-07); module lưu chờ thiết kế bảng |
| nutrition | Food, NutrientValue (source, sourceVersion) | SCHOOL | Không do AI tạo; nguồn Viện Dinh dưỡng (NUT-18) |
| nutrition | Dish, Recipe (gam/trẻ theo nhóm tuổi), ingredient type Fresh/Stored | SCHOOL | NUT-18 |
| nutrition | MealPlan (tuần trong bộ 4 tuần, ageGroup nhà trẻ/mẫu giáo, status, source) | SCHOOL | Chung hai cơ sở (NUT-13); DRAFT → PUBLISHED, công bố là duyệt (NUT-08) |
| nutrition | MealCount (campus, date, meal, classroom lines, status, confirmedBy) | CAMPUS | chờ xác nhận → CONFIRMED (không sửa ngầm; đổi ⇒ xác nhận lại) |
| nutrition | MealConfirmation (lịch sử xác nhận/thay đổi số suất) | CAMPUS | NUT-02..04 |
| nutrition | FoodQuantityPlan (date, campus, lines) | CAMPUS | Dẫn xuất; sĩ số dự kiến hoặc suất đã xác nhận (NUT-07) |
| nutrition | Supplier (name, phone, address, idCard, deliveryPerson) | SCHOOL | Dùng chung hai cơ sở (NUT-12) |
| nutrition | Kho khô: phiếu nhận theo lô, bút toán trừ dự kiến + điều chỉnh | CAMPUS | ADR-0009, NUT-11 |
| nutrition | Kiểm nhận hàng tươi (2 lần), FoodSample, ảnh mẫu bữa | CAMPUS | NUT-12, NUT-16, NUT-21 |
| health | HealthRecord (childId, date, height, weight, status, note, classification, classificationReason, published) | CHILD | Mọi lần đo, sửa có lý do + lịch sử (HLT-13); phân loại WHO, GV chỉnh kèm lý do (HLT-10); công bố (HLT-11) |
| health | Đợt khám bác sĩ | CHILD | 2 lần/năm (HLT-08); nội dung ghi: chưa có rule |
| health | Ghi nhận sức khỏe trong ngày (childId, time, symptom, handling, note) | CHILD | Báo phụ huynh ngay (HLT-14) |
| health | HealthInterpretation (childId, period, text, source, status) | CHILD | AI DRAFT → APPROVED |
| education-plan | Mục tiêu năm học (schoolYear, ageGroup, status, version) | SCHOOL | 1 bộ/(năm học, nhóm tuổi); tổ trưởng soạn, PHT duyệt/ban hành (EDU-02) |
| education-plan | Chủ đề nhánh + kế hoạch theo chủ đề nhánh (classroom, status, version) | CLASS | Duyệt GV → tổ trưởng → PHT (EDU-03) |
| education-plan | Chủ đề tuần, kế hoạch ngày (classroom, date, activities[], objective, materials) | CLASS | Không duyệt (EDU-04); ngữ cảnh đánh giá (OBS-07) |
| education-plan | Lịch sử duyệt (actor, time, action, reason) | — | Bấm duyệt, không ký (EDU-05); nhân bản để sửa (EDU-08) |
| learning-observation | ObservationCriteria, CriteriaOption | SCHOOL | 7 mặt, 3 mức; cố định hay cấu hình: P-11 |
| learning-observation | Observation (childId, date, values[], note) | CLASS | Đánh giá ngày; mọi trẻ có mặt, mặc định "bình thường"; khóa hết ngày (OBS-01, OBS-12) |
| learning-observation | Summary (childId, period tuần/tháng/học kỳ/năm, text, source, status) | CHILD | Nháp → GV xác nhận, không BGH duyệt (OBS-05, OBS-06) |
| learning-observation | Nhận xét lớp hằng ngày (classroomId, date, text) | CLASS | RPT-02; BGH đọc qua reporting |
| learning-observation | Reward (childId, period, type, decidedBy) | CHILD | Cờ ngày, phiếu bé ngoan tuần (OBS-10); khen cuối năm GV → PHT → HT (OBS-14) |
| learning-observation | DevelopmentProfile | CHILD | Read model, không lưu bản sao nguồn |
| notification | Notification, DeliveryStatus | user | Không chứa dữ liệu nhạy cảm vượt mức |
| audit | AuditEvent | SYSTEM | Append-only |

## Quan hệ chính

```text
School 1─* Campus 1─* Classroom *─1 SchoolYear
Classroom 1─* Enrollment *─1 Child 1─* GuardianChildLink *─1 UserAccount(Parent)
UserAccount 1─* StaffAssignment ─→ School | Campus | Classroom
Child 1─* AttendanceRecord, Pickup, LeaveRequest
AttendanceRecord(có mặt) ──(× bữa nhóm tuổi)──→ MealCount ──×Recipe──→ FoodQuantityPlan
MealPlan *─* Dish 1─* Recipe line ─→ Food 1─* NutrientValue
Child 1─* HealthRecord, Observation, Summary, Reward
Classroom 1─* kế hoạch ngày ──(mục tiêu hoạt động)──→ Observation (OBS-07)
Child ──(read model)──→ DevelopmentProfile
```

Tham chiếu xuyên module bằng **ID**; không dùng JPA relationship xuyên module (xem `docs/architecture/PACKAGE_STRUCTURE.md`).
