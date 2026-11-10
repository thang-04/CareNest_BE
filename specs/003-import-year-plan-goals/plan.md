# Implementation Plan: Lấy mục tiêu từ kế hoạch năm có sẵn

**Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Feature directory**: specs/003-import-year-plan-goals. Giá trị BRANCH do Spec Kit trả về là mã feature 003-import-year-plan-goals; branch Git thực tế vẫn như trên, không tạo/chuyển nhánh.

**Status**: Implemented and converged, 22/22 tasks and 13/13 real-BE E2E; user-authorized execution tracked in the [làn L plan](../../docs/plans/completed/2026-10-28-import-year-plan-goals.md).

## Summary

Hoàn thiện chức năng Lấy từ kế hoạch có sẵn ở bước 2: danh sách nguồn độc lập năm đang chọn ở header, tìm đủ trang, tích từng mục tiêu/theo lĩnh vực/tất cả bằng ID ổn định, đọc lại nguồn trước xác nhận rồi ghép mục tiêu và nội dung vào bản đang soạn. Giữ ngữ cảnh phân cấp, sinh ID mới, không chép chủ đề/đợt nghỉ/dấu tích. Lưu tay và khóa phiên dùng nguyên luồng hiện hành.

Triển khai chính ở CareNest_FE, một nhóm màn education-plan. Dùng lại list/detail và lưu toàn văn bản của BE; không thêm endpoint, DTO, dependency, migration, quyền mới hoặc luồng preview inline.

## Technical Context

**Language/Version**: Web JavaScript/JSX, React 18.3.1; BE Java 21, Spring Boot 4.1.1 chỉ là contract đang dùng.
**Primary Dependencies**: Vite 8.3.3, react-router-dom 7, axios 1.x, UI kit nội bộ, useAsync; giữ package/lock hiện có.
**Storage**: Nội dung bền vững qua dịch vụ kế hoạch năm và PostgreSQL hiện hành; lựa chọn/modal trong React state; bản chưa lưu qua cơ chế phục hồi hiện có. Không thêm storage.
**Testing**: node:test + auth-test-loader; Playwright 1.63 hiện có, real-BE E2E chạy riêng; verify BE/FE và check-ai-layer.
**Target Platform**: Web desktop/tablet, mobile responsive theo DESIGN.md; 1 trường/2 điểm trường.
**Project Type**: React SPA dùng Spring REST service hiện có.
**Performance Goals**: SC-006: 200 mục tiêu, tích/chọn tất cả phản hồi ≤1s sau khi tải; list chỉ tải summary, detail chỉ tải nguồn chọn và tái đọc khi xác nhận.
**Constraints**: EDU-06/10/22/23/24; không sửa API client chung/auth; không ghi trước Lưu; không ghi đè bản mới hơn; không log nội dung kế hoạch/dữ liệu riêng.
**Scale/Scope**: Một modal và handler bước 2, tối đa một nguồn mỗi lần thêm; phân trang list đến totalPages, không tăng size vượt 200. Dự kiến 7 file source và 4 file test, tách 3 phase có review; xem active plan.

## Constitution Check

Constitution tại .specify/memory/constitution.md vẫn là template chưa ban hành; không coi các placeholder là nguyên tắc đã duyệt. AGENTS.md và business rules là cổng bắt buộc hiện hành. Không sửa constitution trong task này.

| Gate | Trước research | Sau thiết kế |
| --- | --- | --- |
| Hai quyết định A/A đã ghi, không còn NEEDS CLARIFICATION | Đạt | Đạt: FR-007/008, EDU-24 |
| Giữ quyền server, không multi-school | Đạt | Đạt; giới hạn import so với vai trò kép được nêu ở research |
| Giữ phiên/revision/manual-save | Đạt | Đạt; không đổi hook lưu hoặc headers |
| Không thêm dependency, endpoint, DB/schema | Đạt | Đạt; dùng GET list/detail và POST/PUT hiện có |
| Source qua facade, mock đúng phạm vi, UI kit hiện có | Đạt | Đạt theo thiết kế, cần kiểm chứng khi code |
| Không code trước duyệt plan làn L | Đạt | Đạt; user chỉ đạo triển khai vòng lặp 28/10, active plan approved |
| Không nhánh/commit/push tự động | Đạt | Đạt; Jira riêng cần chốt nếu user yêu cầu commit |

## Project Structure

### Documentation (this feature)

```text
specs/003-import-year-plan-goals/
  spec.md
  plan.md
  research.md
  data-model.md
  contracts/source-import.md
  quickstart.md
  checklists/requirements.md
```

Tasks sẽ tạo bằng speckit-tasks, không tạo ở lượt plan này. Plan duyệt và Progress log nằm trong docs/plans/completed/2026-10-28-import-year-plan-goals.md để đúng cổng repo.

### Source Code (repository root)

```text
CareNest_FE/
  src/components/education-plan/ImportGoalsModal.jsx           M
  src/pages/education-plan/GoalFormPage.jsx                     M
  src/services/education-plan/educationPlanService.js          M
  src/services/education-plan/api/educationPlanApi.js          M
  src/services/education-plan/mock/educationPlanMockRepository.js M
  src/hooks/education-plan/useGoalImportSources.js             C
  src/models/education-plan/importGoals.mjs                    C
  tests/education-plan/year-plan-api.test.mjs                  M
  tests/education-plan/import-goals.test.mjs                   C
  tests/education-plan/import-goal-sources.test.mjs            C
  tests/e2e/system/year-plan-import.spec.mjs                   C
CareNest_BE/
  source Java / migration / openapi: không sửa
```

**Structure Decision**: Hai repo hiện có; chỉ thêm hook đọc nguồn và helper biến đổi nội dung trong module Web. Không thay getAll() toàn cục, provider, mapper chung hoặc normalizeDomains/toRows/nestUnderFirstSub. Danh sách import gọi dịch vụ riêng để tránh eager-load mọi detail và không thay baseline cache ghi known.

## Phase 0 — Research

[research.md](research.md) ghi bằng chứng và quyết định về phân trang, scope, rows thay items, revalidation, dữ liệu cha thiếu, mock, concurrency và test. Không còn unknown chặn thiết kế.

## Phase 1 — Design & Contracts

- [data-model.md](data-model.md): summary nguồn, selection, hierarchy, kết quả ghép và invariants.
- [contracts/source-import.md](contracts/source-import.md): đọc nguồn bằng contract hiện hành, facade/mock và hành vi modal/ghép.
- [quickstart.md](quickstart.md): điều kiện, lệnh test và ma trận chấp nhận; không phải bằng chứng tính năng đã chạy.
- [Active plan](../../docs/plans/completed/2026-10-28-import-year-plan-goals.md): AC → 3 phase → test → gate và rollback; triển khai theo chỉ đạo vòng lặp user 28/10 sau analyze sạch.

## Complexity Tracking

Không có ngoại lệ kiến trúc cần ADR. Số file >8 xử lý bằng 3 phase nhỏ trong cùng tính năng, dừng review ở từng phase. Không thêm cơ chế kiểm provenance nguồn vào server; nếu cần bảo đảm nguyên tử nguồn/đích hoặc chính sách import riêng ở BE thì đó là thay đổi contract phải lập kế hoạch/duyệt riêng.
