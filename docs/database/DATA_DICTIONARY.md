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
| citizen_id | VARCHAR(12) | có | CCCD phụ huynh, khóa nhận diện phụ huynh; UNIQUE, CHECK 12 chữ số (V10). Nhân sự NULL; tài khoản phụ huynh cũ bổ sung khi hồ sơ trẻ ghi CCCD | AUTH-18 |
| created_at, updated_at | TIMESTAMPTZ | không | Thời điểm tạo/sửa | — |
Không có cột mật khẩu: Keycloak quản lý. Role gốc ở Keycloak (`primary_role` chỉ là bản sao).
Index: `uk_user_accounts_keycloak_user_id`, `uk_user_accounts_username`, `uk_user_accounts_citizen_id`, `idx_user_accounts_primary_role`.
Đã chốt 2026-10-28, chưa làm: AUTH-21 đổi tên đăng nhập phụ huynh khi đổi SĐT (cột `username` hiện "không đổi được" ⇒ BE thiết kế cách đổi, Phase 4 của plan `2026-10-28-child-enrollment-guardian-accounts`) — `docs/modules/identity-access.md`.

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
Mục đích: hồ sơ gốc của trẻ. Migration: `V3__create_children.sql`, `V10__child_enrollment_guardian_accounts.sql`. Dữ liệu nhạy cảm — không log họ tên/ngày sinh.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| code | VARCHAR(20) | không | Mã trẻ `HS` + 2 số cuối năm tiếp nhận + số thứ tự trong năm (≥3 chữ số, vd. `HS26001`); UNIQUE; số thứ tự lấy từ `child_code_sequences` | — |
| full_name | VARCHAR(150) | không | Họ tên (API nhận ≤100) | — |
| date_of_birth | DATE | không | Ngày sinh | — |
| gender | VARCHAR(10) | có (xem dưới) | `MALE` / `FEMALE`; bắt buộc với hồ sơ mới/sửa | CHD-08 |
| status | VARCHAR(20) | không | `ACTIVE` (đang học) / `PENDING_PLACEMENT` (chờ xếp lớp) / `LEFT` (đã nghỉ học) | CHD-03 |
| intake_campus_id | BIGINT FK campuses | có | Điểm trường của trẻ chưa có lớp (bắt buộc khi `PENDING_PLACEMENT`); có lớp thì điểm trường theo lớp (enrollment) | CHD-02 |
| enrolled_on | DATE | không | Ngày tiếp nhận | — |
| left_on, left_reason | DATE, VARCHAR(300) | có | Ngày, lý do nghỉ học; có đúng khi `LEFT`, mở lại hồ sơ xóa cả hai; `left_on ≥ enrolled_on` | CHD-03 |
| readmitted_on | DATE | có | Ngày mở lại hồ sơ gần nhất | CHD-03 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Ràng buộc: `ck_children_status`, `ck_children_gender`, `ck_children_left`, `ck_children_left_on`; `ck_children_complete` (có giới tính, chờ xếp lớp thì có điểm trường) là **NOT VALID**: áp cho mọi dòng thêm/sửa, không kiểm trẻ có trước V10 (chỉ dữ liệu demo — trẻ đó phải nhập đủ khi sửa hồ sơ). V10 sinh `code`, `status`, `enrolled_on`, `intake_campus_id` cho trẻ có sẵn (plan mục 3.9).
FE đọc `campusId` của trẻ = điểm trường theo lớp nếu có lớp, không thì `intake_campus_id`.
Index: `uk_children_code`, `idx_children_intake_campus_id`, `idx_children_date_of_birth` (kiểm trùng hồ sơ — CHD-04).

## child_code_sequences  (module: child, scope: SCHOOL)
Mục đích: số thứ tự mã trẻ theo năm; giao dịch tiếp nhận tăng bằng upsert nên nhiều người lưu cùng lúc không cấp trùng mã. Migration: V10.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| code_prefix | VARCHAR(10) PK | không | `HS` + 2 số cuối năm, vd. `HS26` | — |
| last_number | INTEGER | không | Số thứ tự đã cấp gần nhất; CHECK > 0 | — |

## enrollments  (module: child, scope: CLASS)
Mục đích: trẻ học lớp nào theo thời gian; campus của trẻ suy từ lớp tại ngày cần xét. Migration: V3.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK children | không | | — |
| classroom_id | BIGINT FK classrooms | không | | — |
| valid_from | DATE | không | Tính cả ngày này | — |
| valid_to | DATE | có | Tính cả ngày này; NULL = đang học | — |
| reason | VARCHAR(300) | có | Lý do chuyển lớp (bắt buộc khi chuyển, V10) | — |
| created_by | BIGINT FK user_accounts | có | Người xếp/chuyển lớp (V10); NULL ở dữ liệu cũ | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Xếp lớp mở enrollment mới; chuyển lớp đóng enrollment đang mở rồi mở enrollment mới. `uk_enrollments_child_open` (V10, partial unique `valid_to IS NULL`): mỗi trẻ tối đa 1 enrollment đang mở; chồng thời gian giữa các enrollment đã đóng vẫn do service kiểm.
Index: `child_id`, `classroom_id`, `uk_enrollments_child_open`.

## guardian_child_links  (module: child, scope: CHILD)
Mục đích: liên kết phụ huynh–trẻ, nguồn scope phụ huynh; tạo/gỡ qua `/children/{id}/guardians` (AUTH-18, AUTH-19). Migration: V3, V9 (CHECK quan hệ), V10 (6 loại).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT FK user_accounts | không | Tài khoản phụ huynh | AUTH-04 |
| child_id | BIGINT FK children | không | | AUTH-04 |
| relationship | VARCHAR(30) | không | `FATHER` / `MOTHER` / `GRANDFATHER` / `GRANDMOTHER` / `SIBLING` / `GUARDIAN` (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ — như hồ sơ trẻ); `OTHER` chỉ còn ở dữ liệu trước V10 (CHECK `ck_guardian_child_links_relationship`) | AUTH-18 |
| created_at | TIMESTAMPTZ | không | | — |
Index: `uk_guardian_child_links_user_child`, `idx_guardian_child_links_child_id`.

## child_profiles  (module: child, scope: CHILD)
Mục đích: hành chính của trẻ, 1–1 với `children`; phiếu khám sức khỏe đọc từ đây; danh sách trẻ không trả phần này. Migration: V10. Dữ liệu nhạy cảm (CHD-05).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| child_id | BIGINT PK, FK children | không | | — |
| id_number | VARCHAR(20) | có | CCCD/mã định danh 12 số hoặc hộ chiếu 6–12 chữ và số (API kiểm định dạng) | CHD-05 |
| premature | VARCHAR(3) | không | `YES` / `NO` — sinh non | CHD-05 |
| gestation_weeks | SMALLINT | có | 20–42; bắt buộc khi `premature = YES` (CHECK) | CHD-05 |
| ethnicity, beneficiary | VARCHAR(100) | không | Dân tộc, đối tượng | CHD-05 |
| payment_source | VARCHAR(20) | không | Luôn `BHYT` (DEFAULT + CHECK), không nhập | CHD-05 |
| blood_type | VARCHAR(100) | có | Nhóm máu | CHD-05 |
| address_province, address_ward | VARCHAR(100) | không | Tỉnh/thành, phường/xã nơi ở | CHD-05 |
| address_street | VARCHAR(300) | không | Số nhà/thôn/xóm | CHD-05 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |

## child_guardians  (module: child, scope: CHILD)
Mục đích: phụ huynh trên hồ sơ trẻ, có trước khi có tài khoản (AUTH-20); nguồn scope của phụ huynh vẫn là `guardian_child_links`. Migration: V10.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK children | không | | — |
| position | SMALLINT | không | Thứ tự nhập 0–2 ⇒ tối đa 3 phụ huynh mỗi trẻ (CHECK + UNIQUE cùng `child_id`) | AUTH-18 |
| full_name | VARCHAR(150) | không | | — |
| relationship | VARCHAR(30) | không | 6 mã như `guardian_child_links`; `OTHER` chỉ ở dữ liệu chuyển từ liên kết cũ | AUTH-18 |
| phone | VARCHAR(10) | không | SĐT chuẩn hóa 10 số bắt đầu 0 (CHECK) = tên đăng nhập tài khoản phụ huynh | AUTH-18 |
| citizen_id | VARCHAR(12) | có (xem dưới) | CCCD 12 chữ số, khóa nhận diện phụ huynh | AUTH-18 |
| email | VARCHAR(255) | có | Email liên hệ, chữ thường | AUTH-18 |
| user_id | BIGINT FK user_accounts | có | Tài khoản đã kích hoạt cho trẻ này; NULL = chưa kích hoạt | AUTH-20 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Ràng buộc: UNIQUE (`child_id`, `position`), (`child_id`, `phone`), (`child_id`, `citizen_id`) dạng DEFERRABLE INITIALLY DEFERRED (Sửa hồ sơ đổi thông tin giữa hai phụ huynh trong một lần lưu); UNIQUE (`child_id`, `user_id`). `ck_child_guardians_citizen_id_required` **NOT VALID**: dòng thêm/sửa phải có CCCD; dòng V10 chuyển từ liên kết cũ chưa có CCCD (nhập bổ sung). V10 chuyển mỗi liên kết có sẵn có tên đăng nhập là SĐT thành 1 dòng (tối đa 3 mỗi trẻ).
`accountStatus` trả FE: `NOT_ACTIVATED` khi chưa có `guardian_child_links` với trẻ; có thì `user_accounts.status`.
Index: `idx_child_guardians_phone`, `idx_child_guardians_citizen_id`, `idx_child_guardians_user_id` (kiểm một người một tài khoản trên mọi hồ sơ — AUTH-18).

## child_health_declarations  (module: child, scope: CHILD)
Mục đích: khai báo sức khỏe khi tiếp nhận, 1–1 với trẻ; dị ứng có hiệu lực ngay cho bếp, điểm danh. Migration: V10. Dữ liệu sức khỏe nhạy cảm.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| child_id | BIGINT PK, FK children | không | | — |
| allergies_state, diet_state, other_notes_state | VARCHAR(20) | không | `REPORTED` (Có) / `NONE_REPORTED` (Không có) / `NOT_PROVIDED` (Chưa cung cấp); danh sách dị ứng ở `child_allergies` | CHD-06 |
| diet_text, other_notes_text | VARCHAR(500) | có | Có đúng khi mục là `REPORTED` (CHECK) | CHD-06 |
| self_history_state, family_history_state | VARCHAR(20) | không | Tiền sử bản thân, gia đình: chỉ `REPORTED` / `NONE_REPORTED` | HLT-06 |
| self_history_text, family_history_text | VARCHAR(300) | có | Tên bệnh; có đúng khi `REPORTED` | HLT-06 |
| tb_contact_state | VARCHAR(20) | không | Tiếp xúc người bệnh lao: `REPORTED` / `NONE_REPORTED` | CHD-07 |
| tb_contact_date | DATE | có | Ngày tiếp xúc gần nhất; có đúng khi `REPORTED` | CHD-07 |
| declared_by | BIGINT FK user_accounts | không | Người lưu gần nhất | — |
| declared_at | TIMESTAMPTZ | không | Lần lưu gần nhất | — |

## child_allergies  (module: child, scope: CHILD)
Mục đích: dị ứng của khai báo sức khỏe, thứ tự theo `id`; bếp và điểm danh đọc bảng này. Migration: V10.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK child_health_declarations | không | | CHD-06 |
| name | VARCHAR(100) | không | Thực phẩm/chất gây dị ứng | CHD-06 |
Index: `idx_child_allergies_child_id`.

## child_health_declaration_history  (module: child, scope: CHILD)
Mục đích: lịch sử khai báo sức khỏe, chỉ thêm. Migration: V10.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK children | không | | — |
| action | VARCHAR(30) | không | `DECLARED` / `UPDATED` / `UPDATED_FROM_FOLLOW_UP` | HLT-06 |
| user_id | BIGINT FK user_accounts | không | Người lưu | — |
| note | VARCHAR(500) | có | Lý do thay đổi | — |
| allergy_change, history_change | JSONB | có | `{ from, to }` khi dị ứng / tiền sử đổi | CHD-06, HLT-06 |
| created_at | TIMESTAMPTZ | không | | — |
Index: `idx_child_health_declaration_history_child_id` (`child_id`, `created_at`).

## child_history  (module: child, scope: CHILD)
Mục đích: lịch sử hồ sơ trẻ, chỉ thêm. Migration: V10.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK children | không | | — |
| action | VARCHAR(20) | không | `ENROLLED` / `UPDATED` / `WITHDRAWN` / `READMITTED` | CHD-03 |
| user_id | BIGINT FK user_accounts | không | Người làm | — |
| note | VARCHAR(500) | có | Lý do | CHD-03 |
| changes | JSONB | có | `[{ field, label, from, to }]` từng ô đổi; số định danh của trẻ chỉ 3 số cuối | CHD-05 |
| created_at | TIMESTAMPTZ | không | | — |
Index: `idx_child_history_child_id` (`child_id`, `created_at`).

## parent_sms_logs  (module: identity-access, scope: ACCOUNT)
Mục đích: lần gửi SMS tài khoản phụ huynh; FE hiện lần gửi gần nhất của từng phụ huynh (`smsStatus`, `smsAt`). Không lưu nội dung, mật khẩu. Migration: V10.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT FK user_accounts | không | Tài khoản phụ huynh | — |
| phone | VARCHAR(10) | không | SĐT đã gửi | — |
| purpose | VARCHAR(20) | không | `ACTIVATION` / `PASSWORD_RESET` / `USERNAME_CHANGED` | L-15 |
| status | VARCHAR(10) | không | `SENT` / `FAILED` | L-15 |
| sent_at | TIMESTAMPTZ | không | | — |
Index: `idx_parent_sms_logs_user_id` (`user_id`, `sent_at DESC`).

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
