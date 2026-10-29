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
