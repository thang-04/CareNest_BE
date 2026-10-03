# Module — notification

Feature: `notification` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Gửi thông báo (in-app, push mobile) khi domain phát event.

## Owns
Notification, DeliveryStatus, tham chiếu device token.

## KHÔNG owns
Quyết định khi nào gửi (domain quyết định qua event), nội dung nghiệp vụ chi tiết, chat (không thay Zalo).

## Phụ thuộc
- Nghe event từ domain (sau commit). Resolve người nhận qua `identity` + `AccessScopeService`.
- Không domain nào import notification.

## API & bảng
Chưa có. Push provider chưa chốt (xem `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md`).

## Known pitfalls
- Nội dung push không chứa dữ liệu sức khỏe/chi tiết nhạy cảm; chỉ "có cập nhật mới" + deep link.
- Gửi lỗi không được rollback giao dịch nghiệp vụ (listener after-commit).

## Related issues
Chưa có.

## Đọc thêm khi
Thêm kênh gửi mới ⇒ ADR-0004 (communication).
