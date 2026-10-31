# Module — notification

Feature: `notification` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thông báo trẻ đã được đón, danh sách trong App, đăng ký thiết bị (2026-10-31, V42); thông báo trường của BGH: thiết kế

## Mục đích
Gửi thông báo (in-app, push mobile) khi domain phát event; thông báo chung của trường do BGH gửi (PAR-05).

## Owns
Notification, DeliveryStatus, tham chiếu device token, thông báo chung của trường (người gửi BGH, phạm vi nhận).

## KHÔNG owns
Quyết định khi nào gửi (domain quyết định qua event), nội dung nghiệp vụ chi tiết, chat (không thay Zalo).

## Rules
PAR-05 (BGH gửi; push + trong App; không theo dõi đã đọc), PAR-06 (một tài khoản phụ huynh nhận cho mọi con). Event chính: sức khỏe trong ngày (HLT-14, gửi ngay), đã đón trẻ (PCK-04), công bố kết quả (PAR-03).

## Phụ thuộc
- Nghe event từ domain (sau commit). Resolve người nhận qua `identity` + `AccessScopeService`.
- Domain gọi `NotificationService` (interface) để tạo thông báo; notification không gọi ngược domain.

## API & bảng
Plan `docs/plans/completed/2026-10-31-child-pickup.md`.
- Bảng (V42): `notifications` (theo người nhận, `read_at` chỉ cho badge của chính người đó), `device_tokens` (`docs/database/DATA_DICTIONARY.md`).
- `NotificationService.notifyUsers(...)` — module khác gọi trong giao dịch của mình; `NotificationsCreatedEvent` ⇒ `PushDispatchListener` (after-commit) đẩy qua `integration/push/PushGateway`.
- `PushGateway` hiện là `LoggingPushGateway` (cổng giả: chỉ log nền tảng, loại). Push provider chưa chốt (xem `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md`); gắn Firebase Cloud Messaging cần project + thông tin xác thực ngoài repo.
- API (mọi người dùng đã đăng nhập, thường là App phụ huynh): `GET /me/notifications?limit`, `POST /me/notifications/{id}/read`, `POST /me/notifications/read-all`, `PUT /me/devices`, `DELETE /me/devices`. Mã lỗi 1201.
- Loại hiện có: `CHILD_PICKED_UP` (PCK-04).

## Known pitfalls
- Không làm trạng thái "phụ huynh đã đọc" cho trường xem (PAR-05); `read_at` chỉ để App hiện badge cho chính người nhận.
- Nội dung push không chứa dữ liệu sức khỏe/chi tiết nhạy cảm; chỉ "có cập nhật mới" + deep link.
- Gửi lỗi không được rollback giao dịch nghiệp vụ (listener after-commit).

## Related issues
Chưa có.

## Đọc thêm khi
Thêm kênh gửi mới ⇒ ADR-0004 (communication).
