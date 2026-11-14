# Research — 019 Thông báo của trường

Mỗi mục: Quyết định · Lý do · Phương án đã cân nhắc.

## R1. Hàng chờ đẩy: outbox trong bảng `notifications` + worker `@Scheduled`

- **Quyết định:** thêm cột trạng thái đẩy vào chính bảng `notifications` (`push_state`, `push_attempts`, `push_next_at`). Worker `@Scheduled(fixedDelay)` mỗi 5 giây lấy tối đa 100 dòng `PENDING` đến hạn bằng `SELECT … FOR UPDATE SKIP LOCKED`, mỗi dòng xử lý trong một giao dịch ngắn (khóa → đẩy tới các thiết bị → ghi trạng thái → commit).
- **Lý do:** Tiền chốt 14/11 chưa dùng broker; ghi thông báo và "lệnh đẩy" là cùng một dòng nên luôn atomic với giao dịch nghiệp vụ (FR-001). `SKIP LOCKED` cho phép nhiều máy chủ chạy song song mà một dòng chỉ một máy xử lý (FR-003). Dòng bị khóa trong lúc đẩy; máy chết giữa chừng thì giao dịch rollback, dòng trở lại `PENDING`.
- **Giới hạn đã biết:** máy chết đúng sau khi FCM nhận nhưng trước khi commit ⇒ có thể đẩy lại 1 lần (at-least-once, cửa sổ vài trăm ms). App bỏ trùng theo `notificationId` trong `data`. Không có cách exactly-once khi gọi dịch vụ bên ngoài.
- **Thay thế:** RabbitMQ + relay (plan 08/11) — hoãn theo quyết định 14/11; bảng giữ nguyên, sau này chỉ thay worker bằng relay. `@Async` sau commit (như `PushDispatchListener` hiện tại) — mất lệnh khi máy chết, không thử lại.

## R2. Thử lại và ngừng

- **Quyết định:** lỗi tạm thời (HTTP 429, 500, 503, lỗi mạng) ⇒ `push_next_at = now + min(1 phút × 2^(lần-1), 30 phút) ± 20% ngẫu nhiên`; có `Retry-After` thì dùng giá trị lớn hơn. Quá 60 phút kể từ `created_at` ⇒ `FAILED`. Thông báo vẫn trong danh sách.
- **Trạng thái theo thông báo, không theo thiết bị:** chỉ thử lại khi **không thiết bị nào** nhận được và có ít nhất một lỗi tạm thời; có thiết bị đã nhận ⇒ `SENT` (thiết bị lỗi bị bỏ lần đó, ghi log). Tránh đẩy trùng cho thiết bị đã nhận mà không cần bảng theo thiết bị. Trần: một thiết bị lỗi riêng lẻ không được thử lại; nâng lên bảng `notification_deliveries` nếu số liệu cho thấy cần.
- **Lý do:** khuyến nghị FCM (exponential backoff có jitter, tôn trọng Retry-After, không thử lại tin quá cũ).

## R3. Gọi FCM không thêm thư viện

- **Quyết định:** `FcmPushGateway` gọi FCM HTTP v1 (`POST https://fcm.googleapis.com/v1/projects/{id}/messages:send`) bằng `java.net.http.HttpClient`; lấy access token OAuth2 bằng JWT ký RS256 (`java.security.Signature`, khóa PKCS#8 trong file service account) đổi tại `https://oauth2.googleapis.com/token`, cache tới 5 phút trước khi hết hạn. Bật khi có `carenest.push.fcm.credentials-file`; thiếu thì dùng `LoggingPushGateway` (FR-009).
- **Lý do:** không thêm dependency (`firebase-admin` kéo theo ~20 thư viện Google, cần duyệt dependency); FCM v1 chỉ cần 2 lời gọi HTTP.
- **Phân loại lỗi FCM:** `UNREGISTERED` (404), `INVALID_ARGUMENT` về token (400), `SENDER_ID_MISMATCH` (403) ⇒ token hỏng, xóa thiết bị; `THIRD_PARTY_AUTH_ERROR` (iOS thiếu khóa APNs) ⇒ bỏ qua, giữ thiết bị (FR-004); 429/500/503 ⇒ tạm thời.
- **Thay thế:** `firebase-admin` SDK — chuẩn nhưng là dependency mới và nặng.

## R4. Dữ liệu cũ, thiết bị cũ

- Migration thêm `push_state` với mặc định `'SKIPPED'` cho mọi dòng có sẵn, rồi đổi mặc định thành `'PENDING'` (FR-008).
- Người nhận không có thiết bị ⇒ `SKIPPED` ngay khi worker xử lý.
- Dọn thiết bị `updated_at` quá 270 ngày: cron 03:10 hằng ngày (FR-005, khuyến nghị FCM). App đăng ký lại mỗi lần mở nên `updated_at` phản ánh thiết bị còn dùng (đăng ký lại cập nhật `updated_at`).

## R5. Banner che nội dung nhạy cảm

- Tập loại "nhạy cảm" (đã có `HEALTH_TYPES`) mở rộng thêm sức khỏe trong ngày (HLT-14) và công bố cân đo / đánh giá trẻ / đánh giá chủ đề (PAR-03, clarify Q5). Banner dùng câu chung theo loại; nội dung đầy đủ xem trong App qua `GET /me/notifications/{id}`.

## R6. Thông báo của trường: gửi ngay xử lý ngay, hẹn giờ do worker

- **Quyết định:** "Gửi ngay" chốt người nhận và tạo dòng `notifications` trong chính request (vài trăm dòng, nhanh); đẩy đi qua outbox nên không chờ FCM. "Hẹn giờ" lưu `SCHEDULED`; worker mỗi 30 giây lấy thông báo đến giờ bằng `FOR UPDATE SKIP LOCKED`, chuyển `SENDING`, tạo dòng thông báo, rồi `SENT`. Chỉ mục duy nhất `(announcement_id, user_id)` làm việc tạo lại sau khi máy chết an toàn (bỏ qua dòng đã có).
- **Sửa / hủy (clarify Q2):** khóa dòng `FOR UPDATE` rồi kiểm `status = SCHEDULED`; worker cũng khóa dòng nên hai bên không chạy chồng.
- **Người nhận (FR-011, FR-012, clarify Q4):** tập `userId` (bỏ trùng): phụ huynh của trẻ đang học trong lớp thuộc phạm vi; GV phân công lớp; tài khoản bếp của điểm trường; BGH = HT + PHT phụ trách điểm trường của phạm vi (toàn trường ⇒ mọi BGH). Bỏ người gửi.

## R7. Đính kèm

- Dùng `FileStorage` hiện có (S3 riêng tư, spec 017), thêm danh mục `announcement` (`announcements/`, 10MB, `jpg, png, pdf`). Tải qua API kiểm quyền rồi trả link ký tạm thời như các danh mục khác. Người được tải: người gửi, BGH trong phạm vi, người có dòng `notifications` của thông báo đó (FR-016); khác ⇒ 404.

## R8. Quy tắc tự động

- **Danh mục trong code** (`NotificationRuleCatalog`): mỗi `NotificationType` có cờ bắt buộc, sửa được câu chữ, biến được phép, câu chữ mặc định. Bảng `notification_rules` chỉ lưu phần HT đã chỉnh (bật/tắt, câu chữ); không có dòng ⇒ dùng mặc định. Loại có sẵn khác ⇒ bắt buộc, chỉ xem (clarify Q1).
- **Điền câu chữ:** `{tenBien}` thay bằng giá trị; biến không thuộc danh sách được phép ⇒ từ chối khi lưu (FR-019). Banner của loại nhạy cảm không dùng câu chữ này (R5).
- `NotificationService.notifyEvent(type, userIds, vars, childId)`: kiểm bật/tắt, điền câu chữ, gọi luồng lưu sẵn có. Nơi gọi PCK-04, NUT-03 chuyển sang `notifyEvent` để câu chữ sửa được.

## R9. Lịch gửi lặp

- Bảng `announcement_schedules` (mẫu, phạm vi × vai trò, `WEEKLY` + thứ / `MONTHLY` + ngày, giờ gửi, `next_run_at`, bật/tắt). Worker mỗi phút lấy lịch đến hạn (`FOR UPDATE SKIP LOCKED`): ngày học ⇒ tạo thông báo của trường gửi ngay từ mẫu; ngày nghỉ (SCH-05) ⇒ bỏ; luôn tính `next_run_at` lần kế tiếp.
- Ngày 29–31 không có trong tháng ⇒ ngày cuối tháng (clarify Q3), bằng `YearMonth.atEndOfMonth()`.
- Múi giờ: `carenest.time-zone` (như các cron hiện có).
