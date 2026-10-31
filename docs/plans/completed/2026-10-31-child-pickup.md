---
title: BE đón trẻ, ảnh phụ huynh, thông báo khi trẻ được đón
status: done
owner: carenestg94@gmail.com
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [attendance, identity-access, notification, child]
rules: [PCK-01, PCK-02, PCK-03, PCK-04, ATT-10, HLT-15, PAR-01]
risk: high
created: 2026-10-31
updated: 2026-10-31
---

# BE đón trẻ, ảnh phụ huynh, thông báo khi trẻ được đón

## Context

- User (2026-10-31): "làm tiếp phần đón trẻ". FE đã có mock: `FE:src/services/pickup/mock/pickupMockRepository.js`, `FE:src/services/pickup/api/pickupApi.js`, `FE:src/utils/pickup/pickupValidation.js`, `FE:docs/features/README.md` mục "Đón trẻ — Trả trẻ, Gọi phụ huynh".
- BE chưa có đón trẻ (`docs/business/flows/pickup.md`: module attendance). `DailyHealthServiceImpl` có TODO khóa sức khỏe trong ngày khi trẻ đã được trả (HLT-15).
- Chưa có module thông báo; chưa có ảnh phụ huynh (P-33).

## Quyết định đã chốt (user, 2026-10-31)

1. **Ảnh người đón (đóng P-33):** lấy ảnh phụ huynh ở phần phụ huynh của hồ sơ trẻ; phụ huynh đăng nhập App lần đầu phải đổi mật khẩu và cập nhật hồ sơ (ảnh) bắt buộc.
2. **Thông báo:** trẻ được đón ⇒ gửi thông báo cho phụ huynh.
3. **Về sớm / đón muộn:** không phân biệt; phụ huynh đón lúc nào trả lúc đó, ghi giờ trả thực tế (thay "ghi về sớm" của PCK-04).

**Mặc định (tự chọn theo chế độ tự chạy, user đổi được):**
- **D1 — Ảnh thuộc tài khoản phụ huynh** (`user_accounts`), một ảnh dùng cho mọi con; phụ huynh chưa có tài khoản thì không có ảnh ⇒ GV gọi phụ huynh. JPG/PNG/WEBP ≤ 2 MB, kiểm chữ ký tệp, S3 prefix `accounts/photos/`.
- **D2 — "Bắt buộc cập nhật hồ sơ":** `/accounts/me` (và dữ liệu đăng nhập) trả `profileUpdateRequired` = phụ huynh chưa có ảnh; App chặn màn hình tới khi tải ảnh. BE chưa chặn API phụ huynh khác. Hồ sơ phụ huynh cập nhật = ảnh; họ tên, SĐT, CCCD do trường quản lý.
- **D3 — Thông báo:** bảng thông báo theo người dùng (App đọc danh sách, đánh dấu đã đọc) + đăng ký thiết bị; đẩy qua cổng `PushGateway` — hiện là cổng giả (ghi log số thiết bị, không ghi nội dung); gắn Firebase Cloud Messaging khi có project + thông tin xác thực (không để trong repo).
- **D4 — Khớp ảnh:** GV chọn phụ huynh trong danh sách (vị trí phụ huynh trên hồ sơ); BE lấy họ tên, quan hệ, SĐT từ hồ sơ (mock FE gửi họ tên tự do).
- **D5 — Giờ trả:** GV nhập `HH:mm`, không sau giờ hiện tại; chỉ ghi cho hôm nay; xem ngày cũ từ đầu năm học đang hoạt động.
- **D6 — Lỗi theo ô:** 400 + `data: [{ field, message }]` (mock: 422).
- **D7 — Không sửa/hủy kết quả trả trẻ** (như mock); lần không trả được lưu, không tính là đã trả.

## API

| Method | Path | Ai | Ghi chú |
| --- | --- | --- | --- |
| GET | `/classes/{classId}/pickups?date` | GV lớp | Bảng đón: trạng thái từng trẻ (Chờ đón, Đã đón, Vắng, Chưa điểm danh), lần trả, các lần xác minh |
| GET | `/pickups/children/{childId}` | GV lớp | Trả trẻ hôm nay: danh sách phụ huynh kèm đường dẫn ảnh |
| POST | `/pickups` | GV lớp | Ghi kết quả: `PHOTO_MATCH` (kèm `guardianPosition`), `PHONE_CONFIRMED`, `PHONE_DECLINED` |
| GET | `/pickups?date` | GV | Kết quả trong ngày của các lớp mình |
| PUT, GET | `/accounts/me/photo` | Phụ huynh | Tải / xem ảnh của mình |
| GET | `/children/{childId}/guardians/{position}/photo` | Nhân sự trong phạm vi trẻ, phụ huynh của trẻ | Ảnh phụ huynh |
| GET | `/me/notifications` | Mọi người dùng | `{ items, unreadCount }` |
| POST | `/me/notifications/{id}/read`, `/me/notifications/read-all` | Mọi người dùng | |
| PUT, DELETE | `/me/devices` | Mọi người dùng | Đăng ký / gỡ thiết bị nhận thông báo |

## DB (V42)

- `user_accounts`: `photo_key`, `photo_content_type`, `photo_size_bytes`, `photo_updated_at`.
- `pickup_records` (mỗi lần xác minh; trả thành công tối đa một lần/trẻ/ngày).
- `notifications`, `device_tokens`.

## Acceptance criteria

- [x] AC-1: Bảng đón theo lớp/ngày: chỉ trẻ có mặt chờ đón; vắng, chưa điểm danh không trả được.
- [x] AC-2: Trả trẻ khớp ảnh (phụ huynh trong hồ sơ) hoặc phụ huynh xác nhận qua điện thoại (bắt buộc nội dung cuộc gọi); không trả được lưu lại; mỗi trẻ trả một lần/ngày; chỉ GV lớp, chỉ hôm nay.
- [x] AC-3: Trả thành công ⇒ thông báo cho tài khoản phụ huynh của trẻ (lưu + đẩy qua cổng).
- [x] AC-4: Trẻ đã được trả ⇒ không ghi thêm sức khỏe trong ngày (HLT-15).
- [x] AC-5: Phụ huynh tải ảnh; `profileUpdateRequired` hết khi có ảnh; GV lớp xem được ảnh phụ huynh của trẻ, ngoài phạm vi không xem được.
- [x] AC-6: `node scripts/verify.mjs` PASS.

Bằng chứng: `PickupIntegrationTest` (3 test: bảng đón, trả trẻ khớp ảnh / gọi phụ huynh, lỗi 409, thông báo phụ huynh, khóa sức khỏe trong ngày; ảnh phụ huynh + `profileUpdateRequired`; thiết bị chuyển theo người đăng nhập).

## Ghi chú cho FE, App

- FE (mock → BE): khớp ảnh gửi `guardianPosition` thay họ tên tự do; `pickupPersonRelationship` là mã quan hệ (FATHER, MOTHER...); ảnh tải `photoUrl` bằng axios (blob, có token); lỗi theo ô 400 + `data: [{ field, message }]`; bỏ "Chờ đón sớm", `reason` HEALTH_EARLY (không phân biệt về sớm); `parentNotification` thay bằng `notifiedParents`.
- App (chưa sửa, cần user cho phép sửa repo APP): màn cập nhật hồ sơ bắt buộc khi `profileUpdateRequired` (tải ảnh `PUT /accounts/me/photo`), danh sách thông báo `/me/notifications`, đăng ký thiết bị `PUT /me/devices` khi có Firebase.
- Đẩy thật: cần project Firebase + thông tin xác thực (không để trong repo), thêm cổng FCM thay `LoggingPushGateway`.

## Progress log

- 2026-10-31: tạo plan; user chốt ảnh, thông báo, bỏ phân biệt về sớm.
- 2026-10-31: V42; ảnh phụ huynh (`ParentPhotoService`, `AccountPhotoStorage`, base `PrivateImageStorage` dùng chung với ảnh phiếu theo dõi); feature `notification` (bảng, API, `PushDispatchListener`, `LoggingPushGateway`); đón trẻ (`PickupService`, `PickupController`); khóa sức khỏe trong ngày khi trẻ đã được trả; rule PCK-02, PCK-04, đóng P-33; docs, OpenAPI.
