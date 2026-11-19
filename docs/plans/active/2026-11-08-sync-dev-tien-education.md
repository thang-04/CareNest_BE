---
title: Đồng bộ dev và ghép nhánh kế hoạch giáo dục của Tiền
status: draft
owner: Cần
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [education-plan, learning-observation]
rules: [EDU-06, OBS-25, OBS-28]
risk: high
created: 2026-11-08
updated: 2026-11-08
---

# Đồng bộ dev và ghép nhánh kế hoạch giáo dục của Tiền

## Context

User yêu cầu kiểm tra và sửa tình trạng merge nhánh Tiền cùng dev ngày 08/11/2026. Nhánh FE là feature/G94-179-FE-FEAT-42-school-config-api, BE là feature/G94-236-BE-FEAT-29-daily-attendance.

## Phần đã thực hiện

- Fetch origin, fast-forward FE từ 47b4b83 lên bbc4021 và BE từ e89f30d lên 0090280. Cả hai HEAD bằng origin/dev tại lúc đối chiếu.
- Backup toàn bộ thay đổi tracked/staged/untracked bằng stash riêng từng repo. Áp lại giữ code đang sửa; xung đột ISSUE_INDEX FE đã gộp đủ các dòng hai phía. Không còn unmerged paths.
- Backup FE 7f3043bc4d3f1c6639eeba7d203b23e508c8b47a, BE 8bf415c92672553aaf40d1c7727131cec6afd092 vẫn được giữ.

## Phần chờ duyệt trước merge Tiền

- FE origin/feature/G94-178-FE-FEAT-41-lesson-assessment-parent tại ba5c6ec, còn hai commit chưa vào HEAD.
- BE origin/feature/G94-226-BE-FEAT-09-school-year-goals tại 5780111, còn hai commit chưa vào HEAD.
- Bản xem trước merge có xung đột tài liệu và OpenAPI, chưa báo source conflict. Local DB đã áp V80 child_enrollment_photos, V81 selected_parent_account, V82 semester_assessment_boards, V83 year_end_domain_comments.
- Nhánh Tiền thêm V80 expand_edu_workflow_policy, V81 contract_edu_workflow_policy, V82 harden_edu_workflow_cutover. Ghép nguyên tên sẽ có Flyway duplicate version.

## Key decisions đề xuất

- Giữ nguyên số và checksum của V80–V83 đã áp trong DB local và các migration cũ của main/dev.
- Ba migration Tiền chưa áp vào DB này sẽ nhận số mới V84, V85, V86 theo đúng thứ tự expand → contract → harden. Rà các test/script/spec tham chiếu đúng tên trước khi chạy.
- Ghép đầy đủ code giáo dục FE/BE của Tiền, giữ các sửa điểm danh/học kỳ/cuối năm hiện tại. Nếu phát hiện source conflict nghiệp vụ, trình các phần chồng lấn thay vì chọn nguyên repo.
- OpenAPI sinh lại từ source kết hợp, giữ endpoint hai phía; không chọn bỏ toàn bộ một bản snapshot.
- Không tự áp migration vào DB người dùng hoặc triển khai production trong giai đoạn review này.

## Acceptance criteria

- AC-1: origin/dev và hai nhánh Tiền là ancestor của kết quả merge sau khi hoàn tất.
- AC-2: Không mất thay đổi staged/unstaged/untracked trước đồng bộ; backup vẫn truy cập được.
- AC-3: Không có migration trùng version hoặc đổi migration V80–V83 đã áp; test nâng cấp từ DB có V83 chạy được.
- AC-4: Kiểm GV/Tổ trưởng đọc kế hoạch đã ban hành, chọn mục tiêu soạn chủ đề/giáo án; các thao tác PHT/HT theo policy incoming và scope BE.
- AC-5: FE full verify, BE migration/workflow/assessment tests, OpenAPI snapshot và kiểm contract FE có kết quả thật được ghi lại.

## Phases

1. Đồng bộ dev và bảo toàn WIP. Đã xong phần Git, đang kiểm build/verify.
2. Sau duyệt plan, ghép Tiền, hợp nhất tài liệu/snapshot và điều chỉnh tên ba migration chưa áp. Kiểm tra đủ tham chiếu và nâng cấp trên DB test cô lập.
3. Rà luồng sau merge, chạy verify, báo phần đã đạt và lỗi/rủi ro còn lại trước commit/PR.

## Rủi ro và rollback

Stash giữ bản WIP trước sync; không reset/clean hoặc bỏ stash. Sai cấu hình migration có thể ngăn API khởi động nên phải kiểm DB test trước. Chỉ sửa số migration incoming chưa áp, không dùng Flyway repair để bỏ qua checksum.

## Validation log

- git rev-list HEAD...origin/dev trả 0/0 ở cả hai repo sau fast-forward.
- Local flyway_schema_history xác nhận V80–V83 success=true bằng query metadata, không đọc dữ liệu trẻ.
- Chưa merge hai nhánh Tiền hoặc thay tên migration, chờ duyệt theo .ai/workflows/plan-change.md.

## Progress log

### 08/11/2026

- Đồng bộ dev, khôi phục WIP và giữ backup. FE `node scripts/verify.mjs` đạt VERIFY PASS full. BE `mvnw.cmd -q -DskipTests compile` đạt exit 0. Cả hai không còn unmerged paths, diff check đạt. Chưa chạy full BE sau sync, chưa merge Tiền vì chờ duyệt phương án migration.
