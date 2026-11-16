# Implementation Plan: Thông báo của trường — BGH gửi, quy tắc tự động, đẩy lên điện thoại

**Branch**: `feature/G94-288-BE-FEAT-55-school-notifications` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: `specs/019-school-notifications/spec.md` (đã clarify 5 câu ngày 15/11).

## Summary

Ba giai đoạn theo subtask, commit riêng từng mã Jira:

1. **G94-289 (US1):** biến bảng `notifications` thành outbox (cột trạng thái đẩy); worker `@Scheduled` + `FOR UPDATE SKIP LOCKED` đẩy qua `PushGateway`; thử lại lũy tiến tới 60 phút; `FcmPushGateway` gọi FCM HTTP v1 bằng JDK (không thêm thư viện); xóa token hỏng, dọn thiết bị 270 ngày; banner che loại nhạy cảm; `GET /me/notifications/{id}`. Bỏ `PushDispatchListener`.
2. **G94-290 (US2):** module `announcement`: chọn người nhận phạm vi × vai trò, xem trước, gửi ngay / hẹn giờ / sửa / hủy, mẫu soạn sẵn, đính kèm S3 qua `FileStorage`.
3. **G94-291 (US3):** `NotificationRuleCatalog` + bảng `notification_rules` (bật/tắt, câu chữ có biến); `notifyEvent` dùng cho PCK-04, NUT-03, HLT-14, NUT-08, PAR-03, ATT-05; lịch gửi lặp tuần/tháng bỏ ngày nghỉ. Ghi PAR-07..PAR-10 vào `BUSINESS_RULES.md` trước khi code.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (web, data-jpa, security resource server, scheduling sẵn có), Flyway, AWS S3 SDK sẵn có. **Không thêm dependency** (FCM qua `java.net.http.HttpClient` + `java.security`, research R3).

**Storage**: PostgreSQL 17 (3 migration V130–V132); S3 riêng tư qua `FileStorage` (danh mục mới `announcement`).

**Testing**: JUnit 5 + Mockito (service), `@WebMvcTest` (controller), Testcontainers PostgreSQL (outbox + announcement integration), ArchUnit, OpenAPI snapshot.

**Target Platform**: Linux server (Docker trên VPS), 1 hoặc nhiều instance.

**Project Type**: web-service (REST)

**Performance Goals**: banner ≤ 30 giây sau khi tạo (worker 5 giây, lô 100); gửi thông báo cho cả trường (~vài trăm người) trong một request < 2 giây.

**Constraints**: thao tác nghiệp vụ không chờ đẩy; không ghi token/nội dung có tên trẻ vào log; không đẩy lại dòng cũ.

**Scale/Scope**: 1 trường, 2 điểm trường, vài trăm phụ huynh, ~2 thiết bị/người.

## Constitution Check

| Nguyên tắc | Đánh giá |
|---|---|
| 1. Giữ kiến trúc; dependency cần duyệt | Đạt — không thêm dependency; module mới theo lớp controller/service/repository/entity sẵn có |
| 2. BE giữ rule, kiểm phạm vi ở server | Đạt — phạm vi PHT kiểm trong service (`StaffAccessPolicy.managedCampusIds`), tải tệp kiểm người nhận |
| 3. Không dữ liệu thật/secret trong test/log | Đạt — log chỉ id/loại/đếm; file khóa FCM qua biến môi trường |
| 4. Dùng lại storage; không lưu ảnh trẻ công khai | Đạt — `FileStorage` S3 riêng tư |
| 5. Tương thích consumer; schema qua migration mới | Đạt — chỉ thêm trường/endpoint; V130–V132 mới |
| 6. Spec → clarify/plan/tasks/analyze → implement; plan L cần duyệt | Tiền yêu cầu 15/11 chạy liền plan → analyze → tasks → implement → converge, không hỏi thêm ⇒ coi là duyệt. Thay đổi DB ghi ở data-model.md và báo bàn giao |
| 7. Bằng chứng kiểm chứng thật; không commit/push khi chưa cho | Tiền cho commit + push trong tin nhắn 15/11 |

Kết luận: không vi phạm, không cần Complexity Tracking. Kiểm lại sau Phase 1: đạt (không phát sinh thành phần mới ngoài bảng/endpoint trên).

## Project Structure

### Documentation (this feature)

```text
specs/019-school-notifications/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md
├── contracts/api.md
└── tasks.md
```

### Source Code (`src/main/java/com/carenest/`)

```text
integration/push/      PushGateway (thêm kết quả/ngoại lệ phân loại), LoggingPushGateway, FcmPushGateway, FcmProperties
entity/notification/   Notification (+push_state…, announcement_id), PushState, NotificationRule
repository/notification/ NotificationRepository (claim SKIP LOCKED), DeviceTokenRepository (xóa cũ), NotificationRuleRepository
service/notification/  NotificationService (+getMine, notifyEvent), impl/PushOutboxWorker, impl/PushBanner,
                       impl/NotificationRuleCatalog, impl/NotificationRuleServiceImpl, NotificationRuleService
entity/announcement/   SchoolAnnouncement, AnnouncementAttachment, AnnouncementTemplate, AnnouncementSchedule
repository/announcement/ …Repository
service/announcement/  AnnouncementService, AnnouncementTemplateService, AnnouncementScheduleService,
                       impl/RecipientResolver, impl/AnnouncementServiceImpl, impl/AnnouncementDispatchWorker,
                       impl/AnnouncementTemplateServiceImpl, impl/AnnouncementScheduleServiceImpl,
                       impl/AnnouncementScheduleCalculator, impl/AnnouncementScheduleWorker
controller/announcement/ AnnouncementController, AnnouncementTemplateController, AnnouncementScheduleController
controller/notification/ NotificationController (+GET /{id}), NotificationRuleController
dto/announcement/, dto/notification/  request/response record
utils/ApiCode (12xx), utils/enums/NotificationType (+6 loại), FileCategory (+ANNOUNCEMENT)
resources/db/migration/ V130__notification_push_outbox.sql, V131__school_announcements.sql, V132__notification_rules_schedules.sql
resources/application.yml  carenest.push.*, storage.categories.announcement
```

Nơi phát sự kiện sửa ở US3: `PickupServiceImpl`, `MealCountServiceImpl`, `DailyHealthServiceImpl`, `MealPlanServiceImpl`, `LeaveRequestServiceImpl`, `HealthNotifier`, `ChildEvaluationServiceImpl`, `ThemeEvaluationServiceImpl`.

Tài liệu cập nhật: `docs/modules/notification.md`, `docs/business/BUSINESS_RULES.md` (PAR-07..10), `docs/api/openapi.yaml` (snapshot), `docs/database/DATA_DICTIONARY.md`.

**Structure Decision**: một service Spring Boot sẵn có; module `announcement` mới ở tầng nghiệp vụ, gọi `notification` (tầng dưới); `notification` không gọi ngược. Thông báo trường trong danh sách mang `announcementId`; App/Web mở nội dung đầy đủ qua `GET /me/announcements/{id}` của module `announcement`.

## Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Deploy đẩy lại thông báo cũ | V130 đặt `SKIPPED` cho mọi dòng có sẵn |
| Đẩy trùng khi máy chết giữa lúc gửi | Cửa sổ rất nhỏ (R1); App bỏ trùng theo `notificationId` |
| Gửi nhầm phạm vi | Xem trước số người nhận; kiểm phạm vi ở server |
| Khóa FCM lộ | Chỉ đọc từ đường dẫn file trong biến môi trường; không log |

## Progress log

### 2026-11-15 — implement + converge
- Xong T001–T037. Migration V130 (outbox), V131 (thông báo trường), V132 (quy tắc, lịch lặp). Không thêm dependency.
- Kiểm chứng: test đơn vị + controller mới và bị chạm (93 test) đạt; integration Testcontainers PushOutbox, Announcement, Pickup, LeaveRequest, Attendance, HealthCheck, MealCount, MealPlan, ThemeEvaluation đạt; ArchUnit, ControllerSecurityCoverage, OpenApiSnapshot (đã cập nhật snapshot), Spotless đạt.
- `mvnw verify` toàn bộ: chạy tới 208 lớp test, không lỗi (trừ 1 test mới bị lệch ngẫu nhiên do jitter, đã sửa) thì bị hệ thống dừng vì máy thiếu bộ nhớ; chưa chạy lại toàn bộ.
- Thêm giả định: HLT-14 chỉ báo khi có biểu hiện (NOTED), không báo lần kiểm tra bình thường; worker nền tắt trong test (`src/test/resources/config/application.yml`).
- Converge: FR-001…FR-022 và edge case đều có code + test; không thêm task.

### 2026-11-15 — analyze
- Sửa lệch contracts ↔ plan ↔ tasks: nội dung thông báo trường đọc qua `GET /me/announcements/{id}` (không nhúng vào `/me/notifications/{id}`), `NotificationResponse` thêm `announcementId`. Không còn lệch CRITICAL/HIGH; FR-001…FR-022 đều có task (T003–T036).

### 2026-11-15 — plan
- Tiền yêu cầu chạy liền plan → analyze → tasks → implement → converge, commit + push, không hỏi thêm.
- Giả định tự chọn (báo bàn giao): FCM qua JDK không thêm thư viện; trạng thái đẩy theo thông báo (không theo thiết bị); quyền theo vai trò (HT, PHT) thay vì thêm `Permission` mới; PAR-03 tách 3 loại thông báo, mỗi loại bật/tắt riêng; nội dung thông báo trường tối đa 4000 ký tự.
