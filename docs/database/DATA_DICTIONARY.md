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
| username | VARCHAR(100) | không | Username Keycloak (không đổi được); UNIQUE. Phụ huynh = SĐT chuẩn hóa; nhân sự không được là SĐT (AUTH-18) | — |
| email | VARCHAR(255) | có | Nhân sự: email đăng nhập/khôi phục mật khẩu (có trên Keycloak). Phụ huynh: email liên hệ, có thể null, chỉ lưu ở đây (không gửi Keycloak, không dùng đăng nhập/khôi phục). Không UNIQUE: phụ huynh được trùng email nhân sự | AUTH-18 |
| full_name | VARCHAR(150) | không | Họ tên đầy đủ (= `firstName` bên Keycloak) | — |
| status | VARCHAR(20) | không | `ACTIVE` / `INACTIVE`; INACTIVE bị chặn mọi request | — |
| primary_role | VARCHAR(20) | có | Bản sao role Keycloak khi CareNest tạo tài khoản (V8); lọc danh sách + kiểm phạm vi quản lý nhân sự; quyền người gọi vẫn theo token. NULL = chưa rõ (chỉ Admin quản lý) | AUTH-15..17 |
| created_at, updated_at | TIMESTAMPTZ | không | Thời điểm tạo/sửa | — |
Không có cột mật khẩu: Keycloak quản lý. Role gốc ở Keycloak (`primary_role` chỉ là bản sao).
Index: `uk_user_accounts_keycloak_user_id`, `uk_user_accounts_username`, `idx_user_accounts_primary_role`.
Đã chốt 2026-10-28, chưa làm: AUTH-21 đổi tên đăng nhập phụ huynh khi đổi SĐT (cột `username` hiện "không đổi được" ⇒ BE thiết kế cách đổi), AUTH-18 lưu CCCD phụ huynh làm khóa nhận diện — `docs/modules/identity-access.md`.

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
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `idx_classrooms_campus_id`.

## staff_assignments  (module: school-structure, scope: theo `staff_role`)
Mục đích: phân công nhân sự — nguồn scope của BGH/GV/bếp (ADR-0003). Migration: V2.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT FK user_accounts | không | Nhân sự được phân công | — |
| staff_role | VARCHAR(20) | không | `PRINCIPAL` / `VICE_PRINCIPAL` / `TEACHER` / `KITCHEN_STAFF` | AUTH-01..03, 05 |
| campus_id | BIGINT FK campuses | có | Bắt buộc với VICE_PRINCIPAL/KITCHEN_STAFF, NULL với vai trò khác | AUTH-02, AUTH-05 |
| classroom_id | BIGINT FK classrooms | có | Bắt buộc với TEACHER, NULL với vai trò khác | AUTH-03 |
| valid_from | DATE | không | Ngày bắt đầu hiệu lực (tính cả ngày này) | — |
| valid_to | DATE | có | Ngày kết thúc (tính cả ngày này); NULL = đang hiệu lực | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Ràng buộc: `ck_staff_assignments_scope` (đúng cột scope theo vai trò), `ck_staff_assignments_valid_range`. Kết thúc phân công = đặt `valid_to`, không xóa (giữ lịch sử).
Index: `user_id`, `campus_id`, `classroom_id`.

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
Mục đích: liên kết phụ huynh–trẻ, nguồn scope phụ huynh; tạo/gỡ qua `/children/{id}/guardians` (AUTH-18, AUTH-19). Migration: V3, V9 (CHECK quan hệ).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT FK user_accounts | không | Tài khoản phụ huynh | AUTH-04 |
| child_id | BIGINT FK children | không | | AUTH-04 |
| relationship | VARCHAR(30) | không | `MOTHER` / `FATHER` / `GUARDIAN` / `OTHER` (CHECK `ck_guardian_child_links_relationship`, V9). Đã chốt 2026-10-28, chưa làm: thêm `GRANDFATHER` / `GRANDMOTHER` / `SIBLING` (6 loại như hồ sơ trẻ), `OTHER` chỉ còn ở dữ liệu cũ | AUTH-18 |
| created_at | TIMESTAMPTZ | không | | — |
Index: `uk_guardian_child_links_user_child`, `idx_guardian_child_links_child_id`.

## password_tokens (module: identity-access, scope: ACCOUNT)
Token dùng 1 lần cho luồng mật khẩu (OTP quên mật khẩu, đổi mật khẩu lần đầu, reset). Tạo ở V4 tên `web_auth_challenges`, V6 đổi tên. PK token_hash VARCHAR(64), purpose OTP/FIRST_LOGIN/RESET; user_id FK và keycloak_user_id nullable chỉ cho OTP decoy; lookup_hash/otp_hmac VARCHAR(64) là HMAC, provider_cipher TEXT mã hóa capability provider. created_at/expires_at/resend_available_at/consumed_at TIMESTAMPTZ; attempts 0–5; processing_owner VARCHAR(43). CHECK ngăn password capability thiếu account/provider proof. Không lưu OTP/email plaintext. Index `idx_password_tokens_user`/`idx_password_tokens_expiry` (V5 xóa index lookup_hash). Bảng `web_auth_sessions` (V4) đã xóa ở V5 theo ADR-0015.

## otp_send_limits (module: identity-access, scope: SYSTEM)
Giới hạn số lần gửi email OTP (forgot + resend). Tạo ở V4 tên `web_auth_rate_limits`, V6 đổi tên. PK bucket_hash VARCHAR(64) là HMAC của loại quota + lookup + cửa sổ; used INTEGER ≥0, expires_at TIMESTAMPTZ có index cleanup `idx_otp_send_limits_expiry`. Atomic PostgreSQL upsert không vượt budget. Không lưu IP/email plaintext.

## role_permissions  (module: identity-access, scope: SYSTEM)
Mục đích: ma trận quyền theo role do Hiệu trưởng bật/tắt (ADR-0017); có dòng = role có quyền. Migration: `V7__create_permissions.sql` (seed = quyền mặc định của enum `Permission`).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| role | VARCHAR(20) | không | Chỉ `VICE_PRINCIPAL`/`TEACHER`/`KITCHEN_STAFF` (CHECK); role khác cố định trong code | AUTH-11 |
| permission_code | VARCHAR(64) | không | Mã enum `Permission` (lưu chuỗi; mã lạ bị bỏ qua khi tính quyền) | AUTH-10 |
| updated_by | BIGINT | có | FK `user_accounts`; null với dòng seed | AUTH-14 |
| updated_at | TIMESTAMPTZ | không | Thời điểm bật | — |
Index: `uk_role_permissions_role_permission` (role, permission_code).

## user_permission_overrides  (module: identity-access, scope: SYSTEM)
Mục đích: ngoại lệ quyền của từng người so với ma trận role (ADR-0017). Migration: V7.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT | không | FK `user_accounts`, người được cấp/thu quyền | AUTH-10 |
| permission_code | VARCHAR(64) | không | Mã enum `Permission` | AUTH-10 |
| effect | VARCHAR(10) | không | `GRANT` (cộng thêm) / `REVOKE` (thu hồi phần ma trận, không trừ phần cố định) | AUTH-10, AUTH-13 |
| reason | VARCHAR(500) | không | Lý do | AUTH-14 |
| created_by | BIGINT | không | FK `user_accounts`, người sửa gần nhất; CHECK `created_by <> user_id` | AUTH-12 |
| created_at, updated_at | TIMESTAMPTZ | không | Thời điểm tạo/sửa | — |
Index: `uk_user_permission_overrides_user_permission` (user_id, permission_code).

## permission_change_log  (module: identity-access, scope: SYSTEM)
Mục đích: lịch sử mọi thay đổi ma trận/ngoại lệ, chỉ thêm (AUTH-14). Migration: V7.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| batch_id | UUID | không | Các dòng của cùng một lần lưu | AUTH-14 |
| actor_user_id | BIGINT | không | FK `user_accounts`, người đổi | AUTH-14 |
| target_role / target_user_id | VARCHAR(20) / BIGINT | có | Đúng một trong hai (CHECK): ô ma trận của role hoặc ngoại lệ của người | AUTH-14 |
| permission_code | VARCHAR(64) | không | Mã quyền đã đổi | — |
| before_state, after_state | VARCHAR(10) | không | `ON`/`OFF` (ma trận), `GRANT`/`REVOKE`/`INHERIT` (ngoại lệ) | AUTH-14 |
| reason | VARCHAR(500) | không | Lý do bắt buộc | AUTH-14 |
| changed_at | TIMESTAMPTZ | không | Thời điểm đổi | — |
Index: `idx_permission_change_log_changed_at`, `idx_permission_change_log_target_role`, `idx_permission_change_log_target_user` (kèm `changed_at DESC`).
