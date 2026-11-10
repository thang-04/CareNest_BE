# Implementation plan — Feature010

Status: chế độ tự chạy (CLAUDE.md, user chốt 23/10): agent tự viết plan, làm tiếp, ghi quyết định cho user soát. Quản trị phase, AC, key decisions: [plan quản trị](../../docs/plans/active/2026-11-04-health-warnings-book.md). Client plan: `FE:docs/plans/active/2026-11-04-health-warnings-book.md`. Branch BE `feature/G94-180-BE-FEAT-43-child-health-pickup` (không tạo branch).

## Technical context
Java 21, Spring Boot, PostgreSQL 17; module health. Không DB/dependency mới. Dữ liệu dẫn xuất tính khi đọc (ADR-0005). Constitution: clone không có `.specify/`, áp AGENTS.md.

## Design
- `WhoBmiForAge` (static, `service/health/impl`): nạp `health/who-bmi-for-age.csv` một lần, kiểm đủ tháng 0–84 cho hai giới tính; `classify(gender, ageMonths, heightCm, weightKg)` → mức + z-score.
- `HealthRecords`: bỏ phiếu theo dõi khỏi snapshot; `bmiOf` cho lần đo không có phiếu khám; `views` nhận giới tính; `Snapshot.latestExam`, `latestBmi`.
- `HealthCheckServiceImpl`: `Results.bmi` (mức BMI theo lần đo, đọc ngày sinh/giới tính một lần), `Progress.bmiCounts`, `watchList` theo cả mức theo dõi và mức BMI.
- `HealthExamServiceImpl`: bỏ `HealthFollowUpTracker`; mẫu kết luận FOLLOW_UP chỉ cho phiếu cũ đã kết luận.
- Xóa controller/service/entity/repository/DTO/enum phiếu theo dõi, `HealthImageStorage`, cấu hình `carenest.health-media`.

## Phases
1. BE + tri thức + snapshot + verify (plan quản trị Phase 1).
2. FE theo client plan.

## Validation
Test map AC trong `tasks.md`. Verify `node scripts/verify.mjs`; kết quả ở `quickstart.md`.
