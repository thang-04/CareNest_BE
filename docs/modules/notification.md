# Module — notification

Feature: `notification` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thông báo trẻ đã được đón, danh sách trong App, đăng ký thiết bị (2026-10-31, V66); nghỉ học, đổi SĐT phụ huynh, tiếp xúc lao, kết quả sức khỏe, mở đợt/kỳ, xếp lớp (2026-11-10, V87–V89); thông báo trường của BGH: thiết kế

## Mục đích
Gửi thông báo (in-app, push mobile) khi domain phát event; thông báo chung của trường do BGH gửi (PAR-05).

## Owns
Notification, DeliveryStatus, tham chiếu device token, thông báo chung của trường (người gửi BGH, phạm vi nhận).

## KHÔNG owns
Quyết định khi nào gửi (domain quyết định qua event), nội dung nghiệp vụ chi tiết, chat (không thay Zalo).

## Rules
PAR-05 (BGH gửi; push + trong App; không theo dõi đã đọc), PAR-06 (một tài khoản phụ huynh nhận cho mọi con), PAR-07 (mọi thông báo đều đẩy tới App, danh mục theo role ở mục dưới). Event chính: sức khỏe trong ngày (HLT-14, gửi ngay), đã đón trẻ (PCK-04), công bố kết quả (PAR-03).

## Danh mục thông báo trên App theo role (PAR-07, user chốt 2026-11-14)
Mọi thông báo BE tạo đều đẩy tới thiết bị đã đăng ký (`PUT /me/devices`, chỉ App đăng ký) và hiện trong danh sách thông báo; App không lọc loại. Cột "Ở đâu": `dev` = đã có; `G94-236` = nhánh daily-attendance (OBS-29..31, chưa vào `dev`).

| Role nhận | Loại (`NotificationType`) | Rule | Ở đâu |
| --- | --- | --- | --- |
| Phụ huynh | `CHILD_PICKED_UP` | PCK-04 | dev |
| Phụ huynh | `HEALTH_RESULT_PUBLISHED`, `HEALTH_RESULT_UPDATED` (nội dung đẩy chung "Thông tin sức khỏe") | HLT-11, PAR-03 | dev |
| Phụ huynh | `DAILY_HEALTH_NOTED` — GV ghi sức khỏe trong ngày "cần lưu ý", không kèm triệu chứng | HLT-14, OBS-31 | G94-236 |
| Phụ huynh | `WEEKLY_COMMENT_SENT` — nhận xét tuần; nhận xét tháng/học kỳ/cuối năm (OBS-06) không có thông báo | OBS-29 | G94-236 |
| Phụ huynh | `CHILD_PLACED` — con lên lớp/ở lại khi kích hoạt năm học | OBS-31 | G94-236 |
| GV | `LEAVE_REQUEST_CHANGED` — phụ huynh gửi/hủy đơn nghỉ | ATT-05, OBS-31 | G94-236 |
| GV | `CHILD_PLACED` — trẻ được xếp/chuyển vào lớp | CHD-10 | dev |
| GV | `CHILD_WITHDRAWN`, `CHILD_TB_CONTACT`, `HEALTH_CHECK_OPENED`, `ATTENDANCE_DAY_UNLOCKED` | CHD-03, CHD-07, HLT-08, ATT-08 | dev |
| GV | `EDU_*` (kế hoạch giáo dục) | EDU (spec 003) | dev |
| GV | `ATTENDANCE_REMINDER`, `ASSESSMENT_REMINDER`, `ASSESSMENT_REOPENED`, `STAFF_ASSIGNED`, `CUTOFF_CHANGED`, `REWARD_LIST_UPDATED`, `SCHOOL_YEAR_ACTIVATED` | OBS-30, OBS-31, CFG-YR-02 | G94-236 |
| Bếp | `MEAL_COUNT_CONFIRMED` — sổ báo ăn điểm trường được xác nhận / xác nhận lại | NUT-02, ATT-08, OBS-31 | G94-236 |
| Bếp | `CUTOFF_CHANGED`, `SCHOOL_YEAR_ACTIVATED` | OBS-31, CFG-YR-02 | G94-236 |

- Loại chỉ cho BGH (`MEAL_COUNT_RECONFIRM`, `GUARDIAN_PHONE_CHANGE_*`, `LEADER_REMINDER`, `CHILD_TB_CONTACT_DATE_CHANGED`) chỉ tới App khi BGH dùng App (chỉ xem — nhánh TBD-1).
- Chưa có: thông báo của trường do BGH gửi (PAR-05) — hoãn, task riêng (user 2026-11-14).

## Phụ thuộc
- Nghe event từ domain (sau commit). Resolve người nhận qua `identity` + `AccessScopeService`.
- Domain gọi `NotificationService` (interface) để tạo thông báo; notification không gọi ngược domain.

## API & bảng
Plan `docs/plans/completed/2026-10-31-child-pickup.md`.
- Bảng (V66): `notifications` (theo người nhận, `read_at` chỉ cho badge của chính người đó), `device_tokens` (`docs/database/DATA_DICTIONARY.md`).
- `NotificationService.notifyUsers(...)` — module khác gọi trong giao dịch của mình; `NotificationsCreatedEvent` ⇒ `PushDispatchListener` (after-commit) đẩy qua `integration/push/PushGateway`.
- `PushGateway` hiện là `LoggingPushGateway` (cổng giả: chỉ log nền tảng, loại). Push provider chưa chốt (xem `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md`); gắn Firebase Cloud Messaging cần project + thông tin xác thực ngoài repo.
- API (mọi người dùng đã đăng nhập, thường là App phụ huynh): `GET /me/notifications?limit`, `POST /me/notifications/{id}/read`, `POST /me/notifications/read-all`, `PUT /me/devices`, `DELETE /me/devices`. Mã lỗi 1201.
- Loại hiện có (CHECK `ck_notifications_type`, V89): `CHILD_PICKED_UP` (PCK-04), `MEAL_COUNT_RECONFIRM`, `CHILD_WITHDRAWN` (G94-180/CHD-03), `GUARDIAN_PHONE_CHANGE_REQUESTED`, `GUARDIAN_PHONE_CHANGE_DECIDED` (G94-180/AUTH-21), `CHILD_TB_CONTACT`, `CHILD_TB_CONTACT_DATE_CHANGED` (G94-180/CHD-07), `HEALTH_RESULT_PUBLISHED`, `HEALTH_RESULT_UPDATED` (HLT-11, PAR-03), `HEALTH_CHECK_OPENED` (HLT-08), `CHILD_PLACED` (V89, CHD-10 — GV lớp mới).

## Known pitfalls
- Không làm trạng thái "phụ huynh đã đọc" cho trường xem (PAR-05); `read_at` chỉ để App hiện badge cho chính người nhận.
- Nội dung push không chứa dữ liệu sức khỏe/chi tiết nhạy cảm; chỉ "có cập nhật mới" + deep link. Loại sức khỏe nằm trong `PushDispatchListener.HEALTH_TYPES`: push chỉ "Thông tin sức khỏe" / "Có thông tin sức khỏe mới. Mở ứng dụng để xem."; thêm loại sức khỏe mới phải thêm vào tập này.
- Thêm loại mới cần migration mới (không sửa migration cũ) thả và tạo lại `ck_notifications_type` liệt kê đủ mọi loại đang có.
- Gửi lỗi không được rollback giao dịch nghiệp vụ (listener after-commit).

## Related issues
Chưa có.

## Đọc thêm khi
Thêm kênh gửi mới ⇒ ADR-0004 (communication).
