---
title: Phụ huynh xem hoạt động trong ngày và gọi giáo viên lớp
status: approved
owner: Giáp
jira: TBD-4 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [learning-observation, education-plan, child, identity-access]
rules: [EDU-37, PAR-01, PAR-04, AUTH-04, EDU-19]
risk: medium
created: 2026-11-26
updated: 2026-11-26
---
# Phụ huynh xem hoạt động trong ngày và gọi giáo viên lớp

Plan liên repo: BE (2 endpoint chỉ đọc cho phụ huynh) + App (2 màn). App: nhánh `feature/TBD-4-FE-FEAT-0-app-foundation`.

## Context
- EDU-37 (CONFIRMED, ghi "Chưa làm"): phụ huynh thấy tên các hoạt động trong ngày của lớp con. App màn "Hoạt động trong ngày" đang "Sắp có" (`APP:lib/routing/app_router.dart` `_ComingSoonPage('Hoạt động trong ngày')`).
- Giáo án ngày đã duyệt đọc được qua `EduLessonQueryService.findApprovedDayLessons` (`service/eduplan/impl/EduLessonQueryServiceImpl.java:50`) nhưng chỉ giữ dòng **có mục tiêu** (`dayLesson`, khoảng dòng 184–215: `if (!picked.isEmpty())`) và không có giờ.
- Hồ sơ bé `GET /children/{childId}/profile` có `cls.homeroomTeacherId`, `cls.teacherIds` (`ChildRecordServiceImpl.java` khoảng dòng 585) — chỉ ID, không tên, không SĐT. App nút "Gọi giáo viên" đang "Sắp có".
- `AccountService` chỉ có `findFullNames` (`service/account/AccountService.java:16`); SĐT nhân sự = `username` (đăng nhập bằng SĐT); có tài khoản seed không phải SĐT (vd. `teacher1`).
- MODULE_MAP: learning-obs ──► child, education-plan (đúng chiều cho API hoạt động); child ──► identity-access (đúng chiều cho API giáo viên).

## Quyết định đã chốt (Giáp, 26/11/2026)
1. Tiền giao: Giáp được thêm API **chỉ đọc** ở module kế hoạch giáo dục, không đổi nghiệp vụ.
2. Phụ huynh xem **mọi dòng hoạt động có nội dung** của giáo án ngày đã duyệt (kể cả dòng không gắn mục tiêu).
3. Hiển thị danh sách theo thứ tự dòng, **không giờ, không ảnh**; ngày chưa có giáo án đã duyệt ⇒ câu trống.
4. Hiện **tên cô + nút gọi** (App mở trình quay số). **Mọi GV đang dạy lớp**, mỗi cô một dòng, nhãn nhỏ "Chủ nhiệm" cho cô chủ nhiệm.

## Làm rõ nghiệp vụ
- Actor phụ huynh · AUTH-04 (chỉ con mình, khác ⇒ 403 `4108`) · EDU-19 (chỉ giáo án đã ban hành) · PAR-04 (không chat: App chỉ mở trình quay số).
- Đổi so với plan App 18/11 (`APP:docs/plans/completed/2026-11-18-parent-my-child-profile.md`: bỏ nút Gọi cô) ⇒ quyết định 4 thay thế.
- Web không đổi (2 endpoint mới, không sửa API cũ).

### Câu hỏi mở
(không còn)

## Acceptance criteria
- **AC-1** (AUTH-04): phụ huynh không liên kết gọi `GET /children/{id}/activities` hoặc `/teachers` ⇒ 403 `4108`.
- **AC-2** (EDU-37, EDU-19): `GET /children/{childId}/activities?date=` ⇒ các dòng của giáo án ngày **đã ban hành** của lớp bé ngày đó, theo thứ tự dòng: `{ label, topic }`, chỉ dòng có `topic` khác rỗng (có hoặc không có mục tiêu). Không trả mục tiêu, mã nội bộ.
- **AC-3**: ngày chưa có giáo án ban hành, ngày bé chưa có lớp ⇒ `activities: []` (200).
- **AC-4** (PAR-04): `GET /children/{childId}/teachers` ⇒ GV đang phân công lớp hiện tại của bé: `{ fullName, phone, homeroom }`, chủ nhiệm trước; `phone` null khi tên đăng nhập không phải SĐT.
- **AC-5** (App): màn Hoạt động trong ngày: dải ngày trong tuần, danh sách "Tên hoạt động — nội dung"; trống ⇒ "Lớp chưa có giáo án ngày này".
- **AC-6** (App): Bé của tôi › Giáo viên: mỗi cô một dòng (tên, nhãn "Chủ nhiệm"), nút gọi khi có SĐT ⇒ `tel:`; không SĐT ⇒ không có nút.

## Key decisions
| Quyết định | Chọn | Khác | Vì sao |
| --- | --- | --- | --- |
| Chỗ đặt API hoạt động | Controller + service mới trong `observation` (learning-obs), gọi `EduLessonQueryService` | Trong eduplan | eduplan không được phụ thuộc child (MODULE_MAP); learning-obs đã có API phụ huynh |
| Đọc mọi dòng | Thêm `findDayActivities(classroomId, date)` vào `EduLessonQueryService` (chỉ đọc), tách phần đọc nội dung khỏi lọc mục tiêu | Sửa `findApprovedDayLessons` | Không đổi hành vi đánh giá trẻ đang dùng hàm cũ |
| API giáo viên | Endpoint riêng `GET /children/{id}/teachers` (child) | Thêm vào `/profile` | `/profile` dùng chung Web nhân sự; không đổi contract cũ |
| SĐT | `AccountService.findContacts(ids)` (tên + username dạng SĐT) | Đọc repository tài khoản từ child | Service không inject repository module khác |

## Phases
### Phase 1 — BE hoạt động trong ngày
- Files: M `service/eduplan/EduLessonQueryService.java`, `impl/EduLessonQueryServiceImpl.java` (+ `findDayActivities`) · C `dto/observation/ParentActivityDtos.java` · C `service/observation/ParentActivityService.java` + `impl/ParentActivityServiceImpl.java` · C `controller/observation/ParentActivityController.java` · C test tích hợp.
- Tests: AC-1, AC-2, AC-3 → `ParentActivityIntegrationTest`.
- Exit: `mvnw test -Dtest=ParentActivityIntegrationTest,OpenApiSnapshotTest,ArchitectureRulesTest` pass.

### Phase 2 — BE giáo viên của bé
- Files: M `service/account/AccountService.java` + impl (`findContacts`) · C `dto/child/ChildTeacherResponse.java` · M `ChildRecordService` + impl (`findTeachersForParent`) · M `ChildPhotoController` hoặc C `ChildTeacherController` · test.
- Tests: AC-1, AC-4 → `ChildTeacherIntegrationTest`.
- Exit: như Phase 1 + `openapi.yaml`, `API_INDEX.md`, module docs.

### Phase 3 — App
- Files: `parent/my_child` (nguồn live hoạt động, giáo viên; màn hoạt động bỏ giờ/ảnh; mục Giáo viên) · `app_router.dart` (bỏ "Sắp có") · `AndroidManifest.xml` (`<queries>` intent `DIAL`) · tests.
- Tests: AC-5, AC-6 → test nguồn + widget.
- Exit: `node scripts/verify.mjs` → VERIFY PASS.

## Test matrix
| AC | Test | Phase |
| --- | --- | --- |
| AC-1 | ParentActivityIntegrationTest, ChildTeacherIntegrationTest | 1, 2 |
| AC-2, AC-3 | ParentActivityIntegrationTest | 1 |
| AC-4 | ChildTeacherIntegrationTest | 2 |
| AC-5, AC-6 | App live source + widget tests | 3 |

## Rủi ro & rollback
| Rủi ro | Giảm thiểu |
| --- | --- |
| Đụng module của Tiền (eduplan) và Cần (observation) | Chỉ thêm hàm/tệp mới chỉ đọc; không sửa hàm cũ |
| Lộ SĐT GV | Quyết định 4 (Giáp chốt); chỉ GV lớp con mình, không lộ ID |
| Tài khoản GV không phải SĐT | `phone` null ⇒ App ẩn nút gọi |

## Validation log
- [x] Khẳng định code có `file:line`
- [x] Đổi contract: 2 endpoint mới; consumer = App phụ huynh; Web không đổi
- [x] Mỗi AC có test, mỗi phase có Exit
- [x] Không dùng rule PENDING/OPEN

## Progress log
### 2026-11-26 — Phase 1 + 2 BE (done)
- Commit: chưa commit
- Verify: `mvnw test -Dtest=ChildTeacherServiceImplTest,ParentActivityServiceImplTest,EduLessonQueryServiceImplTest,ArchitectureRulesTest,ControllerSecurityCoverageTest` → Tests run: 31, Failures: 0, Errors: 0; OpenApiSnapshotTest cập nhật.
- Khác plan: response hoạt động `{ label, items[] }` (tên thẻ đã xếp / gõ tay + nội dung ô) thay `{ label, topic }` vì một dòng có thể nhiều hoạt động; tách `releasedWeeks` dùng chung cho `findApprovedDayLessons` (hành vi giữ nguyên, 6 test cũ pass); API giáo viên đặt ở module tài khoản (`service/account`, `controller/account`) thay module trẻ vì MODULE_MAP cấm trẻ gọi ngược tài khoản — không cần thêm `AccountService.findContacts`; test dùng Mockito (không Testcontainers).
### 2026-11-26 — Phase 3 App (done, chưa kiểm máy thật)
- Commit: chưa commit
- Verify App: `node scripts/verify.mjs` → `VERIFY PASS full | format ok | design ok | analyze ok | tests 523 fail 0 skip 0`
- App: màn "Hoạt động trong ngày" theo dòng giáo án (dải ngày T2–T6, không giờ, không ảnh, câu trống); Bé của tôi › "Giáo viên của lớp" (mỗi cô một dòng, nhãn "Chủ nhiệm", nút gọi khi có SĐT, lỗi mở trình gọi hiện số); ô Hoạt động ở trang chủ ghi "Xem"; bỏ đánh giá ngày và ảnh giả khỏi màn hoạt động; `AndroidManifest` đã có `<queries>` DIAL từ trước.
- Chưa: kiểm máy thật (BE 8081 cần chạy lại với code mới).
