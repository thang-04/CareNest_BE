# Cross-Module Issues

Vấn đề phát sinh do tương tác giữa module hoặc giữa BE ↔ FE/APP. Đọc khi task chạm ≥2 module (L3).

## Rủi ro dự kiến (thiết kế) — CHƯA phải issue thực tế

Dùng làm checklist khi implement/review. Khi xảy ra thật ⇒ tạo incident và link vào đây.

| ID | Module | Rủi ro | Phòng ngừa | Liên quan |
| --- | --- | --- | --- | --- |
| CMR-01 | attendance → nutrition | Điểm danh/báo ăn đổi sau khi số suất đã xác nhận ⇒ số bếp nấu lệch thực tế (stale derived data) | Lịch sử + đánh dấu thay đổi + xác nhận lại; test case trẻ đến muộn | ATT-07, NUT-03, ADR-0005 |
| CMR-02 | nutrition | Bếp/định lượng dùng số chưa xác nhận | Chỉ trả số đã xác nhận; chưa xác nhận trả trạng thái chờ; test | NUT-03, NUT-07 |
| CMR-03 | identity ↔ mọi module | Lọc scope thiếu ở query ⇒ Hiệu phó/GV thấy campus/lớp khác | Scope kiểm tra ở service; test cross-campus bắt buộc | AUTH-07, AUTH-08 |
| CMR-04 | child → attendance/reporting | Trẻ chuyển lớp/campus giữa năm ⇒ thống kê theo lớp hiện tại thay vì lớp tại ngày dữ liệu | Luôn resolve enrollment theo ngày | — |
| CMR-05 | domain → ai-assistance | Dữ liệu định danh trẻ lọt vào prompt/log | Builder input bí danh; test kiểm tra input | AI-04, AI-05 |
| CMR-06 | BE → FE/APP | Đổi DTO/error code mà client không cập nhật | Workflow update-api; CROSS_REPO_MAP | — |
| CMR-07 | attendance → notification | Event gửi trước commit ⇒ thông báo sai khi transaction rollback | Listener sau commit | ADR-0004 |

## Issue thực tế

| ID | Module | Tóm tắt | Incident |
| --- | --- | --- | --- |
| — | — | Chưa có | — |
