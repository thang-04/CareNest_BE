# Tasks: Phiên bản kế hoạch năm và kế hoạch điều chỉnh, bổ sung

**Input**: spec.md, plan.md, research.md, data-model.md, contracts/api.md · **Tests**: có (BE unit/IT, FE node:test) · `[P]` = chạy song song được (khác file).

## Phase 1 — Setup / nền DB (BE)
- [X] T001 Tạo `src/main/resources/db/migration/V32__add_edu_plan_versions.sql` theo data-model §6
- [X] T002 [P] Thêm `versionNo`, `sourcePlanId`, factory `createVersion(...)` vào `entity/eduplan/EduYearPlan.java`
- [X] T003 [P] Thêm `lineageId` + `effectiveLineage()` + factory chép vào `EduGoalRow`, `EduGoalContent`, `EduTheme`, `EduBreak`
- [X] T004 [P] Thêm `sourceDocId`, `adjustments`, `changeAdjustments(...)`, `linkSource(...)` vào `EduClassDoc.java`
- [X] T005 [P] Thêm ApiCode 7027, 7028, 7029, 7121, 7122, 7123 vào `utils/ApiCode.java`
- [X] T006 Repository: `EduYearPlanRepository` (chuỗi phiên bản, phiên bản mở, ban hành mới nhất); `EduClassDocRepository` (theo id)

## Phase 2 — Foundational: so sánh phiên bản (BE)
- [X] T007 Tạo `service/eduplan/impl/YearPlanVersionDiff.java` (thuần) theo research R6
- [X] T008 [P] Unit test `src/test/java/com/carenest/service/eduplan/impl/YearPlanVersionDiffTest.java` (đổi chữ, chèn MT đánh số lại, xóa/tích nội dung, chủ đề đổi/xóa/mới)
- [X] T009 Tạo DTO `dto/eduplan/YearPlanChangesResponse.java`; thêm field phiên bản vào `YearPlanDetailResponse`, `YearPlanSummaryResponse`, mapper

## Phase 3 — US1 Kế hoạch điều chỉnh và bổ sung (P1)
- [X] T010 [US1] BE: `EduClassDocService.saveAdjustments` + impl (GV lớp, mọi trạng thái, ngày thuộc tuần, ≤4000, ghi history) và `toNode` trả `adjustments`
- [X] T011 [US1] BE: `PUT /class-docs/week/{docKey}/adjustments` trong `EduClassDocController` + `ClassDocAdjustmentsRequest`
- [X] T012 [US1] BE IT: lưu khi APPROVED giữ status/approvals/content; GV khác lớp 7103; ngày ngoài tuần 7123 (`EduClassDocServiceIntegrationTest`)
- [X] T013 [P] [US1] FE: `saveClassDocAdjustments` ở api, mock (`classPlanMockRepository`), facade, hook
- [X] T014 [US1] FE: ô "Kế hoạch điều chỉnh và bổ sung" trong kế hoạch ngày (`lessonShared.jsx`/`WeekPlanBody.jsx`), sửa được mọi trạng thái cho GV lớp; Lưu ở `WeekPlanPage`/`ThemeDossierPage` gọi adjustments khi đổi (manual save + bản giữ)
- [X] T015 [P] [US1] FE: in/xuất Word mục điều chỉnh (`utils/education-plan/classPlanDoc.js`)

## Phase 4 — US2 Tạo/ban hành phiên bản (P1)
- [X] T016 [US2] BE: `createVersion` trong `EduYearPlanService(Impl)` (quyền, nguồn mới nhất, một bản mở — cả khi hai người tạo đồng thời: bắt vi phạm unique ⇒ 7028, request id, chép cây + ticks + lineage, phiên sửa, event)
- [X] T017 [US2] BE: `POST /goals/{planId}/versions` trong controller; detail/summary trả `versionNo`, `sourcePlanId`, `current`, `versions`, `canCreateVersion`
- [X] T018 [US2] BE IT: tạo v2 đủ cây; 7028; 7027; GV 7003; v1 bất biến sau sửa v2; ban hành v2 ⇒ current đổi; xóa v2 nháp
- [X] T019 [P] [US2] FE: mapper/api `createGoalVersion`, field phiên bản; mock tạo phiên bản (luật EDU-25 dẫn rule ID)
- [X] T020 [US2] FE: `GoalDetailPage` nút "Tạo phiên bản điều chỉnh" + Lịch sử phiên bản; `GoalListPage` nhãn phiên bản; `useClassPlanContext` dùng phiên bản hiện hành (`planVersions.mjs: currentPlanOf`)

## Phase 5 — US3 So sánh phiên bản (P1)
- [X] T021 [US3] BE: `getChanges` (quyền EDU-06 hai bản, cùng chuỗi) + `GET /goals/{planId}/changes`
- [X] T022 [US3] BE IT so sánh qua API dữ liệu thật (`EduYearPlanServiceIntegrationTest`)
- [X] T023 [P] [US3] FE: api/mock `getGoalChanges`; mock dùng cùng quy tắc R6
- [X] T024 [US3] FE: khối "Thay đổi so với phiên bản k" ở `GoalDetailPage`

## Phase 6 — US4 Ô đỏ, tạo bản mới tài liệu lớp (P1)
- [X] T025 [US4] BE: khóa tuần theo phiên bản; chặn lưu/gửi trên phiên bản đã thay thế (7122); nhận `sourceDocKey` khi tạo; node trả `yearPlanVersion`, `sourceDocKey`, `superseded`
- [X] T026 [US4] BE: kiểm ô đỏ khi gửi (R5) cho chủ đề nhánh và hồ sơ chủ đề (7121), bỏ qua chủ đề đã kết thúc
- [X] T027 [US4] BE IT: lưu v1 sau v2 → 7122; chép theme doc có tham chiếu MT bị bỏ → gửi 7121; đánh dấu + remap → gửi được; duyệt hồ sơ v1 đang chờ vẫn được
- [X] T028 [P] [US4] FE: `models/education-plan/planVersions.mjs` (currentPlanOf, remapDoc, versionFlags, unresolvedCount) + `tests/education-plan/plan-versions.test.mjs`
- [X] T029 [US4] FE: `VersionWidgets.jsx` (thông báo phiên bản, Tạo từ mẫu/Chép bản cũ, ghi chú chủ đề đã kết thúc, nút Đã kiểm tra) dùng class/token lỗi có sẵn
- [X] T030 [US4] FE: tích hợp ô đỏ vào `BranchPlanPage`, `ThemePlanBody`/`ClassThemePlanPage`, `WeekPlanBody`/`WeekPlanPage`, `ThemeDossierPage`, `ClassPlanPage` (nhãn "mới" cho MT/nội dung mới ở kế hoạch chủ đề, US4-9); `submitBlocker` chặn khi còn ô đỏ; khóa tuần theo phiên bản ở `classPlan.js`
- [X] T031 [P] [US4] FE mock: chặn lưu/gửi phiên bản đã thay thế, kiểm ô đỏ khi gửi; assessment mock ưu tiên phiên bản mới nhất

## Phase 7 — Polish
- [X] T032 [P] Docs BE: BUSINESS_RULES (EDU-01/03/12 sửa, EDU-25/26 thêm), modules/education-plan, flows, DATA_DICTIONARY, ERROR_CONTRACT, openapi.yaml; `node scripts/check-ai-layer.mjs`
- [X] T033 BE `node scripts/verify.mjs`
- [X] T034 FE `node scripts/verify.mjs`
- [X] T035 Playwright real-BE nếu dev stack chạy; ghi kết quả vào quickstart

## Dependencies
T001→T002..T006→T007..T009→(US1 T010..T015 độc lập) ; US2 T016..T020 → US3 T021..T024 → US4 T025..T031 → T032..T035.

## Converge (speckit-converge, 2026-10-28)
- [X] C001 [US4] FE: chặn nút Gửi ở Web khi còn ô đỏ — `BranchPlanPage` (sendBlocked), `ThemeDossierPage` (beforeOpen đếm ô đỏ kế hoạch chủ đề + các tuần) (FR-026)
- [X] C002 [US4] FE: `ThemeDossierPage` khóa sửa khi xem phiên bản đã thay thế, hiện SupersededNote, lưu giữ dòng MT bị bỏ (`danglingRows`) như trang kế hoạch chủ đề (FR-021, FR-023)
- [X] C003 [US4] FE: xem tài liệu phiên bản cũ chỉ đọc qua `?plan=<id>` (`useClassPlanContext`, ctx.superseded) (FR-021, US4-1)
- [X] C004 Docs BE: ghi chú thay ghi nhận PROPOSED 26/10 trong BUSINESS_RULES; EDU-01/03/12 sửa, EDU-25/26/27 thêm; module/flow/DATA_DICTIONARY/ERROR_CONTRACT (T032)
- [X] C005 [US1] FE: in/xuất Word in nội dung ô điều chỉnh, để trống thì giữ dòng viết tay (US1-4)
- [X] C006 [US4] FE mock đánh giá trẻ: MT đã chấm hiện theo MT cùng lineage của giáo án ngày đang dùng (Tiền 28/10 thay mặc định 6, FR-028)
- [X] C007 [US4] FE: `remapCriteriaToPlan` (planVersions.mjs) + unit test cho ghép MT đánh giá trẻ (C006); mock học liệu chặn upload/gắn/gỡ trên phiên bản đã thay thế (FR-021)
- [ ] C008 [US4] Playwright real-BE bổ sung cho spec 004: tài liệu lớp bản cũ chỉ xem, Chép bản cũ và sửa ô đỏ, ô đỏ chặn gửi, Đã kiểm tra/Bỏ tham chiếu, ô điều chỉnh lưu sau duyệt (ST-13..15 của feature 005 chỉ phủ tạo/ban hành V2) — chờ dev stack
