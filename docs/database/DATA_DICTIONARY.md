# Data Dictionary

> Status: **ĐANG ĐIỀN** — mỗi bảng thêm cùng thay đổi với migration. Nguồn chuẩn cột là file migration trong `src/main/resources/db/migration`.

Format mỗi bảng:

```markdown
## <table_name>  (module: <module>, scope: CAMPUS|CLASS|CHILD|SCHOOL|SYSTEM)
Mục đích: ...
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
Enum/status: ...
Index: ...
```

## user_accounts  (module: identity-access, scope: SYSTEM)
Mục đích: hồ sơ nghiệp vụ của người dùng; nối với danh tính Keycloak qua `keycloak_user_id` (ADR-0011). Migration: `V1__create_user_accounts.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính, dùng làm `userId` trong CareNest | — |
| keycloak_user_id | VARCHAR(36) | không | = claim `sub` của access token; UNIQUE | AUTH-07 |
| username | VARCHAR(100) | không | Username Keycloak (không đổi được); UNIQUE | — |
| email | VARCHAR(255) | có | Phụ huynh có thể không có email | — |
| full_name | VARCHAR(150) | không | Họ tên đầy đủ (= `firstName` bên Keycloak) | — |
| status | VARCHAR(20) | không | `ACTIVE` / `INACTIVE`; INACTIVE bị chặn mọi request | — |
| created_at, updated_at | TIMESTAMPTZ | không | Thời điểm tạo/sửa | — |
Không có cột mật khẩu/role: Keycloak quản lý.
Index: `uk_user_accounts_keycloak_user_id`, `uk_user_accounts_username`.

## campuses  (module: school-structure, scope: CAMPUS)
Mục đích: điểm trường của trường duy nhất (ADR-0002). Migration: `V2__create_school_structure.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| code | VARCHAR(30) | không | Mã campus; UNIQUE | — |
| name | VARCHAR(150) | không | Tên hiển thị | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |

## school_years  (module: school-structure, scope: SCHOOL)
Mục đích: năm học; lớp gắn với 1 năm học. Migration: V2.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| name | VARCHAR(20) | không | Vd. `2026-2027`; UNIQUE | — |
| start_date, end_date | DATE | không | CHECK `end_date > start_date` | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |

## classrooms  (module: school-structure, scope: CLASS)
Mục đích: lớp của một campus trong một năm học (năm sau là lớp khác ⇒ phân công cũ không tự áp). Migration: V2.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| campus_id | BIGINT FK campuses | không | Campus của lớp | AUTH-02 |
| school_year_id | BIGINT FK school_years | không | Năm học | — |
| name | VARCHAR(100) | không | UNIQUE cùng `(school_year_id, campus_id)` | — |
| age_group_id | BIGINT FK age_groups | có | Nhóm tuổi của lớp (V7); GV xem kế hoạch năm của nhóm tuổi các lớp mình dạy | EDU-06 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `idx_classrooms_campus_id`, `idx_classrooms_age_group_id`.

## staff_assignments  (module: school-structure, scope: theo `staff_role`)
Mục đích: phân công nhân sự — nguồn scope của BGH/GV/bếp (ADR-0003). Migration: V2.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT FK user_accounts | không | Nhân sự được phân công | — |
| staff_role | VARCHAR(30) | không | `PRINCIPAL` / `VICE_PRINCIPAL` / `TEACHER` / `KITCHEN_STAFF` / `AGE_GROUP_TEAM_LEADER` (V7) | AUTH-01..03, 05, EDU-02 |
| campus_id | BIGINT FK campuses | có | Bắt buộc với VICE_PRINCIPAL/KITCHEN_STAFF, NULL với vai trò khác | AUTH-02, AUTH-05 |
| classroom_id | BIGINT FK classrooms | có | Bắt buộc với TEACHER, NULL với vai trò khác | AUTH-03 |
| age_group_id | BIGINT FK age_groups | có | Bắt buộc với AGE_GROUP_TEAM_LEADER, NULL với vai trò khác (V7) | EDU-02 |
| valid_from | DATE | không | Ngày bắt đầu hiệu lực (tính cả ngày này) | — |
| valid_to | DATE | có | Ngày kết thúc (tính cả ngày này); NULL = đang hiệu lực | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Ràng buộc: `ck_staff_assignments_scope` (đúng cột scope theo vai trò), `ck_staff_assignments_valid_range`. Kết thúc phân công = đặt `valid_to`, không xóa (giữ lịch sử).
Index: `user_id`, `campus_id`, `classroom_id`, `age_group_id`.

## age_groups  (module: school-structure, scope: SCHOOL)
Mục đích: nhóm tuổi (khối) dùng chung toàn trường. Migration: `V7__create_age_groups.sql`, seed 4 nhóm `ag-2`..`ag-5` theo UI.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| code | VARCHAR(20) | không | Mã ổn định (`ag-3`); UNIQUE | — |
| name | VARCHAR(100) | không | Tên đầy đủ, vd. `Mẫu giáo bé (3–4 tuổi)` | — |
| short_name | VARCHAR(50) | không | Tên ngắn, vd. `Mẫu giáo bé` | — |
| sort_order | SMALLINT | không | Thứ tự hiển thị | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |

## children  (module: child, scope: CHILD)
Mục đích: hồ sơ gốc của trẻ (khung tối thiểu; cột nghiệp vụ thêm sau). Migration: `V3__create_children.sql`. Dữ liệu nhạy cảm — không log họ tên/ngày sinh.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| full_name | VARCHAR(150) | không | Họ tên | — |
| date_of_birth | DATE | không | Ngày sinh | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |

## enrollments  (module: child, scope: CLASS)
Mục đích: trẻ học lớp nào theo thời gian; campus của trẻ suy từ lớp tại ngày cần xét. Migration: V3.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK children | không | | — |
| classroom_id | BIGINT FK classrooms | không | | — |
| valid_from | DATE | không | Tính cả ngày này | — |
| valid_to | DATE | có | Tính cả ngày này; NULL = đang học | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Chưa có ràng buộc DB chống enrollment chồng thời gian — service phải kiểm khi làm module child.
Index: `child_id`, `classroom_id`.

## guardian_child_links  (module: child, scope: CHILD)
Mục đích: liên kết phụ huynh–trẻ, nguồn scope phụ huynh. Migration: V3.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT FK user_accounts | không | Tài khoản phụ huynh | AUTH-04 |
| child_id | BIGINT FK children | không | | AUTH-04 |
| relationship | VARCHAR(30) | không | Quan hệ (vd. MOTHER, FATHER) — chưa chốt danh sách | — |
| created_at | TIMESTAMPTZ | không | | — |
Index: `uk_guardian_child_links_user_child`, `idx_guardian_child_links_child_id`.

## password_tokens (module: identity-access, scope: ACCOUNT)
Token dùng 1 lần cho luồng mật khẩu (OTP quên mật khẩu, đổi mật khẩu lần đầu, reset). Tạo ở V4 tên `web_auth_challenges`, V6 đổi tên. PK token_hash VARCHAR(64), purpose OTP/FIRST_LOGIN/RESET; user_id FK và keycloak_user_id nullable chỉ cho OTP decoy; lookup_hash/otp_hmac VARCHAR(64) là HMAC, provider_cipher TEXT mã hóa capability provider. created_at/expires_at/resend_available_at/consumed_at TIMESTAMPTZ; attempts 0–5; processing_owner VARCHAR(43). CHECK ngăn password capability thiếu account/provider proof. Không lưu OTP/email plaintext. Index `idx_password_tokens_user`/`idx_password_tokens_expiry` (V5 xóa index lookup_hash). Bảng `web_auth_sessions` (V4) đã xóa ở V5 theo ADR-0015.

## otp_send_limits (module: identity-access, scope: SYSTEM)
Giới hạn số lần gửi email OTP (forgot + resend). Tạo ở V4 tên `web_auth_rate_limits`, V6 đổi tên. PK bucket_hash VARCHAR(64) là HMAC của loại quota + lookup + cửa sổ; used INTEGER ≥0, expires_at TIMESTAMPTZ có index cleanup `idx_otp_send_limits_expiry`. Atomic PostgreSQL upsert không vượt budget. Không lưu IP/email plaintext.

## Kế hoạch giáo dục năm học  (module: education-plan, scope: SCHOOL theo nhóm tuổi)
Migration: `V8__create_edu_year_plans.sql` (ADR-0017, theo CAN_DB trang 1; lệch so với CAN_DB ghi ở cuối mục).

| Bảng | Cột chính | Ràng buộc | Rule ID |
| --- | --- | --- | --- |
| edu_year_plans | code VARCHAR(30), school_year_id, age_group_id, title VARCHAR(150), description VARCHAR(500) (rỗng được), status, owner_id/owner_title, sent_by/sent_at, issued_by/issued_at, attachment_key, created_at, updated_at | UNIQUE code; UNIQUE (school_year_id, age_group_id); status ∈ DRAFT/PENDING_VP/REJECTED/APPROVED; FK user_accounts | EDU-01..05 |
| edu_goal_rows | year_plan_id, domain_id, sort_order, kind, text, goal_no | kind ∈ sub/group/goal; `goal_no` có ⇔ kind = goal; xóa theo kế hoạch | EDU-09 |
| edu_goal_contents | goal_row_id, sort_order, text | xóa theo dòng | — |
| edu_themes | year_plan_id, sort_order, name VARCHAR(150), abbr VARCHAR(20), start_date, end_date, festivals, note | UNIQUE (year_plan_id, lower(abbr)); end ≥ start | — |
| edu_breaks | year_plan_id, sort_order, label VARCHAR(150), start_date, end_date | end ≥ start | — |
| edu_content_theme_ticks | content_id, theme_id | PK 2 cột; xóa theo nội dung/chủ đề | EDU-09 |
| edu_plan_events | year_plan_id, action, action_label, actor_id, actor_name, actor_role_label, tone, note, occurred_at | tone ∈ '' / ok / warn / err (theo UI) | — |
| edu_plan_approvals | year_plan_id, approver_id, approver_role, approver_title, approved_at, note | FK user_accounts | EDU-05 |
| edu_domains | name VARCHAR(150), display_label, short_label, sort_order, active | UNIQUE name; seed 5 lĩnh vực chuẩn | EDU-09 |
| edu_expected_results | age_group_id, domain_id, sub_domain, text, sort_order | chưa seed dữ liệu | — |
| edu_approval_steps | PK (doc_type, step_order), approver_role | doc_type ∈ year/branch/theme_dossier; seed year: AGE_GROUP_TEAM_LEADER → VICE_PRINCIPAL (chỉ để in khối chữ ký) | — |

Lệch so với CAN_DB (đã chốt 22/10):
- Bỏ 2 cột `updated_at` lặp.
- `users` → `user_accounts`.
- Cột audit dùng `created_at`/`updated_at` như các bảng khác, thay cho `createdDate`/`updatedDate`/`createdBy`/`updatedBy`.
- Thêm `sort_order` cho `edu_breaks`.
- `edu_plan_events`/`edu_plan_approvals` chưa có cột `branch_plan_id`/`theme_plan_id`/`week_plan_id` (thêm khi có các bảng đó).
