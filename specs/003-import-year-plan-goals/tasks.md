# Tasks: Lấy mục tiêu từ kế hoạch năm học có sẵn

**Input**: spec.md, plan.md, research.md, data-model.md, contracts/source-import.md, quickstart.md.
**Authorization**: User 28/10/2026 chỉ đạo tasks→analyze (sửa lỗi)→implement→converge, lặp tới sạch. Không commit/push/branch. Đường dẫn FE dưới đây tương đối từ repo BE, task mới không xóa/đổi ID hoặc lịch sử cũ.
**Tests**: Bắt buộc theo AC/SC của spec; RED trước implementation có logic. Constitution chưa ban hành, giữ AGENTS/EDU-24.

## Phase 1: Setup

- [x] T001 Kiểm tra baseline, checklist 16/16, ignore/build tooling tại ../CareNest_FE/.gitignore và ghi authorization/checkpoints vào docs/plans/active/2026-10-28-import-year-plan-goals.md.

## Phase 2: Foundational

- [x] T002 Chốt giao diện listGoalSources/getGoalSource và mergeImportedGoals trong specs/003-import-year-plan-goals/contracts/source-import.md, bảo đảm tất cả task/code dùng cùng signature, không đổi HTTP contract (FR-001, FR-007, FR-012).

## Phase 3: User Story 1 — Nguồn khác năm/nhóm tuổi (P1)

**Independent test**: API/mock đọc nguồn đúng filter, đủ trang, đúng scope và không sửa nguồn; modal đổi filter không đổi header (AC-1/2/7).

- [x] T003 [P] [US1] Thêm test RED pagination/filter/ID mapping/fresh detail/cache isolation/lỗi 403-404 tại ../CareNest_FE/tests/education-plan/year-plan-api.test.mjs (FR-002/003/013).
- [x] T004 [P] [US1] Thêm test RED mock VP cross-age, TL same-age, forged source ID, empty/error và read-only tại ../CareNest_FE/tests/education-plan/import-goal-sources.test.mjs (FR-007/013).
- [x] T005 [US1] Thêm listGoalSources/getGoalSource trong ../CareNest_FE/src/services/education-plan/api/educationPlanApi.js: filter theo refs, đủ totalPages, summary-only, get detail không ghi known (T003; FR-002/003/004/007).
- [x] T006 [US1] Thêm methods cùng shape và scope mock tại ../CareNest_FE/src/services/education-plan/mock/educationPlanMockRepository.js (T004; FR-007/013).
- [x] T007 [US1] Export facade đọc nguồn qua ../CareNest_FE/src/services/education-plan/educationPlanService.js, không sửa getEducationPlans/getAll toàn cục (T005/006; FR-002/003).
- [x] T008 [US1] Tạo ../CareNest_FE/src/hooks/education-plan/useGoalImportSources.js dùng useAsync, query keys, trạng thái list/detail, reset selection trigger, guard request cũ (T007; FR-003/013).
- [x] T009 [US1] Nối filter/list/detail độc lập provider vào ../CareNest_FE/src/components/education-plan/ImportGoalsModal.jsx, giữ năm header và restrict TL theo nhóm đích, hiển thị metadata/contents/cha (T008; FR-001/002/004/007/013).

## Phase 4: User Story 2 — Chọn từng mục tiêu hoặc tất cả (P1)

**Independent test**: nguồn 10 goal hai lĩnh vực; chọn 1/tập con/domain/all/clear, đổi filter/source/reset, Hủy không đổi form (AC-3).

- [x] T010 [US2] Thêm scenario selection/reset/keyboard/error/late response trong ../CareNest_FE/tests/e2e/system/edu-plan-flows.spec.mjs trước triển khai tương tác (FR-005/006/013/014, SC-002/003/006).
- [x] T011 [US2] Sửa ../CareNest_FE/src/components/education-plan/ImportGoalsModal.jsx để selection theo sourceId/row.id, count đúng, zero không confirm, all/domain toggles, không headings; clear selection đồng bộ khi đổi nguồn (T009/010; FR-005/006/014).

## Phase 5: User Story 3 — Ghép, lưu và giữ wizard (P1)

**Independent test**: ghép→normalize/reopen giữ path cũ/mới, mới ID và ticks rỗng; Save–reopen giữ nội dung; không mutation trước Save (AC-4/5/6/8/9).

- [x] T012 [P] [US3] Viết test RED tại ../CareNest_FE/tests/education-plan/import-goals.test.mjs cho merge selection hierarchy/dedupe/unavailable/new IDs/empty ticks và round-trip first-sub/orphan (FR-008/009/010/011, SC-004/005).
- [x] T013 [US3] Tạo helper thuần ../CareNest_FE/src/models/education-plan/importGoals.mjs theo data-model §5, không mutate input, guard normalize cả target/source, report đủ added/duplicates/unavailable (T012; FR-008/009/010/011).
- [x] T014 [US3] Thêm confirm fresh-read/version+selected-ID checks, generation và double-submit guard vào ../CareNest_FE/src/hooks/education-plan/useGoalImportSources.js và ../CareNest_FE/src/components/education-plan/ImportGoalsModal.jsx (T011; FR-013, AC-7/8).
- [x] T015 [US3] Thay handler importGoals trong ../CareNest_FE/src/pages/education-plan/GoalFormPage.jsx bằng helper, kiểm quyền/lease hiện tại, giữ themes/breaks/metadata, trả boolean chỉ đóng khi ghép được; giữ wizard/manual save/recovery (T013/014; FR-001/008/009/011/012/015).
- [x] T016 [US3] Hoàn thiện ../CareNest_FE/tests/e2e/system/edu-plan-flows.spec.mjs: fake fixtures riêng, source-change/403-404/network, lost lease, canEdit=false độc lập lease, fresh detail thiếu selected ID dù revision không đổi, no-write-before-save, save/reopen, timing 200 goals và responsive (T015; FR-001..015, SC-001..006).

## Phase 6: Polish & Verification

- [x] T017 Cập nhật ../CareNest_FE/docs/features/year-plan-import.md dẫn EDU-24, interface/giới hạn quyền/provenance và verified limitations; cập nhật specs/003-import-year-plan-goals/quickstart.md bằng bằng chứng thật, không chép business rules sang FE.
- [x] T018 Chạy node scripts/verify.mjs và node scripts/check-ai-layer.mjs tại ../CareNest_FE, ghi output thật vào docs/plans/active/2026-10-28-import-year-plan-goals.md; xử lý lỗi trong scope (FR-001..015).
- [x] T019 Chạy E2E API/system và ma trận quickstart với BE thật theo ../CareNest_FE/tests/e2e/README.md, ghi dữ liệu/bằng chứng ở quickstart và Progress log; không báo mock thành BE thật (SC-001..006).
- [x] T020 Rà code độc lập, chạy cổng BE/docs sau lần sửa cuối và ghi kết quả vào docs/plans/active/2026-10-28-import-year-plan-goals.md; giữ source BE/OpenAPI nguyên trạng.

## Dependencies & Execution Order

T001→T002; T003/T004 có thể song song; T005→T007 và T006→T007; T008→T009→T010→T011→T014→T015→T016. T012→T013 độc lập transport sau foundation và có thể song song US1/US2 vì sở hữu file khác. T017–T020 sau tích hợp, mỗi checkpoint review nội bộ theo chỉ đạo user.

## Parallel Examples

- US1: tests API T003 và tests mock T004 ở file khác.
- US2: main xử lý modal tuần tự T010/011; không dispatch hai người sửa cùng ImportGoalsModal.jsx.
- US3: helper/test T012/013 có thể chạy độc lập transport T003–T007; tích hợp chỉ sau khi contracts hai bên khớp.

## Implementation Strategy

MVP đọc nguồn đúng quyền + chọn một/tất cả, rồi ghép/lưu đầy đủ trước bàn giao. 3 review checkpoint tương ứng active plan, không dừng xin phép lại vì user đã chỉ đạo vòng lặp. Analyze chỉ đọc; remediation là bước riêng được user cho phép. Converge append-only; giữ mọi task cũ, không renumber hoặc xóa task đã hoàn thành. Không tạo Jira/branch/commit hay sửa dependency.

## Execution notes 2026-10-28

- Analyze: 21/21 FR+SC có task; Critical/High/Medium=0, không task mồ côi; remediation loop được user cho phép. Constitution vẫn template, không thay đổi nó.
- Checklist requirements 16/16; ignore hiện có đủ (ESLint ignorePatterns thay cho .eslintignore), FE feature checkout hiện tại source sạch trước task; giữ các docs dirty của user.
- Transport T003–T007 RED 8 test mới fail do thiếu method, GREEN 16/16 API/mock tests. Helper T012/013 GREEN 15 case; test:edu aggregate 59/59, không skip.
- T008/009/011/014/015: browser mock pass nguồn khác năm/tuổi, selection/all/reset, no-write-before-Save, source/target preservation, empty new ticks, save–reopen/wizard; review tĩnh không P1/P2.
- T010/T016 dùng suite riêng ../CareNest_FE/tests/e2e/system/year-plan-import.spec.mjs thay vì nối vào chuỗi serial edu-plan-flows.spec.mjs để fixture độc lập. Giữ đường dẫn dự kiến cũ ở task như lịch sử, không đổi ID/renumber; chỉ khác cách tổ chức test, không đổi intent.
- TDD UI: browser RED trước tích hợp do còn mô tả copy lịch/dấu tích cũ; GREEN sau khi đổi modal/handler. Suite E2E bền vững được bổ sung tiếp để bảo vệ hành vi.
- Các task chưa đánh dấu vẫn chưa được coi hoàn tất; không suy từ node/mock thành E2E thật.

- E2E suite bền vững 13 scenarios đã tạo và syntax/format đạt; chưa chạy vì E2E_DEV_PASSWORD chưa có trong process/local dev seed. Backend/Keycloak health 200. User đã được hỏi đặt biến vào ignored .env.e2e.local; không yêu cầu gửi secret qua chat. T019 vẫn mở.
- FE verify đầy đủ sau tích hợp: VERIFY PASS full | npm run verify | log verify.log. Review độc lập 31 tests API/mock/helper pass, không finding đã chứng minh P1/P2/P3; chưa coi là BE E2E.

## Phase 7: Convergence

Assessment 28/10/2026: 15 FR, 6 SC, 21 acceptance scenarios, 7 edge cases, 10 design groups. Code missing/partial/contradicts/unrequested=0/0/0/0; evidence partial=1 (V001, MEDIUM). Constitution skipped vì còn template. Không sửa spec/plan/source trong converge.

- [x] T021 Hoàn tất kiểm chứng T019 bằng suite ../CareNest_FE/tests/e2e/system/year-plan-import.spec.mjs với BE/Keycloak thật sau khi E2E_DEV_PASSWORD được đặt trong môi trường hoặc ignored ../CareNest_FE/.env.e2e.local; ghi kết quả thật tại specs/003-import-year-plan-goals/quickstart.md và Progress log, không báo mock là BE thật per SC-001..SC-006 và plan: real-BE E2E exit (partial — validation V001).

## Execution status after convergence pass 1

- 19/21 task hoàn tất. T019 và T021 cùng chờ một điều kiện ngoài code: credential dev để chạy 13 real-BE E2E scenarios. Không tự đóng task này hoặc báo converged khi chưa có output thật.
- BE cổng nền sau implementation: VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 123s; FE full verify đã đạt. Java source/OpenAPI không sửa.
- Browser mock edge QA đạt 7/7: revision đổi, selected ID thiếu cùng revision, 403, 404, network, late close và late filter; happy path khác năm/tuổi + Save/reopen cũng đạt.
- Môi trường đã kiểm: BE/KC health200; E2E_DEV_PASSWORD không có trong process/User/Machine, file ignored .env.e2e.local chưa có. Đang chờ user cung cấp qua môi trường, không qua chat.

- Retry 28/10 sau user đặt file: local config đã được đọc, nhưng real-BE E2E beforeAll fail lấy token (invalid_grant / Invalid user credentials); 1 setup failed, 12 test không chạy. Canonical dotenv và username/email đều cùng lỗi; actual realm account enabled=true, requiredActions=[] qua read-only lookup. Đây là mismatch credential tài khoản Phó HT; T019/T021 vẫn mở, chờ user cập nhật file đúng tài khoản. Không lưu password/token trong artifacts.

## Execution status after real-BE E2E — 28/10/2026

- T019/T021 hoàn tất: 13/13 ca E2E trên BE/Keycloak thật đạt trong một lần chạy cuối (1.1m), không fail/skip; E2E_PG_DB khớp database runtime carenest_merge. File env riêng được Git ignore; không ghi secret vào task/log.
- Hai assertion test được sửa cho đúng UI hiện có: canEdit=false không render editor; Ctrl+S trên pristine document không ghi/tạo timestamp. Các kiểm tra không có quyền import, dữ liệu giữ nguyên và không dư lượt ghi vẫn được giữ.
- 21/21 task hoàn tất; giữ toàn bộ ID, task và lịch sử các lần chờ/failed ở trên. Chạy converge read-only lần cuối, nếu sạch không append header/task trống.

## Phase 8: Convergence

Post-E2E fixture audit 28/10: một bản [E2E-IMPORT] còn sót từ lần SQL helper trỏ sai DB trước đó. Feature code không thiếu; isolation/cleanup test cần bổ sung guard môi trường và marker cùng ID (MEDIUM, partial, T016/T019, plan fixture cleanup).

- [x] T022 Hoàn tất guard fixture trong ../CareNest_FE/tests/e2e/system/year-plan-import.spec.mjs: yêu cầu E2E_PG_DB rõ ràng; cleanup plan theo ID+marker và năm theo ID+name; dọn đúng bản thử còn sót đã xác minh, chạy lại 13 E2E và xác nhận 0 fixture [E2E-IMPORT] còn lại per T016/T019 và plan: isolated fixture cleanup (partial).

## Final verification after fixture hardening — 28/10/2026

- T022 hoàn tất: E2E yêu cầu E2E_PG_DB, cleanup ID+marker/ID+name; dọn đúng orphan ID70 có marker riêng đã xác minh từ lần sai DB. Sau rerun 13 passed (1.0m), đếm fixture import còn lại = 0.
- 22/22 task hoàn tất, không bỏ ID hoặc lịch sử cũ. Converge cuối chỉ đọc; không thêm phase rỗng khi không còn gap. File .env.e2e.local vẫn ignored và không tracked.
