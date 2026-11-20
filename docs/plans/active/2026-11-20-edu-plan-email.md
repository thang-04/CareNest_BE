---
title: Email cho kế hoạch giáo dục
status: in-progress
owner: Nguyễn Duy Thắng
jira: G94-386 / BE-FEAT-63
branch: feature/G94-386-BE-FEAT-63-edu-plan-email
modules: [notification, education-plan]
rules: [EDU-73, EDU-74]
risk: high
created: 2026-11-20
updated: 2026-11-20
---
# G94-386 — Email cho kế hoạch giáo dục

Nhánh: `feature/G94-386-BE-FEAT-63-edu-plan-email` (từ `origin/dev` 4feeab49) · Làn L (migration, `integration/email`, notification dùng chung) · Trạng thái: đã code, chờ review

## Quyết định đã chốt (Thắng, 20/11/2026)

- Thay "email: hoãn" ở EDU-73, EDU-74: gửi email kèm thông báo trong ứng dụng.
- Nhắc gần hạn bằng email: chỉ gói học kỳ + hồ sơ chủ đề (giữ job `remindNearDeadlines` 06:00 hiện có); kế hoạch năm/tổ không nhắc gần hạn.
- Tần suất: một lần trong khoảng 7 ngày trước hạn (theo cờ `once` sẵn có).

## Hướng làm

Email bám theo thông báo trong ứng dụng: mỗi dòng `notifications` loại `EDU_*` (1 người nhận × 1 sự kiện) sinh 1 dòng hàng chờ email trong cùng giao dịch; worker gửi sau commit. Không đổi logic chọn người nhận (đã đúng chuỗi duyệt, loại người soạn, nhắc GVCN khi thiếu người duyệt).

| AC | Cách đạt |
| --- | --- |
| 1–5 | 6 điểm gọi `notifyUsers` hiện có ở `EduYearPlanServiceImpl`, `EduClassDocServiceImpl` |
| 6 | `remindNearDeadlines` + `once` ⇒ 1 thông báo ⇒ 1 email |
| 7 | UNIQUE(`notification_id`); vòng gửi mới tạo thông báo mới ⇒ email mới |
| 8 | Hàng chờ lưu cùng giao dịch (rollback ⇒ không có dòng); worker giao dịch riêng, lỗi SMTP chỉ đổi trạng thái dòng email |
| 9 | Mỗi người một dòng, một email; thiếu email ⇒ `SKIPPED/NO_EMAIL`, lỗi ⇒ thử lại có backoff rồi `FAILED` |
| 10 | Nút "Mở hồ sơ" = `CARENEST_WEB_BASE_URL` + link sẵn có; Web vẫn bắt đăng nhập, BE vẫn kiểm quyền |

## Thay đổi

1. Migration `V158__notification_email_deliveries.sql`: bảng `notification_email_deliveries` (notification_id UNIQUE FK cascade, `scope` ≤300, state PENDING/SENT/FAILED/SKIPPED, attempts, next_attempt_at, last_attempt_at, completed_at, last_error_kind, created_at) + index hàng chờ.
2. Entity + repository `NotificationEmailDelivery`.
3. `NotificationService`: overload `notifyUsers(..., link, once, scope)`; loại `EDU_*` có link ⇒ thêm dòng email. Overload cũ giữ nguyên (scope = null).
4. Edu call sites truyền `scope`: "Năm học … · Nhóm tuổi …" (năm/tổ); thêm "· Lớp … · Học kỳ n / Chủ đề …" (hồ sơ lớp).
5. `EmailOutboxWorker` (`@Scheduled`, bật/tắt + batch + số lần thử qua cấu hình) + `EduEmailComposer`: tiêu đề `[CareNest] <title>`, HTML + text tiếng Việt: loại sự kiện, tên hồ sơ, phạm vi, nội dung (người làm, ghi chú/lý do/hạn mới/số ngày còn lại), thời điểm, nút "Mở hồ sơ". Mỗi email một người nhận (`to` đơn). Email người nhận đọc bằng query `findRecipient` trong repository notification (gọi AccountService tạo vòng account → child → notification → account).
5b. Thiết kế đồng bộ (Thắng yêu cầu 20/11/2026): `integration/email/CareNestEmailLayout` = khung chung (màu, header, hộp thông tin, nút, link dự phòng, footer) chép đúng `docker/keycloak/.../email/html/carenest-layout.ftl`; email BE nào cũng dựng qua layout này, không tự viết HTML riêng. Đổi một bên ⇒ đổi bên kia.
6. Cấu hình `carenest.email.web-base-url` (`CARENEST_WEB_BASE_URL`), `carenest.email.outbox.*`; `.env.example`, `DEPLOYMENT.md`.
7. Docs: EDU-73, EDU-74 (bỏ "email hoãn"), `docs/modules/education-plan.md`, `notification.md`, `DATA_DICTIONARY.md`.

Log chỉ id/trạng thái; không log email, tiêu đề, nội dung (theo `SmtpEmailGateway`).

## Kiểm thử

- Unit: `NotificationServiceImplTest` (EDU tạo dòng email, loại khác không; `once` không nhân đôi), `EmailOutboxWorkerTest` (SENT; NO_EMAIL; lỗi ⇒ retry ⇒ FAILED; một người lỗi không chặn người khác), composer (tiêu đề, link, escape HTML).
- Integration (Testcontainers sẵn có): rollback giao dịch không còn dòng email; migration chạy sạch.
- `./mvnw -q test` cho các test liên quan + `OpenApiSnapshotTest` (không đổi API).

## Ngoài phạm vi

Gửi kèm tài liệu; nhắc gần hạn kế hoạch năm/tổ; bật/tắt email theo người dùng; FE/APP (không đổi API).

## Progress log

- 20/11/2026: chốt phạm vi, tạo nhánh + plan.
- 20/11/2026: code xong; bỏ AccountService khỏi worker (ArchUnit báo vòng account → child → notification → account), đọc người nhận bằng query repository. Test: NotificationServiceImplTest, EmailOutboxWorkerTest, CareNestEmailLayoutTest, EmailOutboxIntegrationTest, PushOutboxIntegrationTest, ArchitectureRulesTest, OpenApiSnapshotTest pass (44); eduplan + notification suite pass trước bước sửa vòng.
- 20/11/2026: thêm logo header (`<web>/email/logo-mark.png`, như `logoUrl` mail Keycloak) và gộp lời chào + đoạn đầu như mail OTP.
- 20/11/2026: dev đã có V157 (stock movement) ⇒ migration của task dùng V158; merge dev.
