# Tasks: Ba bảng mục tiêu cha–con
## Phase 1: Setup
- [x] T001 Ghi approval và tạo spec/plan/research/data-model/contracts/quickstart tại specs/006-year-plan-goal-hierarchy (FR-001..008).
## Phase 2: Foundational regression
- [x] T002 Viết và chạy RED test V34→V35 ở src/test/java/com/carenest/repository/eduplan/EduGoalHierarchyMigrationTest.java (FR-001/002/004, SC-001/004).
## Phase 3: US1 — Nâng cấp và entity
- [x] T003 [US1] Tạo V35__split_edu_goal_hierarchy.sql với backfill, sequence và FK; tạo ba entity/repository, sửa EduGoalContent; bỏ persistence cũ (FR-001/002/004).
## Phase 4: US2 — CRUD cây
- [x] T004 [US2] Sửa EduYearPlanServiceImpl đọc/lưu/xóa, validate ID/type/ownership và chuyển cha an toàn; giữ mapper/API (FR-003/004/007, SC-002/004).
- [x] T005 [US2] Regression schema/service ở EduYearPlanSchemaIntegrationTest và EduYearPlanServiceIntegrationTest cho thiếu cấp, nhiều nhóm, chuyển/xóa cha, ID sai/lặp (FR-001/003/004, SC-002/004).
## Phase 5: US3 — Phiên bản và consumer
- [x] T006 [US3] Sửa copyContent/snapshot và LearningResourceServiceImpl dùng goals repository; chỉnh fixture SQL giáo án/học liệu (FR-005/006/007).
- [x] T007 [US3] Regression copy/diff/lineage/giáo án/học liệu và snapshot OpenAPI (FR-005/006/007, SC-003).
## Phase 6: Polish & validation
- [x] T008 Cập nhật ADR, data dictionary, ERD, domain model, module card, hai file CSDL ngoài repo; ghi evidence/knowledge (FR-008).
- [x] T009 Chạy full verify, PostgreSQL thật skip 0, check-ai-layer; review độc lập theo plan L (FR-001..008, SC-001..004).
- [x] T010 Chạy converge sau implement; append task còn thiếu rồi implement/verify cho tới không còn gap, ghi quickstart và progress log (FR-001..008).
## Dependencies
T001→T002→T003→T004→T005→T006→T007→T008→T009→T010. Không deploy riêng một US; DB-1 là một phase tích hợp.
## Parallel execution
Review đọc source độc lập có thể chạy sau khi diff ổn định; không giao hai task cùng sửa một file.
## Implementation strategy
RED upgrade test trước; implement schema và service đồng bộ; regression và full gate; converge rồi sửa gap. Giữ thay đổi workspace trước task.

## Phase 7: Convergence
- [x] T011 Bổ sung fixture nâng cấp nhiều domain/phiên bản và sequence đã vượt max ID trong src/test/java/com/carenest/repository/eduplan/EduGoalHierarchyMigrationTest.java per FR-002/SC-001 và plan: rủi ro backfill (partial).

## Completion evidence — 29/10/2026
- VERIFY PASS full | tests 433 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 247s | log target/verify.log
- 11/11 tasks hoàn tất (10 task gốc + T011 convergence). Migration 5/5, PostgreSQL thật, skip 0.
- Converge 2: không còn gap; review độc lập PASS. OpenAPI không có diff.
- Chưa chạy migration lên DB đang dùng; chưa browser E2E FE; không commit/push/tạo nhánh.

## Phase 8: Delta02/11 — Spec/analyze
- [x] T012 Domain14, EDU-48 và module tại specs/006-year-plan-goal-hierarchy/domain-spec.md, docs/business/BUSINESS_RULES.md, docs/modules/education-plan.md (FR-009..012).
- [x] T013 Cập nhật spec/plan/research/data-model/contracts/checklist/quickstart tại specs/006-year-plan-goal-hierarchy và delta plan quản trị hiện có, giữ constitution/lịch sử (FR-009..015).
- [x] T014 Analyze artifacts/task/constitution, check-ai-layer, review độc lập và approval user trước code; evidence quickstart.md (FR-015/SC-008).
## Phase 9: US4 — Xóa nhánh
- [x] T015 [US4] RED CareNest_FE/tests/education-plan/goal-subdomain-delete.test.mjs: boundary đầu/giữa/cuối/duy nhất/rỗng/ID mất/cùng tên, cha tùy chọn, counts/ticks/input bất biến (FR-009..011, SC-005/006, AC-D1..D4).
- [x] T016 [US4] Helper thuần xác định nhánh trong CareNest_FE/src/models/education-plan/goalTable.js, GREEN T015, giữ normalizer (FR-009/011, SC-005, AC-D3..D5).
- [x] T017 [US4] CareNest_FE/src/components/education-plan/GoalTables.jsx: counts+ConfirmationModal danger/Hủy/xóa đúng nhánh, read-only/manual-save; regression đánh số/themeGoalIds (FR-009..012, SC-005/006, AC-D1..D6).
## Phase 10: US5 — Select ellipsis
- [x] T018 [US5] CareNest_FE/src/components/education-plan/CurriculumGoalsModal.jsx và src/styles/modules/education-plan.css: dùng cơ chế target cho domain/title, giữ value/option/filter/keyboard (FR-013/014, SC-007, AC-U1/U2).
## Phase 11: Validation/convergence bên trong D2
- [x] T019 [US4] Browser fixture tổng hợp confirm/Hủy/counts/read-only/nhánh khác/manual-save payload và save/reopen server khi có môi trường; evidence quickstart.md. Thiếu persistence thì không đánh dấu toàn task hoàn thành (FR-009..012/015, SC-005/006).
- [x] T020 [US5] Browser360/390/768/1440: một dòng/computed ellipsis/title/options/value/filter/target/keyboard, ảnh và log quickstart.md (FR-013..015, SC-007).
- [x] T021 Full FE verify, BE quick cho tài liệu, check-ai-layer hai repo, review độc lập, docs/quality/VERIFICATION.md và progress log/feature FE (FR-015).
- [x] T022 Implement→converge FR-009..015/SC-005..008/US4..5: append-only gap, implement/verify lại; vòng sạch hash tasks không đổi, evidence quickstart.md (FR-015, SC-008).
### Delta dependencies
T012→T013→T014+approval→T015→T016→T017; T018 độc lập source sau approval. T017/T018→T019/T020→T021→T022. Phase9..11 là các nhóm task trong D2 của plan đã duyệt, không thêm cổng giữa các vòng. T001..T011 và evidence lịch sử giữ nguyên.

## Phase 12: Convergence — ordinal trong xác nhận (FR-010, SC-006)
- [x] T023 [US4] Ảnh browser cho thấy callback đọc subNo mutable cuối vòng nên xóa nhánh1 lại ghi số2 trong xác nhận; giữ snapshot số thứ tự theo block trong CareNest_FE/src/components/education-plan/GoalTables.jsx, thêm assertion ordinal ở tests/e2e/system/year-plan-subdomain-delete.spec.mjs, chạy RED/GREEN browser và real E2E, verify/review/converge lại. Tên/ID/phạm vi xóa không sai, không thay intent.
