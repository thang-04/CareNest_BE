---
title: Bảng đánh giá học kỳ theo lĩnh vực và cuối năm lũy kế
status: in-progress
owner: Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [learning-observation]
rules: [OBS-25, OBS-27, OBS-28]
risk: high
created: 2026-11-05
updated: 2026-11-05
---
# Bảng học kỳ và cuối năm lũy kế

## Context
User duyệt ba ảnh và chốt kỳ I/II riêng, cuối năm cả năm, tỷ lệ 50% hai tầng ngày 05/11/2026. API cũ ChildEvaluationServiceImpl/evaluations chỉ có nhận xét từng trẻ, chưa có bảng lớp/lĩnh vực.

## Quyết định đã chốt
Thời gian học kỳ lấy cấu hình HT; lĩnh vực/MT theo kế hoạch duyệt. Học kỳ đếm hoạt động theo ngưỡng 50%. Cuối năm được user chốt lại 09/11/2026: đếm kết quả từng chủ đề có gắn MT, ngưỡng 50%; giữ dữ liệu đã chốt, không sửa APP, không commit/push.

## Làm rõ nghiệp vụ
GV lớp ghi/xác nhận; BGH/tổ trưởng đọc theo scope hiện có. Kỳ II không lũy kế, phiếu năm lũy kế. Trình bày theo ba ảnh user đã duyệt, không thêm bước BGH duyệt.

### Câu hỏi mở
Không còn câu hỏi chặn. User đã yêu cầu “làm đi bạn” sau khi giải thích đầy đủ phạm vi API/bảng lưu ngày 05/11/2026.

## Acceptance criteria
AC1 thời gian lấy cấu hình, AC2 tỷ lệ 50% hai tầng, AC3 lịch sử chuyển lớp/lineage và lưu sửa đúng, AC4 snapshot, AC5 role/scope, AC6 print/xlsx cùng số liệu, AC7 bảo toàn dữ liệu cũ, AC8 năm lũy kế. Chi tiết tại specs/semester-domain-assessment/domain-spec.md.

## Key decisions
Thêm bảng semester_assessments để lưu note/trạng thái/snapshot lớp-kỳ thay vì xác nhận tuần tự từng trẻ. Giữ API nhận xét cũ làm lịch sử. Phiếu cuối năm dùng các bảng cuối chủ đề (snapshot nếu đã xác nhận, kết quả hiện tại nếu chưa) và gộp lịch sử chuyển lớp cùng nhóm tuổi. Không dùng FE làm backend giả.

## Phases
### Phase 1 — BE nguồn dữ liệu và snapshot
Migration bảng lớp-kỳ, DTO/controller/service/query/test; trích tổng hợp mục tiêu dùng cho học kỳ và phiếu năm. Exit node scripts/verify.mjs; check-ai-layer; dừng review.
### Phase 2 — FE ba form, bản in và kiểm chứng
Facade/hook/bảng học kỳ, tiêu đề chung, bản in/xlsx, leave guard, modal căn cứ; giữ shell/token. Exit node scripts/verify.mjs; browser GV/PHT/HT, check-ai-layer; dừng review.

## Test matrix
AC1..8 → tests integration BE và FE view/print/export, browser, full verify; chưa chạy phần mới.

## Rủi ro & rollback
Lớp đã có nhận xét kỳ cũ vẫn giữ, không chuyển thành bảng xác nhận tự động. Kế hoạch sửa hoặc chuyển lớp không đổi snapshot. Migration additive, không drop. Quy mô >8 file chia hai phase review.

## Validation log
Đã đọc template domain, workflow feature-spec-loop, DTO EvaluationDtos và YearEndSheetServiceImpl, ThemeEvaluationServiceImpl, màn FE thực tế. Contract là đề xuất chưa sử dụng trong source.

## Progress log
### 2026-11-05 — Chờ duyệt kỹ thuật
Bốn artifact domain/contract/data/tasks đã viết; chưa tạo migration hoặc endpoint.

### 2026-11-05 — Triển khai
- User đã duyệt sau giải thích ba phần, bắt đầu implementation theo contract.

### 2026-11-05 — Kết quả triển khai
Đã có migration, API, giao diện, in/Excel và tổng hợp cả năm. Review scope guard đã xử lý. BE full 1044 test đạt, FE full đạt, browser bản thử tới đăng nhập. Chờ review người dùng.

### 2026-11-09 — Điều chỉnh nguồn kết quả cuối năm
- User chốt cuối chủ đề dùng quy tắc any-met; vắng/không có lần Đạt là Chưa đạt.
- User chốt MT cuối năm Đạt khi đạt ở ít nhất 50% chủ đề có gắn MT, thay tổng hợp trực tiếp các hoạt động cả năm.
- Giữ snapshot phiếu đã xác nhận và tính cả lớp cũ cùng năm/nhóm tuổi.
