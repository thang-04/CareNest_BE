# Implementation Plan: Lấy mục tiêu năm học từ Chương trình của Bộ

**Branch**: BE `feature/G94-226-BE-FEAT-09-school-year-goals` · FE `feature/G94-178-FE-FEAT-41-lesson-assessment-parent` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

## Summary

Nạp danh mục "Kết quả mong đợi" (mục D) và "Nội dung giáo dục theo độ tuổi" (mục C) của VBHN 01/VBHN-BGDĐT cho 4 nhóm tuổi vào DB; API đọc danh mục và nguồn; FE thêm nút "Lấy từ Chương trình của Bộ" (hộp chọn 2 thẻ: Mục tiêu · Gợi ý nội dung), trang "Nguồn chương trình của Bộ", nhãn "Theo Bộ"/"Đã sửa từ Bộ". Lấy vào kế hoạch làm ở client như EDU-24, lưu qua PUT kế hoạch; mục tiêu giữ liên kết `curriculumGoalId` (đi theo phiên bản 004).

## Technical Context

Java 21 / Spring Boot 4.1.1 / PostgreSQL 17 / Flyway (V30, V31); React 18 + Vite JS; test JUnit + Testcontainers, `node:test`, Playwright 1.63. Ràng buộc: không sửa CSS/token; tái dùng `Modal`, `EmptyState`, `Notice`, bảng lớp có sẵn; lỗi qua `ApiCode`; không đè thay đổi chưa commit của 004 và bản sửa token.

## Constitution Check

`.specify/memory/constitution.md` là mẫu chưa điền ⇒ dùng AGENTS.md BE/FE làm cổng: layered (controller → service → repository), log START/END, ApiCode, không business rule ở FE ngoài mock, verify BE full + FE verify + Playwright. Đạt.

## Design

### BE (package `eduplan`)

- Migration V30 (bảng `edu_curriculum_sources`, `edu_curriculum_goals`, `edu_curriculum_goal_contents`, `edu_curriculum_contents`; cột `edu_goal_rows.curriculum_goal_id`), V31 (dữ liệu, sinh tự động, tra `age_groups.code`, `edu_domains.name`).
- Entity: `EduCurriculumSource`, `EduCurriculumGoal` (+ `@ElementCollection`? không — entity `EduCurriculumGoalContent`), `EduCurriculumContent`; `EduGoalRow.curriculumGoalId` (+ `copyFor` chép).
- Repository: 4 repo read-only (`findAllByAgeGroupIdOrderBySortOrderAsc`, `findAllByActiveTrue…`, `findAllByCurriculumGoalIdIn…`).
- Service `EduCurriculumService` + `impl/EduCurriculumServiceImpl` (`@Transactional(readOnly)`): `getSources()`, `getGoals(ageGroupId)`, `getContents(ageGroupId)`; kiểm nhóm tuổi tồn tại.
- Controller `EduCurriculumController` `/education-plans/curriculum/{sources|goals|contents}`.
- `EduYearPlanServiceImpl.saveContent`: nhận `curriculumGoalId` cho dòng goal; kiểm cùng nhóm tuổi (batch) ⇒ `CURRICULUM_GOAL_INVALID` 7030. `toDetail`: nạp câu gốc + tham chiếu theo id (batch) ⇒ `Row.curriculumGoalId/curriculumGoalText/curriculumSectionRef`.
- DTO: `CurriculumSourceResponse`, `CurriculumGoalResponse`, `CurriculumContentResponse`; sửa `YearPlanRequest.Row`, `YearPlanDetailResponse.Row`, mapper.
- Docs: openapi (snapshot), ERROR_CONTRACT không đổi bảng dải; BUSINESS_RULES EDU-28; module card; DATA_DICTIONARY.

### FE

- `models/education-plan/curriculum.mjs`: `curriculumAsSourcePlan(goals, domains)` (dựng kế hoạch nguồn ảo: sub/group/goal+contents, goal mang `curriculumGoalId`, `curriculumGoalText`, `curriculumSectionRef`); `addSuggestedContents(form, goalRowId, suggestions)`; `curriculumBadge(row)`.
- `models/education-plan/importGoals.mjs`: giữ `curriculumGoal*` khi chép mục tiêu (EDU-24 + Bộ).
- `services/education-plan/api/educationPlanApi.js` + `mock` + facade: `getCurriculumSources`, `getCurriculumGoals(ageGroupCode)`, `getCurriculumContents(ageGroupCode)`; mock đọc `src/mocks/curriculumSeed.json` (sinh từ cùng dữ liệu).
- `yearPlanMapper.js`: map 3 trường về UI, gửi `curriculumGoalId`.
- `components/education-plan/CurriculumGoalsModal.jsx` (thẻ Mục tiêu · Gợi ý nội dung, link trang nguồn), `CurriculumBadge` trong `GoalTables.jsx`.
- `pages/education-plan/CurriculumSourcesPage.jsx`, route `/education/curriculum-sources` (mọi vai trò kế hoạch giáo dục), mục menu con "Nguồn chương trình của Bộ".
- `GoalFormPage.jsx`: nút cạnh "Lấy từ kế hoạch có sẵn".

### Test

- BE: `EduCurriculumServiceIntegrationTest` (số dòng mỗi nhóm tuổi, mẫu nguyên văn, nguồn), `EduCurriculumControllerTest` (envelope, 404), `EduYearPlanServiceIntegrationTest` (lưu/đọc liên kết, 7030, phiên bản mới giữ liên kết), OpenApi snapshot.
- FE: `tests/education-plan/curriculum.test.mjs` (ghép, trùng, gợi ý, nhãn), e2e `IT-17..` (API) + `ST-13` (ban hành → phiên bản mới → lấy từ Bộ → gửi → ban hành; trang nguồn).

## Progress log

- 2026-10-28: specify, clarify (Tiền chốt), dữ liệu tách xong (327 MT, 423 gợi ý).
- 2026-10-28: plan, data-model, tasks, analyze (A1 MEDIUM đã sửa ⇒ sạch). Implement T001–T016: V30/V31, API `/education-plans/curriculum/*`, mã lỗi 7030, FE nút/hộp chọn/nhãn/trang nguồn.
- 2026-10-28: BE verify full 424/424 test pass, ArchUnit/OpenAPI ok, spotless sạch (chạy ở repo thật). FE verify PASS.
- 2026-10-28: converge vòng 1 thêm C001 (BE test phiên bản mới giữ liên kết) và C002 (lọc lĩnh vực con ở Gợi ý nội dung). E2E mới: `tests/e2e/integration/edu-curriculum-api.spec.mjs` (IT-17, IT-18), `tests/e2e/system/edu-plan-version-ministry.spec.mjs` (ST-13..15, gồm T035 của 004); chạy trên BE dựng từ working copy ở :8081 + DB bản sao `carenest_e2e005` (BE IntelliJ :8080 chưa có V30/V31).
- Giả định còn mở: link gốc chính thức `official_url` (vbpl.vn) NEEDS VERIFICATION — team đối chiếu, sửa bằng migration mới nếu cần.
- 2026-10-28: Playwright bắt 2 lỗi: (1) lĩnh vực có mục tiêu tự gõ ngoài lĩnh vực con không nhận mục tiêu Bộ có lĩnh vực con ⇒ hỏi Tiền, chốt A (giữ từ chối, báo lý do) — C003; (2) phiên bản V2 bị coi trùng nhóm tuổi (lỗi 004) — C004 đã sửa. Kết quả cuối: integration 18/18, system 31/31 (ST-08 chập chờn 1 lần, chạy lại pass). Converge vòng 2: không còn thiếu sót.
