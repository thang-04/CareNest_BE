# Quickstart — kiểm chứng 019

## Chuẩn bị

- Docker chạy (integration test dùng Testcontainers PostgreSQL 17).
- Không cần Firebase: thiếu `carenest.push.fcm.credentials-file` ⇒ `LoggingPushGateway` chỉ ghi log (FR-009).
- Bật FCM thật (tùy chọn, máy dev): đặt `CARENEST_PUSH_FCM_CREDENTIALS_FILE=/đường/dẫn/service-account.json` (không để trong repo).

## Lệnh

```bash
./mvnw -q test -Dtest='PushOutbox*Test,FcmPushGatewayTest,NotificationControllerTest'                  # US1
./mvnw -q test -Dtest='Announcement*Test,RecipientResolverTest'                                        # US2
./mvnw -q test -Dtest='NotificationRuleServiceImplTest,NotificationServiceImplTest,AnnouncementSchedule*Test' # US3
./mvnw verify                                                                      # toàn bộ: test, ArchUnit, OpenAPI snapshot, Spotless
```

## Kịch bản và kết quả mong đợi

| # | Kịch bản | Mong đợi | Test |
|---|---|---|---|
| 1 | Tạo thông báo cho người có 2 thiết bị | dòng `PENDING`; worker đẩy 2 thiết bị; `SENT` | `PushOutboxWorkerTest` |
| 2 | Gateway ném lỗi tạm thời | `PENDING`, `push_attempts=1`, `push_next_at≈+1 phút` | `PushOutboxWorkerTest` |
| 3 | Lỗi tạm thời và đã quá 60 phút | `FAILED`, dòng vẫn trong `/me/notifications` | `PushOutboxWorkerTest` |
| 4 | Token báo không hợp lệ | thiết bị bị xóa; thiếu khóa APNs ⇒ giữ | `PushOutboxWorkerTest` |
| 5 | Thông báo sức khỏe / PAR-03 | banner câu chung | `PushOutboxWorkerTest` (`healthBannerShowsOnlyGenericText`) |
| 6 | 2 worker cùng lấy hàng chờ | mỗi dòng đẩy 1 lần (`SKIP LOCKED`) | `PushOutboxIntegrationTest` |
| 7 | Dòng có trước migration | `SKIPPED` | V130 thêm cột với DEFAULT `'SKIPPED'` rồi mới đổi DEFAULT; chưa có test riêng |
| 8 | `GET /me/notifications/{id}` của người khác | 404 | `NotificationControllerTest` |
| 9 | HT gửi phụ huynh lớp A; phụ huynh 2 con | mỗi phụ huynh 1 thông báo | `RecipientResolverTest`, `AnnouncementIntegrationTest` |
| 10 | PHT gửi lớp điểm trường khác / toàn trường | 403 | `AnnouncementServiceImplTest` |
| 11 | Hẹn giờ rồi sửa; sửa khi `SENDING` | bản sửa được gửi; 409 | `AnnouncementServiceImplTest` |
| 12 | Hủy hẹn giờ; worker tới giờ | không ai nhận | `AnnouncementServiceImplTest`; gửi hẹn + gửi lại không trùng: `AnnouncementIntegrationTest` |
| 13 | Tệp thứ 6 / > 10 MB / .docx | 400 / 413 / 415 | tệp thứ 6: `AnnouncementServiceImplTest`; dung lượng, loại tệp do `FileStorage` (danh mục `announcement`) kiểm |
| 14 | Người ngoài danh sách tải tệp | 404 | `AnnouncementServiceImplTest` |
| 15 | HT tắt "trẻ đã được đón" | 400; tắt "thực đơn tuần" ⇒ công bố không tạo thông báo | `NotificationRuleServiceImplTest` |
| 16 | Câu chữ có biến lạ | 400 | `NotificationRuleServiceImplTest` |
| 17 | Lịch tháng ngày 31 vào tháng 4; lần trúng ngày nghỉ | chạy 30/4; ngày nghỉ bỏ, `next_run_at` sang lần sau | `AnnouncementScheduleCalculatorTest`, `AnnouncementScheduleServiceImplTest` |
| 18 | PHT mở quy tắc / lịch | 403 | `NotificationRuleServiceImplTest`, `AnnouncementScheduleServiceImplTest` (controller `hasRole('PRINCIPAL')`) |
