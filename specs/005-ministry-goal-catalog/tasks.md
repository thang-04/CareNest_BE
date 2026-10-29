# Tasks: Lấy mục tiêu năm học từ Chương trình của Bộ

**Input**: spec.md, plan.md, data-model.md, contracts/curriculum-catalog.md, research.md

## Phase 1: Dữ liệu & DB

- [X] T001 Migration `src/main/resources/db/migration/V30__add_edu_curriculum_catalog.sql` theo data-model.md (4 bảng, FK, unique, index, cột `edu_goal_rows.curriculum_goal_id`).
- [X] T002 Sinh `V31__seed_edu_curriculum_vbhn_01_2021.sql` từ dữ liệu đã tách (1 nguồn, 327 mục tiêu + nội dung, 423 gợi ý), tra `age_groups.code`/`edu_domains.name`, nguyên văn, có `section_ref`.
- [X] T003 [P] Entity + repository: `EduCurriculumSource`, `EduCurriculumGoal`, `EduCurriculumGoalContent`, `EduCurriculumContent`; `EduGoalRow.curriculumGoalId` (+ `copyFor`).

## Phase 2: BE API (US1, US2)

- [X] T004 DTO `CurriculumSourceResponse`, `CurriculumGoalResponse`, `CurriculumContentResponse`.
- [X] T005 `EduCurriculumService` + `impl/EduCurriculumServiceImpl` (đọc theo nhóm tuổi, 404 nhóm tuổi không tồn tại).
- [X] T006 `EduCurriculumController` `/education-plans/curriculum/{sources,goals,contents}` (PRINCIPAL, VICE_PRINCIPAL, TEACHER).
- [X] T007 `ApiCode.CURRICULUM_GOAL_INVALID(7030)`; `YearPlanRequest.Row.curriculumGoalId`; `YearPlanDetailResponse.Row.curriculumGoalId/curriculumGoalText/curriculumSectionRef`; `EduYearPlanServiceImpl.saveContent` kiểm + lưu; `toDetail`/mapper trả câu gốc.
- [X] T008 Test: `EduCurriculumServiceIntegrationTest` (đếm 41/84/99/103 MT, 77/119/121/106 gợi ý, câu mẫu nguyên văn, nguồn), `EduCurriculumControllerTest`, `EduYearPlanServiceIntegrationTest` (lưu/đọc liên kết, 7030, phiên bản mới giữ liên kết).
- [X] T009 Docs BE: openapi snapshot, BUSINESS_RULES EDU-28, modules/education-plan.md, DATA_DICTIONARY, flows/education-plan.md.

## Phase 3: FE (US1–US4)

- [X] T010 `src/mocks/curriculumSeed.js` (cùng dữ liệu; module JS vì node:test không nạp JSON qua alias) + mock repo + api + facade `getCurriculumSources/Goals/Contents`.
- [X] T011 `models/education-plan/curriculum.mjs` (kế hoạch nguồn ảo, thêm gợi ý nội dung, nhãn) + `importGoals.mjs` giữ `curriculumGoal*`; `yearPlanMapper.js` map trường mới; `EDU_ERROR_CODE.CURRICULUM_GOAL_INVALID`.
- [X] T012 `components/education-plan/CurriculumGoalsModal.jsx` (thẻ Mục tiêu: lĩnh vực > lĩnh vực con > nhóm > MT + nội dung, chọn từng/nhóm/lĩnh vực/tất cả, số đã chọn; thẻ Gợi ý nội dung: chọn MT đích, lọc lĩnh vực, tích gợi ý; link trang nguồn; loading/empty/error).
- [X] T013 `GoalFormPage.jsx`: nút "Lấy từ Chương trình của Bộ"; dùng `mergeImportedGoals`; báo kết quả.
- [X] T014 `GoalTables.jsx`: nhãn "Theo Bộ"/"Đã sửa từ Bộ" (link tới trang nguồn đúng mục).
- [X] T015 `pages/education-plan/CurriculumSourcesPage.jsx` + route `/education/curriculum-sources` + mục menu; thông tin văn bản + danh mục theo nhóm tuổi (MT + gợi ý), neo `#cg-{id}`.
- [X] T016 Test `tests/education-plan/curriculum.test.mjs` (ghép, trùng, giữ liên kết qua EDU-24, gợi ý nội dung, nhãn).

## Phase 4: Kiểm chứng

- [X] T017 e2e integration: API danh mục (đếm theo nhóm tuổi, 404), lưu kế hoạch với `curriculumGoalId` sai nhóm tuổi ⇒ 7030.
- [X] T018 e2e system: kế hoạch đã ban hành → tạo phiên bản mới (004) → Lấy từ Chương trình của Bộ (MT + gợi ý nội dung) → Lưu → gửi → Phó HT ban hành; nhãn "Theo Bộ"; trang nguồn mở đúng mục; kèm T035 của 004.
- [X] T019 `node scripts/verify.mjs` BE (full) + FE; Playwright toàn bộ trên BE từ working copy (DB carenest_merge).

## Converge (speckit-converge, 2026-10-28)

- [X] C001 [US1] BE test: phiên bản điều chỉnh (EDU-25) chép cả liên kết nguồn của Bộ — `EduYearPlanServiceIntegrationTest.curriculumGoalLinkKeptAndValidated` (FR-005)
- [X] C002 [US4] FE: thẻ Gợi ý nội dung lọc thêm theo lĩnh vực con — `CurriculumGoalsModal` (`#cur-sub`) (FR-012)
- [X] C003 [US1] Quyết định A (Tiền 28/10): lĩnh vực có mục tiêu tự gõ ngoài lĩnh vực con ⇒ không thêm mục tiêu của Bộ có lĩnh vực con, báo lý do — ghi spec + BUSINESS_RULES EDU-28; test FE `curriculum.test.mjs` (ca từ chối)
- [X] C004 [004 T035] FE: phiên bản điều chỉnh bị coi là "trùng nhóm tuổi" ⇒ không sang bước 2, không Lưu được — `GoalFormPage` bỏ kiểm trùng khi sửa kế hoạch đã có cùng năm học/nhóm tuổi (Playwright ST-14 bắt được)
