# API contract — 019 Thông báo của trường

Mọi endpoint có tiền tố API hiện có (`WebMvcConfig`), trả `ResponseJson`. Lỗi dùng `ApiCode` dải `12xx THÔNG BÁO`.

## US1 — G94-289

| Method | Path | Quyền | Mô tả |
|---|---|---|---|
| GET | `/me/notifications/{id}` | đăng nhập | Một thông báo của chính mình (mở từ banner). Người khác ⇒ 404 `NOTIFICATION_NOT_FOUND` (FR-007) |

Response `NotificationResponse` (đã có) + trường mới `announcementId` (null nếu không phải thông báo trường, thêm ở G94-290), không phá tương thích. Nội dung đầy đủ và tệp của thông báo trường: `GET /me/announcements/{id}` (US2).

Push `data` gửi App: `type`, `notificationId`, `childId` (nếu có). Banner loại nhạy cảm: câu chung (R5).

## US2 — G94-290 (HT, PHT)

| Method | Path | Body | Mô tả |
|---|---|---|---|
| POST | `/announcements/recipient-preview` | `RecipientTargetRequest` | `{ total, byRole: { PARENT, TEACHER, KITCHEN, BGH } }` (FR-011) |
| POST | `/announcements` | multipart: `data` (`AnnouncementRequest` JSON) + `files[]` (≤5) | Gửi ngay (`sendAt` null) hoặc hẹn giờ |
| PUT | `/announcements/{id}` | multipart: `data` (`AnnouncementRequest` + `keepAttachmentIds`) + `files[]` | Sửa khi `SCHEDULED` (clarify Q2) |
| POST | `/announcements/{id}/cancel` | — | Hủy khi `SCHEDULED` |
| GET | `/announcements?page&size` | — | Danh sách: HT mọi thông báo; PHT thông báo thuộc điểm trường mình hoặc do mình gửi |
| GET | `/announcements/{id}` | — | Chi tiết: người gửi, thời điểm, phạm vi, vai trò, trạng thái, số người nhận, tệp. Không có ai đã đọc (FR-015) |
| GET | `/me/announcements/{id}` | — | Người nhận (hoặc người gửi, BGH trong phạm vi) xem nội dung đầy đủ + tệp: `{ id, title, body, sentAt, attachments[] }`; khác ⇒ 404 |
| GET | `/announcement-attachments/{attachmentId}/content` | — | Tải tệp: người gửi, BGH trong phạm vi, người nhận; khác ⇒ 404 (FR-016) |
| GET | `/announcement-templates` | — | Mẫu: HT mọi mẫu; PHT mẫu toàn trường + điểm trường mình |
| POST | `/announcement-templates` | `AnnouncementTemplateRequest` | Tạo |
| PUT | `/announcement-templates/{id}` | `AnnouncementTemplateRequest` | Sửa |
| DELETE | `/announcement-templates/{id}` | — | Xóa (mẫu đang dùng cho lịch lặp ⇒ 409) |

```text
RecipientTargetRequest { scopeType: SCHOOL|CAMPUS|AGE_GROUP|CLASSROOM, campusId?, ageGroupId?, classroomId?, roles: [PARENT|TEACHER|KITCHEN|BGH] }
AnnouncementRequest    { title (1–150), body (1–4000), target: RecipientTargetRequest, sendAt?: ISO instant, keepAttachmentIds?: [id] }
AnnouncementTemplateRequest { name (1–100), title (1–150), body (1–4000), campusId? }
AnnouncementResponse   { id, title, body, target, status, sendAt, sentAt, recipientCount, sender: { id, fullName }, attachments[], canEdit }
```

Lỗi mới (12xx):

| Code | HTTP | Khi |
|---|---|---|
| `ANNOUNCEMENT_NOT_FOUND` 1202 | 404 | không có hoặc ngoài phạm vi xem |
| `ANNOUNCEMENT_SCOPE_DENIED` 1203 | 403 | PHT gửi ngoài điểm trường / toàn trường (FR-013) |
| `ANNOUNCEMENT_NOT_EDITABLE` 1204 | 409 | sửa/hủy khi không còn `SCHEDULED` |
| `ANNOUNCEMENT_NO_RECIPIENTS` 1205 | 400 | phạm vi × vai trò ra 0 người |
| `ANNOUNCEMENT_TOO_MANY_FILES` 1206 | 400 | > 5 tệp |
| `ANNOUNCEMENT_INVALID_SEND_AT` 1207 | 400 | giờ hẹn đã qua hoặc quá 30 ngày |
| `ANNOUNCEMENT_TEMPLATE_NOT_FOUND` 1208 | 404 | |
| `ANNOUNCEMENT_TEMPLATE_IN_USE` 1209 | 409 | xóa mẫu đang có lịch lặp |

Tệp sai loại / quá lớn dùng mã sẵn có `UNSUPPORTED_MEDIA_TYPE`, `FILE_TOO_LARGE`, `FILE_CONTENT_MISMATCH`.

## US3 — G94-291 (chỉ HT)

| Method | Path | Body | Mô tả |
|---|---|---|---|
| GET | `/notification-rules` | — | Mọi loại: `{ type, ruleCode, label, mandatory, editable, enabled, titleTemplate, bodyTemplate, variables[], customized }` |
| PUT | `/notification-rules/{type}` | `{ enabled, titleTemplate?, bodyTemplate? }` | Bắt buộc mà tắt ⇒ 400; loại chỉ xem mà sửa câu chữ ⇒ 400; biến lạ ⇒ 400 có `fieldErrors` |
| GET | `/announcement-schedules` | — | Danh sách lịch |
| POST | `/announcement-schedules` | `AnnouncementScheduleRequest` | Tạo |
| PUT | `/announcement-schedules/{id}` | `AnnouncementScheduleRequest` | Sửa (tính lại `nextRunAt`) |
| DELETE | `/announcement-schedules/{id}` | — | Xóa |

```text
AnnouncementScheduleRequest { templateId, target: RecipientTargetRequest, frequency: WEEKLY|MONTHLY, dayOfWeek? (1–7), dayOfMonth? (1–31), sendTime: "HH:mm", active }
```

Lỗi mới: `NOTIFICATION_RULE_MANDATORY` 1210 (400), `NOTIFICATION_RULE_READ_ONLY` 1211 (400), `NOTIFICATION_RULE_UNKNOWN_VARIABLE` 1212 (400), `ANNOUNCEMENT_SCHEDULE_NOT_FOUND` 1213 (404).

## Tác động FE/APP

- APP: nhận push FCM (`data.notificationId` ⇒ gọi `GET /me/notifications/{id}`), bỏ trùng theo `notificationId`. Task APP riêng.
- FE: màn soạn / danh sách / mẫu / quy tắc / lịch. Task FE-FEAT riêng. Endpoint cũ không đổi.
