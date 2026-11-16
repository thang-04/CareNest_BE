# Data model — 019 Thông báo của trường

Ba migration, mỗi subtask một file (số chốt lúc làm, sau V129): V130 (G94-289), V131 (G94-290), V132 (G94-291).

## V130 — trạng thái đẩy (US1, G94-289)

`notifications` (đã có) thêm:

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `push_state` | VARCHAR(10) | NOT NULL, CHECK IN (`PENDING`,`SENT`,`FAILED`,`SKIPPED`) | thêm với DEFAULT `'SKIPPED'` (dòng cũ không đẩy — FR-008), sau đó DEFAULT `'PENDING'` |
| `push_attempts` | SMALLINT | NOT NULL DEFAULT 0 | số lần đã thử |
| `push_next_at` | TIMESTAMPTZ | NULL | lần thử kế tiếp; NULL = ngay |

- Chỉ mục một phần: `idx_notifications_push_pending ON notifications (push_next_at NULLS FIRST, id) WHERE push_state = 'PENDING'`.
- Chuyển trạng thái: `PENDING` → `SENT` (≥1 thiết bị nhận) | `SKIPPED` (không có thiết bị) | `PENDING` + `push_next_at` (lỗi tạm thời, chưa quá 60 phút) | `FAILED` (quá 60 phút hoặc lỗi vĩnh viễn ở mọi thiết bị).

`device_tokens`: không đổi cấu trúc; thêm chỉ mục `idx_device_tokens_updated_at (updated_at)` cho việc dọn 270 ngày.

## V131 — thông báo của trường (US2, G94-290)

### `school_announcements`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | BIGINT identity | PK |
| `sender_id` | BIGINT | NOT NULL FK `user_accounts` |
| `title` | VARCHAR(150) | NOT NULL, không rỗng |
| `body` | VARCHAR(4000) | NOT NULL, không rỗng |
| `scope_type` | VARCHAR(10) | NOT NULL CHECK IN (`SCHOOL`,`CAMPUS`,`AGE_GROUP`,`CLASSROOM`) |
| `scope_campus_id` | BIGINT | FK `campuses`; bắt buộc khi `CAMPUS`; `AGE_GROUP` tùy chọn (khối trong một điểm trường) |
| `scope_age_group_id` | BIGINT | FK `age_groups`; bắt buộc khi `AGE_GROUP` |
| `scope_classroom_id` | BIGINT | FK `classrooms`; bắt buộc khi `CLASSROOM` |
| `roles` | VARCHAR(60) | NOT NULL; danh sách `PARENT,TEACHER,KITCHEN,BGH` ngăn bởi dấu phẩy, ≥1 |
| `status` | VARCHAR(10) | NOT NULL CHECK IN (`SCHEDULED`,`SENDING`,`SENT`,`CANCELLED`) |
| `send_at` | TIMESTAMPTZ | NOT NULL; gửi ngay = thời điểm gửi |
| `sent_at` | TIMESTAMPTZ | NULL |
| `recipient_count` | INT | NOT NULL DEFAULT 0 |
| `schedule_id` | BIGINT | NULL (V132 thêm FK tới `announcement_schedules`) |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

- Chỉ mục: `(status, send_at) WHERE status = 'SCHEDULED'`; `(created_at DESC)`.
- Chuyển trạng thái: `SCHEDULED` → `SENDING` → `SENT`; `SCHEDULED` → `CANCELLED`. Gửi ngay: tạo thẳng `SENDING` → `SENT` trong cùng giao dịch. Sửa chỉ khi `SCHEDULED` (clarify Q2). Hẹn giờ: `send_at` > now và ≤ now + 30 ngày.
- Phạm vi theo người gửi: PHT chỉ `CAMPUS`/`AGE_GROUP`/`CLASSROOM` thuộc điểm trường mình (khối bắt buộc kèm `scope_campus_id`); `SCHOOL` và khối toàn trường chỉ HT (FR-013).

### `announcement_attachments`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | BIGINT identity | PK |
| `announcement_id` | BIGINT | NOT NULL FK `school_announcements` ON DELETE CASCADE |
| `file_key` | VARCHAR(255) | NOT NULL |
| `file_name` | VARCHAR(255) | NOT NULL |
| `content_type` | VARCHAR(100) | NOT NULL |
| `size_bytes` | BIGINT | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

- Tối đa 5 / thông báo; mỗi tệp ≤ 10 MB; `jpg, jpeg, png, pdf` (kiểm cả nội dung đầu tệp — `FileTypes.matches`).

### `announcement_templates`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | BIGINT identity | PK |
| `name` | VARCHAR(100) | NOT NULL |
| `title` | VARCHAR(150) | NOT NULL |
| `body` | VARCHAR(4000) | NOT NULL |
| `campus_id` | BIGINT | NULL = mẫu toàn trường; có giá trị = mẫu của điểm trường |
| `created_by` | BIGINT | NOT NULL FK `user_accounts` |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

- PHT thấy mẫu toàn trường + mẫu điểm trường mình; chỉ tạo/sửa/xóa mẫu của điểm trường mình. HT mọi mẫu (FR-017).

### `notifications` thêm

- `announcement_id BIGINT NULL FK school_announcements`; chỉ mục duy nhất `uk_notifications_announcement_user (announcement_id, user_id) WHERE announcement_id IS NOT NULL` (tạo lại an toàn sau khi máy chết).
- CHECK `ck_notifications_type` thêm `SCHOOL_ANNOUNCEMENT`.
- `body` của thông báo trường lưu tối đa 500 ký tự (như hiện tại); nội dung đầy đủ đọc từ `school_announcements` qua `GET /me/notifications/{id}`.

## V132 — quy tắc tự động và lịch lặp (US3, G94-291)

### `notification_rules`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `type` | VARCHAR(40) | PK (tên `NotificationType`) |
| `enabled` | BOOLEAN | NOT NULL |
| `title_template` | VARCHAR(150) | NULL = mặc định |
| `body_template` | VARCHAR(500) | NULL = mặc định |
| `updated_by` | BIGINT | NOT NULL FK `user_accounts` |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

- Chỉ lưu khi HT đã chỉnh; danh mục (bắt buộc / sửa được / biến) nằm trong code `NotificationRuleCatalog`.
- Loại có rule (sửa được câu chữ): `CHILD_PICKED_UP` (PCK-04, bắt buộc), `DAILY_HEALTH_NOTED` (HLT-14, bắt buộc), `MEAL_COUNT_RECONFIRM` (NUT-03, bắt buộc), `MENU_PUBLISHED` (NUT-08, tắt được), `HEALTH_RESULT_PUBLISHED` / `CHILD_EVALUATION_CONFIRMED` / `THEME_EVALUATION_CONFIRMED` (PAR-03, tắt được), `LEAVE_REQUEST_CREATED` (ATT-05, tắt được). Mọi loại khác: bắt buộc, chỉ xem.

### `announcement_schedules`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | BIGINT identity | PK |
| `template_id` | BIGINT | NOT NULL FK `announcement_templates` (xóa mẫu đang dùng ⇒ từ chối) |
| `scope_type`, `scope_campus_id`, `scope_age_group_id`, `scope_classroom_id`, `roles` | như `school_announcements` | |
| `frequency` | VARCHAR(10) | NOT NULL CHECK IN (`WEEKLY`,`MONTHLY`) |
| `day_of_week` | SMALLINT | 1–7 (Thứ Hai = 1), bắt buộc khi `WEEKLY` |
| `day_of_month` | SMALLINT | 1–31, bắt buộc khi `MONTHLY`; tháng thiếu ngày ⇒ ngày cuối tháng (clarify Q3) |
| `send_time` | TIME | NOT NULL |
| `next_run_at` | TIMESTAMPTZ | NOT NULL |
| `active` | BOOLEAN | NOT NULL DEFAULT TRUE |
| `created_by` | BIGINT | NOT NULL FK `user_accounts` |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL |

- Chỉ mục `(next_run_at) WHERE active`. `school_announcements.schedule_id` FK tới bảng này.
- CHECK `ck_notifications_type` thêm `DAILY_HEALTH_NOTED`, `MENU_PUBLISHED`, `LEAVE_REQUEST_CREATED`, `CHILD_EVALUATION_CONFIRMED`, `THEME_EVALUATION_CONFIRMED`.

## Người nhận (dùng chung US2, US3)

| Vai trò | Cách lấy (ngày gửi thực tế) |
|---|---|
| Phụ huynh | lớp đang hoạt động của năm học ACTIVE trong phạm vi → `EnrollmentRepository.findActiveByClassroomIds` → `GuardianChildLinkService.findLinksOfChildren` (userId không null) |
| Giáo viên | `StaffAssignmentService.findCurrentTeacherIds(lớp trong phạm vi)` |
| Bếp | tài khoản `KITCHEN_STAFF` của các điểm trường chứa lớp trong phạm vi (1 lần / điểm trường — AUTH-22) |
| BGH | HT (`findPrincipalIds`) + PHT các điểm trường của phạm vi (`findCampusVicePrincipalIds`); toàn trường ⇒ PHT mọi điểm trường |

Bỏ trùng toàn bộ; bỏ người gửi (clarify Q4).
