# Tasks: Thông báo của trường — BGH gửi, quy tắc tự động, đẩy lên điện thoại

**Input**: `specs/019-school-notifications/` (plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md)

**Tests**: có — spec có Independent Test + Acceptance Scenarios; quickstart liệt kê test theo kịch bản.

Đường dẫn Java tương đối `src/main/java/com/carenest/` (main) và `src/test/java/com/carenest/` (test).

## Phase 1: Setup

- [x] T001 Thêm cấu hình `carenest.push` (`fcm.credentials-file`, `fcm.project-id`, `worker-delay` 5s, `batch-size` 100, `max-age` 60m, `device-max-idle` 270d) và danh mục storage `announcement` (`folder: announcements/`, `code: announcement`, `max-size: 10MB`, `types: [jpg, jpeg, png, pdf]`) trong `src/main/resources/application.yml`; thêm `ANNOUNCEMENT` vào `utils/enums/FileCategory.java`

## Phase 2: Foundational

- [x] T002 Thêm dải mã `12xx` mới (1202–1213 theo contracts/api.md) vào `utils/ApiCode.java`

## Phase 3: User Story 1 — Đẩy lên điện thoại (P1, G94-289) 🎯 MVP

**Goal**: mọi thông báo được lưu rồi đẩy bất đồng bộ qua outbox, thử lại, không trùng.
**Independent Test**: quickstart kịch bản 1–8.

- [x] T003 [US1] Migration `src/main/resources/db/migration/V130__notification_push_outbox.sql`: `push_state VARCHAR(10) NOT NULL DEFAULT 'SKIPPED'` CHECK IN (`PENDING`,`SENT`,`FAILED`,`SKIPPED`) rồi `SET DEFAULT 'PENDING'`; `push_attempts SMALLINT NOT NULL DEFAULT 0`; `push_next_at TIMESTAMPTZ NULL`; chỉ mục một phần `WHERE push_state = 'PENDING'`; chỉ mục `device_tokens(updated_at)`
- [x] T004 [US1] `utils/enums/PushState.java` + cột trong `entity/notification/Notification.java` (`of(...)` đặt `PENDING`), phương thức `markPushed`, `markSkipped`, `markFailed`, `scheduleRetry(Instant next)`
- [x] T005 [US1] `integration/push/PushGateway.java`: `send` ném `PushException(kind = INVALID_TOKEN | RETRYABLE | PERMANENT, Duration retryAfter)`; cập nhật `LoggingPushGateway`
- [x] T006 [US1] `integration/push/FcmProperties.java` + `integration/push/FcmPushGateway.java` (FCM HTTP v1, JWT RS256 bằng JDK, cache access token, phân loại lỗi theo research R3; `@ConditionalOnProperty carenest.push.fcm.credentials-file`); `LoggingPushGateway` thành `@ConditionalOnMissingBean`
- [x] T007 [US1] `repository/notification/NotificationRepository.java`: `claimDue(Instant now, int limit)` native `SELECT id … WHERE push_state='PENDING' AND (push_next_at IS NULL OR push_next_at <= :now) ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED`; `lockPending(Long id)`; `DeviceTokenRepository.deleteByUpdatedAtBefore(Instant)`
- [x] T008 [US1] `service/notification/impl/PushBanner.java`: tiêu đề/nội dung banner, che loại nhạy cảm (sức khỏe, PAR-03 — FR-006, clarify Q5)
- [x] T009 [US1] `service/notification/impl/PushOutboxWorker.java`: `@Scheduled(fixedDelayString = carenest.push.worker-delay)` lấy id đến hạn, xử lý từng dòng trong `TransactionTemplate` riêng (khóa SKIP LOCKED → đẩy mọi thiết bị → SENT/SKIPPED/retry/FAILED; INVALID_TOKEN xóa thiết bị; backoff 1 phút × 2^(n-1) ≤ 30 phút ±20%, tôn trọng retryAfter, ngừng sau 60 phút) + cron 03:10 dọn thiết bị > 270 ngày; xóa `PushDispatchListener.java` và event `NotificationsCreatedEvent`
- [x] T010 [US1] `GET /me/notifications/{id}` trong `controller/notification/NotificationController.java` + `NotificationService.getMine` (người khác ⇒ `NOTIFICATION_NOT_FOUND`)
- [x] T011 [P] [US1] Test `service/notification/impl/PushOutboxWorkerTest.java` (kịch bản 1–5) và `service/notification/impl/PushBannerTest.java`
- [x] T012 [P] [US1] Test `integration/push/FcmPushGatewayTest.java` (ký JWT bằng khóa sinh trong test, phân loại lỗi qua HTTP server giả `com.sun.net.httpserver`)
- [x] T013 [US1] Test `service/notification/impl/PushOutboxIntegrationTest.java` (Testcontainers: dòng cũ SKIPPED, 2 worker không trùng) và controller test `GET /me/notifications/{id}` trong `controller/notification/NotificationControllerTest.java`
- [x] T014 [US1] Cập nhật `docs/modules/notification.md` (outbox, trạng thái đẩy, FCM), `docs/database/DATA_DICTIONARY.md`, `docs/api/openapi.yaml`; `./mvnw verify` đạt ⇒ commit `G94-289`

**Checkpoint**: US1 chạy độc lập; mọi thông báo hiện có được đẩy qua outbox.

## Phase 4: User Story 2 — BGH gửi thông báo (P2, G94-290)

**Goal**: HT/PHT gửi ngay, hẹn giờ, sửa, hủy, mẫu, đính kèm.
**Independent Test**: quickstart kịch bản 9–14.

- [x] T015 [US2] Migration `V131__school_announcements.sql`: bảng `school_announcements`, `announcement_attachments`, `announcement_templates` đúng data-model.md (title VARCHAR(150), body VARCHAR(4000), CHECK `scope_type`, `status`); `notifications.announcement_id` + chỉ mục duy nhất một phần `(announcement_id, user_id)`; CHECK `ck_notifications_type` thêm `SCHOOL_ANNOUNCEMENT`
- [x] T016 [P] [US2] Entity `entity/announcement/SchoolAnnouncement.java`, `AnnouncementAttachment.java`, `AnnouncementTemplate.java` + `announcementId` trong `dto/notification/NotificationResponse.java` + enum `AnnouncementStatus`, `AnnouncementScopeType`, `RecipientRole` trong `utils/enums/`; `NotificationType.SCHOOL_ANNOUNCEMENT`; `Notification.announcementId`
- [x] T017 [P] [US2] Repository `repository/announcement/SchoolAnnouncementRepository.java` (`lockById` PESSIMISTIC_WRITE, `claimDue` SKIP LOCKED), `AnnouncementAttachmentRepository.java`, `AnnouncementTemplateRepository.java`
- [x] T018 [US2] DTO trong `dto/announcement/` (`RecipientTargetRequest`, `AnnouncementRequest`, `AnnouncementResponse`, `RecipientPreviewResponse`, `AnnouncementTemplateRequest`, `AnnouncementTemplateResponse`, `AnnouncementAttachmentResponse`, `MyAnnouncementResponse`)
- [x] T019 [US2] `service/announcement/impl/RecipientResolver.java`: lớp trong phạm vi (năm học ACTIVE, `ClassroomService.findActiveYearClassrooms`), phụ huynh/GV/bếp/BGH theo bảng data-model, bỏ trùng, bỏ người gửi; kiểm phạm vi người gửi (PHT chỉ điểm trường mình, toàn trường chỉ HT — FR-013)
- [x] T020 [US2] `NotificationService.notifyAnnouncement(announcementId, userIds, title, body)` (bỏ người đã có dòng cùng announcement — chạy lại an toàn)
- [x] T021 [US2] `service/announcement/AnnouncementService.java` + `impl/AnnouncementServiceImpl.java`: preview, create (gửi ngay / hẹn ≤30 ngày), update (khóa, chỉ `SCHEDULED`), cancel, list, detail, attachments (≤5, `FileStorage.store` danh mục ANNOUNCEMENT, xóa tệp khi bỏ), tải tệp (người gửi/BGH trong phạm vi/người nhận, khác ⇒ 404), `getMine` cho người nhận
- [x] T022 [US2] `impl/AnnouncementDispatchWorker.java`: `@Scheduled` 30 giây lấy `SCHEDULED` đến giờ (SKIP LOCKED) → `SENDING` → giao → `SENT`; cũng nhận lại `SENDING` bị bỏ dở
- [x] T023 [US2] `service/announcement/AnnouncementTemplateService.java` + impl (HT mọi mẫu; PHT mẫu toàn trường xem được, sửa/xóa chỉ mẫu điểm trường mình — FR-017)
- [x] T024 [US2] Controller `controller/announcement/AnnouncementController.java` (`/announcements…`, `/announcement-attachments/{id}/content`, `/me/announcements/{id}`) và `AnnouncementTemplateController.java`, `@PreAuthorize` HT/PHT (riêng `/me/…` đăng nhập)
- [x] T025 [P] [US2] Test `service/announcement/impl/RecipientResolverTest.java`, `AnnouncementServiceImplTest.java`, `AnnouncementTemplateServiceImplTest.java`
- [x] T026 [US2] Test `controller/announcement/AnnouncementControllerTest.java` và `service/announcement/impl/AnnouncementIntegrationTest.java` (gửi ngay, hẹn/hủy, worker)
- [x] T027 [US2] Cập nhật `docs/modules/notification.md`, `DATA_DICTIONARY.md`, `openapi.yaml`; verify đạt ⇒ commit `G94-290`

**Checkpoint**: US2 dùng được cùng US1.

## Phase 5: User Story 3 — Quy tắc tự động và lịch lặp (P3, G94-291)

**Goal**: HT bật/tắt, sửa câu chữ; nối HLT-14, NUT-08, ATT-05, PAR-03; lịch lặp.
**Independent Test**: quickstart kịch bản 15–18.

- [x] T028 [US3] Ghi PAR-07..PAR-10 (quyết định 08/11 + clarify 15/11) vào `docs/business/BUSINESS_RULES.md`
- [x] T029 [US3] Migration `V132__notification_rules_schedules.sql`: `notification_rules`, `announcement_schedules` (CHECK `frequency`, `day_of_week` 1–7, `day_of_month` 1–31), FK `school_announcements.schedule_id`; CHECK type thêm `DAILY_HEALTH_NOTED`, `MENU_PUBLISHED`, `LEAVE_REQUEST_CREATED`, `CHILD_EVALUATION_CONFIRMED`, `THEME_EVALUATION_CONFIRMED`
- [x] T030 [US3] `service/notification/impl/NotificationRuleCatalog.java` (mã rule, nhãn, bắt buộc, sửa được, biến, câu chữ mặc định) + `NotificationTemplates.render`; entity/repo `NotificationRule`
- [x] T031 [US3] `NotificationService.notifyEvent(type, userIds, vars, childId)` (tắt ⇒ bỏ; điền câu chữ) + `NotificationRuleService`/impl + `NotificationRuleController` (`/notification-rules`, chỉ HT)
- [x] T032 [US3] Chuyển `PickupServiceImpl` (PCK-04), `MealCountServiceImpl` (NUT-03), `HealthNotifier.resultPublished` (PAR-03 công bố) sang `notifyEvent`
- [x] T033 [US3] Nối sự kiện mới: `DailyHealthServiceImpl.addEntry` (HLT-14, phụ huynh), `MealPlanServiceImpl.publishMealPlan` (NUT-08, phụ huynh trẻ đang học), `LeaveRequestServiceImpl.createLeaveRequest` (ATT-05, GV lớp), `ChildEvaluationServiceImpl.confirm`, `ThemeEvaluationServiceImpl.confirm` (PAR-03, phụ huynh)
- [x] T034 [US3] Lịch lặp: entity/repo `AnnouncementSchedule`, `AnnouncementScheduleCalculator` (tuần/tháng, ngày cuối tháng), `AnnouncementScheduleService`/impl, `AnnouncementScheduleWorker` (mỗi phút, bỏ ngày nghỉ `SchoolYearService.isSchoolDay`), `AnnouncementScheduleController` (chỉ HT); xóa mẫu đang dùng ⇒ `ANNOUNCEMENT_TEMPLATE_IN_USE`
- [x] T035 [P] [US3] Test `NotificationRuleServiceImplTest.java`, `NotificationTemplatesTest.java`, `AnnouncementScheduleCalculatorTest.java`, `AnnouncementScheduleWorkerTest.java`; sửa test hiện có của nơi phát sự kiện
- [x] T036 [US3] Cập nhật docs + openapi; verify đạt ⇒ commit `G94-291`

## Phase 6: Polish

- [x] T037 Converge: đối chiếu spec FR-001…FR-022 với code, thêm task còn thiếu; push nhánh

## Dependencies

Setup → Foundational → US1 → US2 → US3 (US2 dùng outbox US1; US3 dùng mẫu, người nhận US2). Trong mỗi phase: migration → entity/repo → service → controller → test → docs/commit.

## Parallel

- US1: T011 ‖ T012 sau T009. US2: T016 ‖ T017; T025 sau T021. US3: T035 sau T034.

## Implementation Strategy

MVP = US1 (mọi thông báo hiện có lên điện thoại). Sau đó US2, US3; mỗi story commit riêng mã Jira, verify trước mỗi commit.

## Delta Tiền chốt 17/11/2026 — PAR-13, OBS-30

Yêu cầu/14 mục domain, AC, contract, dữ liệu không migration, kế hoạch, tasks và analyze/converge tại [bổ sung dọn thông báo đã hủy](domain-spec.md). Bộ lọc q/status, DELETE một hoặc mọi CANCELLED khớp filter+scope trên mọi trang, canDelete; PAR-07 không tự nhận giữ nguyên. Nhắc điểm danh mỗi 5 phút từ 08:00 đến trước giờ khóa cấu hình, thay một lần trong 30 phút trước khóa.

- [x] T038 Query/filter/canDelete, khóa và xóa/afterCommit cleanup (AC1–4); bằng chứng tại domain-spec.md.

- [x] T039 Controller DELETE, GET filter, OpenAPI và API index (AC1–4); bằng chứng tại domain-spec.md.

- [x] T040 FE/API/mock bộ lọc, xóa một/tất cả và xác nhận (AC1–4); bằng chứng tại domain-spec.md.

- [x] T041 Nhắc điểm danh [08:00,cutoff), slot 5 phút và test biên (AC6); bằng chứng tại domain-spec.md.

- [x] T042 Regression không tự nhận ở resolver/mock và giữ gửi hẹn/lặp (AC5); bằng chứng tại domain-spec.md.

- [x] T043 Analyze/converge cuối, targeted tests và cập nhật tài liệu/giới hạn; bằng chứng tại domain-spec.md.

### Điều chỉnh tiếp — timing tập trung và chọn dòng
Theo domain-spec.md AC7–9/T7–T10: giữ DB hiện có, timing defaults/cron về YAML + properties, GET cancelledCount, POST delete-selection (ids hoặc allMatching/excludedIds/filter), UI checkbox thay nút xóa tất cả. User chốt chọn tất cả mọi trang; không đổi scope và mặc định giờ.
