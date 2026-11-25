---
title: Phụ huynh chọn năm học (BE)
status: approved
owner: Giáp
jira: TBD-4 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [school-structure, child, health, learning-observation]
rules: [CFG-YR-05, AUTH-03, AUTH-04, ATT-15, PAR-01, PAR-03]
risk: medium
created: 2026-11-25
updated: 2026-11-25
---
# Phụ huynh chọn năm học (BE)

Plan BE. Phần App: `APP:docs/plans/active/2026-11-25-parent-school-year-picker.md`.

## Context
- App phụ huynh cần ô "Năm học" như GV/Web: xem năm đang chạy và năm cũ của bé.
- `GET /school-years` chỉ cho `PRINCIPAL, VICE_PRINCIPAL, TEACHER` (`controller/organization/SchoolYearController.java:50`). Phụ huynh gọi ⇒ 403.
- Luật thấy năm của nhân sự: `SchoolConfigAccess.canSeeYear` (`service/organization/impl/SchoolConfigAccess.java:81`): ACTIVE/CLOSED luôn thấy; PLANNED chỉ từ ngày chuẩn bị và có phân công (CFG-YR-05).
- API phụ huynh theo khoảng ngày bắt khoảng nằm trong một năm học (ATT-15, `ParentAttendanceServiceImpl.java:176`). App không biết ngày năm học ⇒ phải đoán, mùa hè bị 4109.
- Không lọc theo năm: `GET /children/{childId}/health/published` (`HealthMeasurementServiceImpl.java:253`), `GET /children/{childId}/weekly-comments` (`WeeklyCommentServiceImpl.java:331`). `GET /children/{childId}/evaluations` đã có `schoolYearId` (`ParentAssessmentController.java:48`).

## Quyết định đã chốt
1. Làm theo đề xuất cho phụ huynh: endpoint mới danh sách năm của bé; năm đã đóng chỉ xem; trang chủ giữ hôm nay — user, 25/11/2026.
2. BE ở nhánh `fix/G94-180-BE-FIX-10-school-year-scope`, không tạo PR mới — user, 25/11/2026.
3. Chưa làm bếp, chưa nối ô năm học GV — user, 25/11/2026.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor phụ huynh · outcome xem dữ liệu của bé theo từng năm học bé đã học · AUTH-04 CONFIRMED, CFG-YR-05 CONFIRMED, AUTH-03 CONFIRMED (năm cũ chỉ đọc), ATT-15 CONFIRMED, PAR-01/PAR-03 CONFIRMED (chỉ dữ liệu đã công bố, không đổi).
- **Đối chiếu thiết kế ban đầu:** Web cho nhân sự chọn năm (`SchoolConfigAccess.java:81`); phụ huynh chưa có bộ chọn. Yêu cầu mới mở rộng cùng luật cho phụ huynh, giới hạn theo năm bé có học.
- **Ảnh hưởng:** child ──► school-structure (đúng chiều MODULE_MAP). Quyền: chỉ phụ huynh liên kết (4108). APP: Bé của tôi, Điểm danh, Đơn nghỉ, Đánh giá, Khen thưởng, Sổ sức khỏe. FE Web: không gọi 3 thay đổi này (tham số mới tùy chọn, mặc định như cũ).

### Câu hỏi mở
(không còn)

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Phụ huynh thấy năm nào? | Năm bé có học lớp; PLANNED chỉ từ ngày chuẩn bị và bé đã được xếp lớp năm đó | user | 25/11 | docs/modules/organization.md (Phase 1) |
| Năm cũ? | Chỉ xem như GV | user | 25/11 | — |
| Nhánh BE? | g94-180, không PR mới | user | 25/11 | — |

## Acceptance criteria
- **AC-1** (AUTH-04): Given phụ huynh không liên kết với bé When `GET /children/{id}/school-years` Then 403, `errorCode` 4108.
- **AC-2** (AUTH-03): Given bé học Nhà trẻ 1 năm 2025–2026 (CLOSED) và Mầm 1 năm 2026–2027 (ACTIVE) When phụ huynh gọi Then 2 năm, mới trước, mỗi năm có `schoolYearId, name, startDate, endDate, status, className`.
- **AC-3** (CFG-YR-05): Given năm PLANNED, bé đã xếp lớp năm đó When hôm nay trước ngày chuẩn bị Then không có năm đó; When từ ngày chuẩn bị Then có, `className` = lớp được xếp.
- **AC-4** (AUTH-04): Given năm bé không học lớp nào Then không có trong danh sách.
- **AC-5** (PAR-03): Given `GET /children/{id}/health/published?schoolYearId=X` Then chỉ kết quả có ngày trong năm X; growth và mục mới nhất tính trên các kết quả đó; không tham số ⇒ như cũ.
- **AC-6** (PAR-03): Given `GET /children/{id}/weekly-comments?schoolYearId=X` Then chỉ nhận xét của năm X; không tham số ⇒ như cũ.
- **AC-7**: `schoolYearId` không tồn tại hoặc bé không học năm đó ⇒ 404 `SCHOOL_YEAR_NOT_FOUND` (giống năm không được thấy).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nguồn danh sách năm | Endpoint mới `GET /children/{childId}/school-years` | Mở `GET /school-years` cho PARENT | Phụ huynh chỉ thấy năm bé học; có `className`; không lộ năm khác |
| Nơi đặt service | `service/child/ChildSchoolYearService` | Trong `SchoolYearService` | child ──► school-structure đúng chiều; organization không đọc enrollment |
| Lọc sức khỏe | Theo ngày kết quả trong [startDate, endDate] của năm | Theo `campaign.schoolYearId` | Có kết quả không thuộc đợt (cân đo lẻ); ngày luôn có |
| Đơn nghỉ `/leave-requests/me` | Không đổi BE | Thêm `schoolYearId` | Danh sách của cả phụ huynh, App lọc theo ngày đơn trùng năm (chỉ hiển thị) |

## Phases
### Phase 1 — Danh sách năm học của bé
- Files: C `service/child/ChildSchoolYearService.java` · C `service/child/impl/ChildSchoolYearServiceImpl.java` · C `controller/child/ChildSchoolYearController.java` · C `dto/child/ChildSchoolYearResponse.java` · M `repository/child/EnrollmentRepository.java` (nếu thiếu truy vấn theo trẻ) · C test `ChildSchoolYearIntegrationTest`.
- Steps: 1. Test AC-1..4, AC-7 đỏ. 2. Service: kiểm `GuardianChildLinkService.isGuardianOf`; enrollment của trẻ → lớp → năm (`ClassroomService`, `SchoolYearService`); ACTIVE/CLOSED từ enrollment; PLANNED từ `ClassPlacement.toClassroomId` hoặc `PlannedAdmission.classroomId` khi hôm nay ≥ ngày chuẩn bị; `className` = lớp enrollment mới nhất trong năm. 3. Controller `hasRole('PARENT')`, log START/END. 4. openapi, `API_INDEX.md`, module doc.
- Tests: AC-1..4, AC-7 → `ChildSchoolYearIntegrationTest`.
- Exit: `./mvnw -q test -Dtest=ChildSchoolYearIntegrationTest` → pass.
- **Dừng chờ user review.**

### Phase 2 — Lọc theo năm cho sức khỏe đã công bố và nhận xét tuần
- Files: M `HealthMeasurementController.java`, `HealthMeasurementService.java`, `HealthMeasurementServiceImpl.java` · M `WeeklyCommentController.java`, `WeeklyCommentService.java`, `WeeklyCommentServiceImpl.java` · M test `HealthRecordIntegrationTest`, test nhận xét tuần phụ huynh.
- Steps: 1. Test AC-5, AC-6 đỏ. 2. `@RequestParam(required = false) Long schoolYearId`; kiểm năm qua `ChildSchoolYearService` (AC-7); lọc. 3. openapi, `API_INDEX.md`.
- Tests: AC-5 → `HealthRecordIntegrationTest#parentSeesPublishedOfOneYear`; AC-6 → test nhận xét tuần.
- Exit: `./mvnw -q test -Dtest='HealthRecordIntegrationTest,WeeklyComment*Test,ChildSchoolYearIntegrationTest'` → pass; `./mvnw -q verify` (lỗi đã biết `EduYearPlanServiceIntegrationTest` varchar(20) ghi rõ).
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | AUTH-04 | ChildSchoolYearIntegrationTest (integration) | 1 | |
| AC-2 | AUTH-03 | ChildSchoolYearIntegrationTest | 1 | |
| AC-3 | CFG-YR-05 | ChildSchoolYearIntegrationTest | 1 | |
| AC-4 | AUTH-04 | ChildSchoolYearIntegrationTest | 1 | |
| AC-5 | PAR-03 | HealthRecordIntegrationTest | 2 | |
| AC-6 | PAR-03 | WeeklyComment test | 2 | |
| AC-7 | AUTH-04 | ChildSchoolYearIntegrationTest + Health | 1–2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Lộ năm/lớp của trẻ khác | Dữ liệu trẻ | Kiểm guardian link trước mọi truy vấn; AC-1 | Revert commit |
| PR #113 to thêm | Review lâu | Commit nhỏ, mã TBD-4 | Revert commit riêng |
| Web gọi API cũ | — | Tham số tùy chọn, mặc định như cũ | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer: App phụ huynh (6 màn); Web không gọi tham số mới
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] Đã tách: plan BE (2 phase) + plan App
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review

## Progress log
### 2026-11-25 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `mvnw test -Dtest=OpenApiSnapshotTest,ChildSchoolYearIntegrationTest,ArchitectureRulesTest` → Tests run: 23, Failures: 0, Errors: 0 (ChildSchoolYearIntegrationTest 5/5, đỏ trước bằng stub UnsupportedOperation)
- Khác plan: không sửa `EnrollmentRepository` (dùng `findChildDuring`); thêm `ClassPlacementRepository.findAllByChildId`; tài liệu ở `docs/modules/child.md` (không phải organization.md).
- Tiếp: Phase 2 (lọc `schoolYearId` cho health/published, weekly-comments).
### 2026-11-25 — Phase 2 (done)
- Commit: chưa commit
- Verify: `mvnw test -Dtest=WeeklyCommentIntegrationTest,HealthRecordIntegrationTest,ChildSchoolYearIntegrationTest,HealthRecordControllerTest,WeeklyComment*Test,ArchitectureRulesTest` → Tests run: 59, Failures: 0, Errors: 0; OpenApiSnapshotTest cập nhật snapshot.
- Khác plan: thêm `ChildSchoolYearService.findChildYear` (không kiểm người xem) vì `/health/published` cho cả nhân sự; `HealthRecords.latestExam/latestBmi` thêm overload có bộ lọc. Giải thích xu hướng đã duyệt giữ nguyên (không lọc năm).
