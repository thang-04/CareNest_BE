# Implementation plan — Feature011

Status: chế độ tự chạy (CLAUDE.md, user chốt 23/10): agent tự viết plan, làm tiếp, ghi quyết định cho user soát. Quản trị phase, AC, key decisions: [plan quản trị](../../docs/plans/active/2026-11-04-health-growth-report.md). Branch BE `feature/G94-180-BE-FEAT-43-child-health-pickup` (không tạo branch).

## Technical context
Java 21, Spring Boot, PostgreSQL 17; module health. Không DB/dependency mới. Dữ liệu dẫn xuất tính khi đọc (ADR-0005). Constitution: clone không có `.specify/`, áp AGENTS.md.

## Design
- `WhoLmsTable` (`service/health/impl`): nạp CSV L, M, S theo tháng 0–84 một lần, kiểm đủ tháng hai giới tính; z-score LMS (tùy chọn giới hạn đuôi), làm tròn 2 chữ số. `WhoBmiForAge` dùng lại (API cũ giữ nguyên); thêm `WhoWeightForAge` (giới hạn đuôi), `WhoHeightForAge` (công thức thường) + enum `WeightForAgeStatus`, `HeightForAgeStatus`.
- `HealthGrowthReportServiceImpl`: phạm vi `SchoolStructureService.listCampuses` ∩ `HealthCheckRules.canManage`; đợt/kỳ của tháng từ `HealthCheckCampaignRepository.findByCampusIdInOrderByStartDateDescIdDesc`; lần đo, vắng một lần mỗi điểm trường; lớp, trẻ bằng `HealthCheckRules.classesOf`/`rosterOf`; hợp theo trẻ, xếp mức, cộng tổng; cấp học từ `AgeGroupService.getAgeGroups`.
- `HealthCheckController`: `GET /health-checks/growth-report` (HT, PHT), `month` là `YearMonth` (`@DateTimeFormat yyyy-MM`).
- DTO `HealthGrowthReportResponse` (record lồng tên riêng toàn hệ thống), enum `HealthGrowthLevel`.

## Phases
1. BE + tri thức + snapshot + verify (plan quản trị Phase 1).
2. FE (agent FE).

## Validation
Test map AC trong `tasks.md`. Verify `node scripts/verify.mjs`; kết quả ở `quickstart.md`.
