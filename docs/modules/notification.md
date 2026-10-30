# Module — notification

Feature: `notification` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

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
- Không domain nào import notification.

## API & bảng
Chưa có. Push provider chưa chốt (xem `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md`).

## Known pitfalls
- Không làm trạng thái "phụ huynh đã đọc" (PAR-05); DeliveryStatus chỉ là trạng thái gửi.
- Nội dung push không chứa dữ liệu sức khỏe/chi tiết nhạy cảm; chỉ "có cập nhật mới" + deep link.
- Gửi lỗi không được rollback giao dịch nghiệp vụ (listener after-commit).

## Related issues
Chưa có.

## Đọc thêm khi
Thêm kênh gửi mới ⇒ ADR-0004 (communication).
