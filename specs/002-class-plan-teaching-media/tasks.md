# Tasks — Học liệu và trình chiếu

## Phase 1 — Setup
- [x] T001 Ghi spec/clarifications trong specs/002-class-plan-teaching-media/spec.md (FR-001..017).
- [x] T002 Thiết kế data-model.md/contracts/plan.md cùng thư mục; giữ stack và dữ liệu cũ.
- [x] T003 Analyze read-only spec/plan/tasks và source, ghi kết quả trong analysis.md cùng thư mục.

## Phase 2 — Foundation
- [x] T004 Viết test giới hạn/type/binding trong src/test/java/com/carenest/service/eduplan/LearningResourcePolicyTest.java.
- [x] T005 Viết StorageServiceTest trong src/test/java/com/carenest/integration/storage/StorageServiceTest.java cho confinement/cleanup.
- [x] T006 Tạo policy trong src/main/java/com/carenest/service/eduplan/LearningResourcePolicy.java và StorageService trong integration/storage (FR-004/005/006/009).
- [x] T007 Migration V31__add_edu_learning_resources.sql, entity EduLearningResource.java/repository EduLearningResourceRepository.java theo data-model.md.
- [x] T008 Cấu hình media root/multipart trong application.yml, Dockerfile/compose.yaml/gitignore cho private persistent data (FR-006).

## Phase 3 — US1: giữ luồng tài liệu
- [x] T009 [US1] Kiểm hồi quy EduClassDocServiceIntegrationTest.java: prerequisite/assignment/dossier/lock và old JSON (FR-001/002).
- [x] T010 [US1] Tái dùng policy scope trong EduClassDocService.java/impl cho media access, không thay luồng duyệt (FR-007/008).

## Phase 4 — US2: học liệu
- [x] T011 [US2] Viết integration tests upload/retry/out-of-class/stale/locked/rebind/detach trong LearningResourceServiceIntegrationTest.java.
- [x] T012 [US2] DTO LearningResourceResponse/Binding và LearningResourceServiceImpl/repository: lock parent, validate saved activity/goal, rollback cleanup (FR-003/007..011).
- [x] T013 [US2] LearningResourceController.java multipart/list/rebind/detach/protected content, Range+CORS tests (FR-003..011).
- [x] T014 [US2] FE model/services learning resources và tests tại CareNest_FE/tests/education-plan/learning-resources.test.mjs; IndexedDB mock Blob (FR-006/009/010).
- [x] T015 [US2] FE ActivityResources.jsx trên ClassThemePlanPage.jsx: save-before-upload/status/errors/rebind/detach confirm/private preview (FR-003..011/016).

## Phase 5 — US3: dạy học
- [x] T016 [US3] FE model lọc activity theo ngày từ grid, test MT/empty/old-data trong learning-resources.test.mjs (FR-012/015).
- [x] T017 [US3] FE TeachingPresentationPage.jsx + AppRoutes.jsx + education-plan.css: selected Blob, image/video/PDF/download, keyboard/fullscreen/back (FR-012..016).
- [x] T018 [US3] FE ClassThemePlanPage.jsx/WeekPlanPage.jsx launch action trong hai thao tác, giữ unsaved guard và draft label.
- [x] T019 [US3] Browser smoke upload/reload/rebind/presenter theme/day/keyboard/fullscreen và lưu bằng chứng vào target (SC-003..005).

## Phase 6 — Quality/Converge
- [x] T020 Cập nhật docs/database/DATA_DICTIONARY.md, docs/modules/education-plan.md, docs/system/CROSS_REPO_MAP.md, docs/business/flows/education-plan.md và OpenAPI snapshot.
- [x] T021 Review làn L qua subagent, full node scripts/verify.mjs cả BE/FE, check-ai-layer và ghi output trong quickstart.md/progress log.
- [x] T022 Converge so spec/plan/tasks/code, append task ID mới cuối tasks.md nếu thiếu, không thay lịch sử (FR-017/SC-006).

## Design review additions — 2026-10-27
- [x] T023 [US3] Trong FE model/presenter kiểm week pick goalId+saved text cùng ID; stale pick báo rõ, không tự ghép học liệu sau reorder. Thêm parity fixture parser heading/plus trong tests/education-plan/learning-resources.test.mjs và LearningResourcePolicyTest.java.
- [x] T024 [US2] LearningResourceServiceIntegrationTest.java kiểm ZIP Office đúng folder/type, Range/scoped content và rollback tệp; không chấp nhận ZIP đổi đuôi.

## Dependencies / execution
T001→T002→T003→T004..T008→T009/T010→T011..T015→T016..T019→T020..T022.
Independent tests: US1 hồi quy chain cũ; US2 upload/mutation/privacy standalone; US3 presenter dữ liệu cũ không media và media hợp lệ.
Parallel opportunities: T004/T005 khác file; BE review và FE model tests; không cần agent implementer. MVP chuỗi cũ giữ nguyên + học liệu rồi presenter; vẫn hoàn tất toàn bộ scope.

## Phase 7: Convergence 1 — user S3 steering và legacy day content
- [x] T025 [US2] Thay storage filesystem T005/006/008 bằng S3 SDK v2 trong pom.xml, EducationStorageConfig.java và StorageService.java; cập nhật S3 tests/config/docs. User đồng ý dependency 27/10/2026; không cần local volume nữa. Giữ T005/006/008 để lưu lịch sử thay đổi.
- [x] T026 [US3] Trong FE classPlan.js/TeachingPresentationPage.jsx hỗ trợ nội dung grid nhập tay cũ khi ngày không có picks, không tự gán MT/học liệu; thêm test learning-resources.test.mjs và browser smoke.

Converge 1: T025/T026 là work mới theo source so với intent; T019/T020/T021/T022 và review final còn mở, không tạo task trùng cho các việc đang có. Không sửa spec/plan/source trong bước Converge.

## Phase 8: Convergence 2 — readiness nhận diện nội dung
- [x] T027 [US2] ActivityResources.jsx và TeachingPresentationPage.jsx chỉ dùng fingerprint khi thuộc đúng doc.rows hiện tại; trong lúc tính/nạp lại không ghép media theo signature cũ. Kiểm stale/unbound và giữ async cleanup viewer.

## Phase 9: Convergence 3 — PDF viewer fallback
- [x] T028 [US3] LearningResourceViewer.jsx/education-plan.css dùng native PDF object có fallback tải rõ ràng khi browser không có PDF viewer; không để iframe trắng. Browser IAB hiện không render PDF inline nên ghi đúng giới hạn kiểm chứng, không tuyên bố xem PDF đã pass.

Implementation notes: T006 policy đặt utils/LearningResourcePolicy.java theo ArchUnit (service package chỉ interface). T008 local-volume được thay thế T025 theo user S3, không giữ volume local. T011/T024 tái dùng fixture EduClassDocServiceIntegrationTest.java thay vì nhân đôi LearningResourceServiceIntegrationTest; Range trong LearningResourceControllerTest, rollback trong LearningResourceServiceImplTest. T019 browser smoke mock đã chạy; PDF native-render chưa được xác nhận trong IAB, fallback/download đang kiểm lại. T022 đã chạy các vòng Converge1..3, final clean phụ thuộc T021.


## Phase 10: Convergence 4 — multipart contract
- [x] T029 [US2] LearningResourceController.java/OpenAPI snapshot phải mô tả activityKey/signature/label là multipart parts như FE gửi; sửa annotation query-param đang lệch contract, thêm HTTP multipart test trong LearningResourceControllerTest.java và kiểm snapshot.

## Phase 11: Convergence 5 — S3 thật theo cấu hình user chỉ định
- [x] T030 [US2] Thêm src/test/java/com/carenest/integration/storage/StorageS3Smoke.java (chạy thủ công, không tự chạy CI) để đọc credential qua environment, upload/read byte-compare và dọn object mới trong prefix thử riêng. User cho phép lấy .env tại upload-fast-s3; không in/copy secret vào repo. Ghi kết quả thật trong quickstart.md và EDUCATION_MEDIA_S3.md.

## Phase 12: Convergence 6 — parser whitespace parity
- [x] T031 [US2] LearningResourcePolicy.hasActivity dùng đúng whitespace của parser JS (gồm NBSP/BOM khi dán từ Word), không coi heading rỗng có NBSP là hoạt động; thêm fixture parity BE/FE trong LearningResourcePolicyTest và learning-resources.test.mjs (FR-010).
