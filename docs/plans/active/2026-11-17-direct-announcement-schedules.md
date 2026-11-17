---
title: Trang lịch gửi với nội dung riêng và chọn bật/tắt
status: in-progress
owner: Tiền
jira: G94-288 / FE-FEAT-55 / BE-FEAT-55
branch: feature/G94-288-BE-FEAT-55-notifications-integrated
modules: [notification]
rules: [PAR-07, PAR-08, PAR-10]
risk: high
created: 2026-11-17
updated: 2026-11-17
---
# Lịch gửi trực tiếp, checkbox và toast

Tiền yêu cầu 17/11/2026, sau khi xong task timing/selection. Domain/AC10–13/contracts/data/tasks tại CareNest_BE/specs/019-school-notifications/domain-spec.md. Dùng lại form soạn, mẫu điền nhanh lưu snapshot giống form hiện có. Giữ lịch cũ/JSON cũ tương thích. Không modal tạo/sửa lịch; không đổi role hoặc schema lịch tuần/tháng.

## Progress log
- Đọc entity/request/response/service/worker/repo/controller, FK tệp/schedule/template và callers, form/list/mock/API/router/toast trước sửa. Bulk activation phải khóa và kiểm toàn bộ ids; tệp dùng chung cần giữ file còn được tin đã gửi tham chiếu. V144 mới (V143 trùng dev, đổi khi merge 18/11), không sửa migration cũ, không DB thật/App/dependency/e2e/commit.
- 17/11 (tiếp): T11–T13 xong. Thêm xóa nhiều lịch bằng checkbox (BE delete-selection nguyên tử + FE nút Xóa đã chọn, xác nhận chung xóa một/nhiều), công tắc Bật lịch gửi mặc định bật. BE 27 test (lịch 10, integration 6, controller 7, OpenAPI 4) đạt, OpenAPI/API_INDEX cập nhật; FE notifications 31/31, lint/format/build đạt. Chưa e2e/browser, không commit.
- 17/11 (tiếp 2): chỉ lịch đã tắt tick/xóa (BE 1215), cột Thao tác mọi bảng, bỏ bấm dòng mở chi tiết, trang chi tiết lịch, header checkbox thông báo luôn bấm được. BE lịch 11 + integration 6 đạt; FE 31/31, lint/format/build đạt. Chưa e2e/browser, không commit.
