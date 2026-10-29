# Implementation Plan: Phiên bản kế hoạch năm và kế hoạch điều chỉnh, bổ sung

**Branch**: BE `feature/G94-226-BE-FEAT-09-school-year-goals`, FE `feature/G94-178-FE-FEAT-41-lesson-assessment-parent` (giữ nguyên, không commit) | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Status**: Implemented + converged (T001–T034, C001–C005); T035 Playwright chưa chạy — làn L (migration, API contract, 2 repo). Chế độ tự chạy (CLAUDE.md 23/10): không chờ duyệt; giả định ghi ở spec Assumptions và Progress log bên dưới.

## Summary

(A) Thêm cột `adjustments` (JSONB, ngày → văn bản) cho giáo án tuần và endpoint riêng ghi nó ở mọi trạng thái, để không đụng nội dung đã duyệt. (B) Kế hoạch năm thành chuỗi phiên bản: mỗi phiên bản là một dòng `edu_year_plans` (`version_no`, `source_plan_id`), các dòng con mang `lineage_id`; endpoint tạo phiên bản (chép cả cây) và so sánh theo lineage. Tài liệu lớp gắn phiên bản đã có sẵn (`year_plan_id`); thêm `source_doc_id`, khóa tuần có hậu tố cho phiên bản ≥2, chặn ghi trên phiên bản đã thay thế và chặn gửi khi bản chép còn tham chiếu sai/chưa kiểm tra. Web: chọn phiên bản hiện hành, nút tạo phiên bản, lịch sử phiên bản, màn so sánh, "Tạo bản mới" (mẫu/chép) với remap + ô đỏ, ô điều chỉnh trong kế hoạch ngày; mock repo làm BE giả tương ứng.

## Technical Context

**Language/Version**: BE Java 21, Spring Boot 4.1.1, JPA, Flyway, PostgreSQL 17; FE React 18.3, Vite, JS/JSX.
**Primary Dependencies**: có sẵn; không thêm dependency.
**Storage**: Migration mới `V29__add_edu_plan_versions.sql` ([data-model.md](data-model.md)). Không sửa V8–V28.
**Testing**: BE `mvnw verify` (unit + integration Testcontainers nếu Docker có) qua `node scripts/verify.mjs`; FE `node --test` qua `node scripts/verify.mjs`; Playwright real-BE nếu dev stack chạy.
**Target Platform**: Web; Mobile không đổi.
**Performance Goals**: Tạo phiên bản với 300 MT/1000 nội dung < 2 s; so sánh < 500 ms (một truy vấn mỗi bảng, ghép trong bộ nhớ).
**Constraints**: Không đổi style/token (dùng `--color-danger*`/class lỗi có sẵn); giữ lưu tay + bản giữ ẩn (EDU-22); không ghi tên AI; không chạm `.env`, KEPT-LOCAL.
**Scale/Scope**: 1 trường, ≤ 6 nhóm tuổi/năm, ≤ 5 phiên bản/chuỗi.

## Constitution Check

Constitution là template chưa ban hành; cổng thực tế = AGENTS.md + BUSINESS_RULES.

| Gate | Kết quả |
| --- | --- |
| Rule nghiệp vụ có nguồn | Đạt: Tiền chốt 28/10 (coordinator) + 7 mặc định; cập nhật EDU-01/03/12, thêm EDU-25/26 |
| Status do BE quyết định | Đạt: tạo phiên bản/so sánh/chặn gửi ở BE; FE chỉ hiển thị |
| Migration mới, không sửa cũ | Đạt: V29 |
| Contract đổi có liệt kê tác động FE/APP | Đạt: contracts/; APP không dùng education-plan |
| Không dependency mới | Đạt |
| Không commit/push/branch | Đạt |

## Project Structure

```text
specs/004-year-plan-versioning/{spec,plan,research,data-model,quickstart,tasks}.md, contracts/api.md, checklists/requirements.md

CareNest_BE/
  src/main/resources/db/migration/V29__add_edu_plan_versions.sql              C
  entity/eduplan/{EduYearPlan,EduGoalRow,EduGoalContent,EduTheme,EduBreak,EduClassDoc}.java  M
  dto/eduplan/{YearPlanDetailResponse,YearPlanSummaryResponse}.java           M
  dto/eduplan/{YearPlanChangesResponse,ClassDocAdjustmentsRequest}.java       C
  repository/eduplan/{EduYearPlanRepository,EduClassDocRepository}.java       M
  service/eduplan/{EduYearPlanService,EduClassDocService}.java                M
  service/eduplan/impl/{EduYearPlanServiceImpl,EduClassDocServiceImpl}.java   M
  service/eduplan/impl/YearPlanVersionDiff.java                               C (so sánh thuần, test unit)
  mapper/eduplan/EduYearPlanMapper.java                                       M
  controller/eduplan/{EduYearPlanController,EduClassDocController}.java       M
  utils/ApiCode.java                                                          M (7027–7029, 7121–7123)
  test: YearPlanVersionDiffTest C, EduYearPlanServiceIntegrationTest M, EduClassDocServiceIntegrationTest M
  docs: BUSINESS_RULES, modules/education-plan, flows/education-plan, DATA_DICTIONARY, api/openapi.yaml, ERROR_CONTRACT

CareNest_FE/
  src/models/education-plan/planVersions.mjs            C (chọn bản hiện hành, remap, ô đỏ — thuần, test)
  src/models/education-plan/classPlan.js                M (khóa tuần theo phiên bản, ngày điều chỉnh)
  src/services/education-plan/api/{educationPlanApi,yearPlanMapper}.js   M
  src/services/education-plan/mock/{educationPlanMockRepository,classPlanMockRepository}.js M
  src/services/education-plan/educationPlanService.js   M
  src/hooks/education-plan/useEducationPlan.jsx         M
  src/components/education-plan/{planUi,lessonShared,WeekPlanBody,PlanWidgets}.jsx M
  src/components/education-plan/VersionWidgets.jsx      C (thông báo phiên bản, tạo bản mới, ô đỏ)
  src/pages/education-plan/{GoalDetailPage,GoalListPage,BranchPlanPage,ClassThemePlanPage,ThemeDossierPage,WeekPlanPage,ClassPlanPage}.jsx M
  src/utils/education-plan/classPlanDoc.js              M (in ô điều chỉnh)
  src/services/assessment/mock/assessmentMockRepository.js M (ưu tiên phiên bản mới nhất)
  tests/education-plan/plan-versions.test.mjs           C
```

**Structure Decision**: Mỗi phiên bản là một kế hoạch đầy đủ ⇒ tái dùng toàn bộ luồng sửa/gửi/ban hành/phiên sửa, tài liệu lớp tự tách theo `year_plan_id`. Logic so sánh BE ở một class thuần (dễ test); remap/ô đỏ FE ở model thuần, BE kiểm lại khi gửi.

## Phase 0 — Research
[research.md](research.md): R1 phiên bản = dòng mới vs bảng snapshot; R2 lineage nullable; R3 nơi lưu ô điều chỉnh; R4 khóa giáo án tuần; R5 chặn gửi ở BE; R6 phạm vi "đổi"; R7 phiên bản bị thay thế.

## Phase 1 — Design
- [data-model.md](data-model.md): thiết kế DB V29 + invariant.
- [contracts/api.md](contracts/api.md): 3 endpoint mới, field mới, mã lỗi.
- [quickstart.md](quickstart.md): lệnh test và ma trận chấp nhận.

## Complexity Tracking

| Điểm | Lý do | Phương án đơn giản hơn bị loại |
| --- | --- | --- |
| >8 file, 2 repo | Yêu cầu chạm cả kế hoạch năm và 4 loại tài liệu lớp | Không tách được mà vẫn có ô đỏ end-to-end |
| Kiểm ô đỏ ở cả FE và BE | FE cần hiển thị từng ô; BE phải chặn gửi (status BE quyết) | Chỉ FE ⇒ gọi API trực tiếp lách được (SC-006) |

## Progress log

- 2026-10-28: specify + clarify (7 mặc định + A1/A2 tự chọn). Plan, research, data-model, contracts viết theo chế độ tự chạy.
- 2026-10-28: analyze 2 lượt (3 điểm lượt 1 đã sửa, lượt 2 sạch — analysis.md).
- 2026-10-28: implement BE (V29, entity, service, controller, ApiCode, test) + FE (planVersions.mjs, mock/API/hook, VersionWidgets, 6 trang). Snapshot OpenAPI cập nhật có chủ đích.
- 2026-10-28: converge lượt 1 thêm C001–C005 (chặn gửi ở Web, hồ sơ chủ đề chỉ xem trên phiên bản cũ, xem bản cũ qua ?plan=, docs, in ô điều chỉnh); lượt 2 thêm chặn học liệu trên phiên bản cũ (requireThemeAccess), không còn mục thiếu.
- Kiểm chứng: BE `node scripts/verify.mjs` PASS (414 test trước converge 2; chạy lại sau converge 2), FE `node scripts/verify.mjs` PASS, check-ai-layer BE 0 lỗi / FE 0 lỗi. Playwright: dev stack đang chạy nhưng BE là tiến trình IntelliJ chạy code cũ (chưa V29) và thiếu biến E2E_DEV_PASSWORD ⇒ không chạy.
- Giả định cần Tiền soát: A1–A8 trong spec (đặc biệt A1 tài liệu phiên bản cũ chỉ xem, A2 cách hết đỏ, A4 khóa tuần có hậu tố id kế hoạch).

