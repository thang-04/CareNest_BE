# Module — notification

Feature: `notification` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thông báo trẻ đã được đón, danh sách trong App, đăng ký thiết bị (2026-10-31, V66); nghỉ học, đổi SĐT phụ huynh, tiếp xúc lao, kết quả sức khỏe, mở đợt/kỳ, xếp lớp (2026-11-10, V87–V89); hàng chờ đẩy + FCM (2026-11-15, V139); thông báo của trường do BGH gửi, mẫu, quy tắc tự động, lịch gửi lặp (2026-11-15, V140–V141, spec 019)

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

- Nhãn loại (Tiền yêu cầu 17/11/2026): `NotificationResponse.typeLabel` là tên nhóm tiếng Việt tính từ `NotificationType.typeLabel()`, có trong danh sách/chi tiết/đánh dấu đã đọc. Giữ `type` để client điều hướng; không lưu nhãn vào DB hoặc đổi payload push. Web hiển thị nhãn ở danh sách, chuông và chi tiết; App đang đọc JSON theo key nên trường bổ sung không buộc đổi App. OpenAPI đã bổ sung field, snapshot test đạt.
Plan `docs/plans/completed/2026-10-31-child-pickup.md`.
- Bảng (V66): `notifications` (theo người nhận, `read_at` chỉ cho badge của chính người đó), `device_tokens` (`docs/database/DATA_DICTIONARY.md`).
- `NotificationService.notifyUsers(...)` — module khác gọi trong giao dịch của mình; dòng lưu ở `push_state = PENDING` (outbox, V139). Không còn event/listener after-commit.
- `PushOutboxWorker` dùng outbox V139 và ledger từng thiết bị V142, batch 300, bốn luồng; thử lại thiết bị lỗi trong 60 phút, không gửi lại thiết bị đã ghi SENT (PAR-11/12).
- `PushGateway`: `FcmPushGateway` (FCM HTTP v1 bằng JDK, JWT RS256 từ file service account) khi đặt `CARENEST_PUSH_FCM_CREDENTIALS_FILE`; không đặt ⇒ `LoggingPushGateway` chỉ log. Lỗi phân loại `PushException`: token hỏng (`UNREGISTERED`, `SENDER_ID_MISMATCH`, `INVALID_ARGUMENT` khi xác định token sai) ⇒ xóa thiết bị; `THIRD_PARTY_AUTH_ERROR` (iOS chưa có khóa APNs) ⇒ giữ thiết bị; 429/5xx ⇒ thử lại.
- API (mọi người dùng đã đăng nhập, thường là App phụ huynh): `GET /me/notifications?limit`, `GET /me/notifications/{id}` (mở từ banner, người khác ⇒ 404), `POST /me/notifications/{id}/read`, `POST /me/notifications/read-all`, `PUT /me/devices`, `DELETE /me/devices`. Mã lỗi 1201.
- Loại hiện có (CHECK `ck_notifications_type`, V89): `CHILD_PICKED_UP` (PCK-04), `MEAL_COUNT_RECONFIRM`, `CHILD_WITHDRAWN` (G94-180/CHD-03), `GUARDIAN_PHONE_CHANGE_REQUESTED`, `GUARDIAN_PHONE_CHANGE_DECIDED` (G94-180/AUTH-21), `CHILD_TB_CONTACT`, `CHILD_TB_CONTACT_DATE_CHANGED` (G94-180/CHD-07), `HEALTH_RESULT_PUBLISHED`, `HEALTH_RESULT_UPDATED` (HLT-11, PAR-03), `HEALTH_CHECK_OPENED` (HLT-08), `HEALTH_TREND_APPROVED` (V138, HLT-21 — GV lớp duyệt nhận định xu hướng ⇒ phụ huynh; body = tổng quan đã duyệt, push che như loại sức khỏe khác), `CHILD_PLACED` (V89, CHD-10 — GV lớp mới), `WEEKLY_COMMENT_SENT` (V129, OBS-29 — phụ huynh của trẻ khi cô gửi nhận xét tuần), `ATTENDANCE_REMINDER`, `ASSESSMENT_REMINDER`, `ASSESSMENT_REOPENED`, `LEADER_REMINDER` (V133, OBS-30 — nhắc việc GV lớp / BGH; đường dẫn mở đúng màn, tham số `r` là khóa "một lần"); `STAFF_ASSIGNED`, `CUTOFF_CHANGED`, `LEAVE_REQUEST_CHANGED`, `MEAL_COUNT_CONFIRMED`, `REWARD_LIST_UPDATED` (V134, OBS-31 — phân công, đổi giờ chốt, đơn nghỉ, sổ báo ăn đã xác nhận cho bếp, luồng khen thưởng); `DAILY_HEALTH_NOTED` (sức khỏe đầu ngày cần lưu ý ⇒ phụ huynh), `SCHOOL_YEAR_ACTIVATED` (kích hoạt năm ⇒ GV, PHT, bếp) (V135); `CHILD_PLACED` cũng gửi phụ huynh khi trẻ lên lớp / ở lại lúc kích hoạt năm.

- **Thông báo của trường** (module `announcement`, PAR-05, PAR-07, PAR-08): bảng `school_announcements`, `announcement_attachments` (S3 riêng tư, danh mục `announcement`), `announcement_templates` (V140). `RecipientResolver` chốt người nhận theo phạm vi × vai trò tại thời điểm gửi; mỗi người một dòng `notifications` (`announcement_id`, chỉ mục duy nhất `(announcement_id, user_id)`), đẩy qua hàng chờ ở trên. Gửi ngay chạy trong request; hẹn giờ do `AnnouncementDispatchWorker` (30 giây, SKIP LOCKED). API: `/announcements` (preview, gửi multipart `data` + `files`, sửa, hủy, danh sách, chi tiết), `/announcement-templates`, `/me/announcements/{id}` (người nhận đọc nội dung đầy đủ), `/announcement-attachments/{id}/content`. Mã lỗi 1202–1209.
- **Quy tắc tự động** (PAR-09, V141): `NotificationRuleCatalog` (code) giữ loại bắt buộc / sửa được / biến / câu chữ mặc định; bảng `notification_rules` chỉ lưu phần HT đã chỉnh. Nơi phát sự kiện gọi `NotificationService.notifyEvent(userIds, type, values, childId)`: PCK-04 `PickupServiceImpl`, NUT-03 `MealCountServiceImpl`, HLT-14 `DailyHealthServiceImpl.addEntry` (chỉ khi có biểu hiện), NUT-08 `MealPlanServiceImpl.publishMealPlan`, ATT-05 `LeaveRequestServiceImpl.createLeaveRequest` (GV lớp), PAR-03 `HealthNotifier` + `EvaluationNotifier` (đánh giá trẻ, đánh giá chủ đề). API `/notification-rules` (chỉ HT). Mã lỗi 1210–1212.
- **Lịch gửi lặp** (PAR-10, V141): `announcement_schedules`; `AnnouncementScheduleWorker` mỗi phút chạy lịch đến hạn (SKIP LOCKED), ngày nghỉ (`SchoolYearService.isSchoolDay`) bỏ lần đó, luôn tính `next_run_at` kế tiếp (tháng thiếu ngày ⇒ ngày cuối tháng). API `/announcement-schedules` (chỉ HT). Mã lỗi 1213.

## Known pitfalls
- **PAR-11:** theo dõi gửi từng thiết bị bằng V142; thiết bị đã gửi thành công không gửi lại vì lỗi thiết bị khác.
- Không làm trạng thái "phụ huynh đã đọc" cho trường xem (PAR-05); `read_at` chỉ để App hiện badge cho chính người nhận.
- Nội dung push không chứa dữ liệu sức khỏe/chi tiết nhạy cảm; chỉ "có cập nhật mới" + deep link. Loại nhạy cảm nằm trong `PushBanner.SENSITIVE_TYPES`: push chỉ "Thông tin sức khỏe" / "Có thông tin sức khỏe mới. Mở ứng dụng để xem."; thêm loại sức khỏe mới phải thêm vào tập này.
- Loại thông báo mới có câu chữ HT sửa được ⇒ khai báo trong `NotificationRuleCatalog` và gọi `notifyEvent`; loại nhạy cảm thêm vào `PushBanner`.
- Thêm loại mới cần migration mới (không sửa migration cũ) thả và tạo lại `ck_notifications_type` liệt kê đủ mọi loại đang có.
- Gửi lỗi không rollback giao dịch nghiệp vụ: việc đẩy chạy ở worker, giao dịch riêng.
- PAR-12 (Tiền 15/11/2026, clarify spec 902 Q2=A): máy chết sau Firebase nhận nhưng trước lưu kết quả ⇒ cho retry trong 60 phút từ lúc tạo tin, chấp nhận khả năng banner trùng hiếm gặp riêng cửa sổ này. App bắt buộc chống xử lý trùng theo `data.notificationId` ở phần do App quản lý; kiểm riêng OS auto-display. Không áp ngoại lệ cho máy đã ghi thành công chỉ vì máy khác lỗi (PAR-11).

## Related issues
Chưa có.

## Đọc thêm khi
Thêm kênh gửi mới ⇒ ADR-0004 (communication).
