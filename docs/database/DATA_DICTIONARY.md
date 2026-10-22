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
| code | VARCHAR(30) | không | Mã campus; UNIQUE (service so khớp không phân biệt hoa thường) | — |
| name | VARCHAR(150) | không | Tên hiển thị; UNIQUE theo `LOWER(name)` (V11) | — |
| short_name | VARCHAR(50) | không | Tên ngắn (V11, mặc định `''`) | — |
| address | VARCHAR(255) | không | Địa chỉ (V11, mặc định `''`) | — |
| phone | VARCHAR(20) | có | 8–20 chữ số (V11) | — |
| is_main | BOOLEAN | không | Điểm trường chính; đúng một điểm chính (`uk_campuses_main` unique một phần) (V11) | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Không xóa được điểm trường chính, điểm trường có lớp hoặc có phân công nhân sự.

## school_years  (module: school-structure, scope: SCHOOL)
Mục đích: năm học; lớp gắn với 1 năm học. Migration: V2.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| name | VARCHAR(20) | không | Vd. `2026-2027`; UNIQUE | — |
| start_date, end_date | DATE | không | CHECK `end_date > start_date`; service chặn chồng khoảng ngày với năm khác | — |
| status | VARCHAR(20) | không | `PLANNED` / `ACTIVE` / `CLOSED` (V10, mặc định `PLANNED`); CLOSED chỉ đọc | — |
| activated_at, activated_by | TIMESTAMPTZ, BIGINT FK user_accounts | có | Lúc/người kích hoạt (V10) | AUTH-01 |
| closed_at, closed_by | TIMESTAMPTZ, BIGINT FK user_accounts | có | Lúc/người kết thúc; kích hoạt năm mới tự đóng năm ACTIVE cũ (V10) | AUTH-01 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `uk_school_years_active` (unique một phần `WHERE status = 'ACTIVE'`) ⇒ tối đa một năm ACTIVE.

## attendance_cutoffs  (module: school-structure, scope: SCHOOL)
Mục đích: giờ chốt điểm danh/báo ăn theo năm học (ATT-06, P-03 để cấu hình). Migration: `V14__create_attendance_cutoffs.sql`. Mỗi lần lưu thêm một dòng (lịch sử); giờ chốt của ngày D = dòng có `effective_from` ≤ D gần nhất, cùng ngày lấy dòng lưu sau. Xóa năm học PLANNED xóa luôn các dòng của năm đó.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| school_year_id | BIGINT FK school_years | không | Năm học | — |
| cutoff_time | TIME | không | Giờ chốt (HH:mm) | ATT-06 |
| effective_from | DATE | không | Ngày bắt đầu áp dụng, nằm trong năm học | ATT-06 |
| note | VARCHAR(500) | không | Ghi chú lần đổi | — |
| created_by | BIGINT FK user_accounts | có | Người đặt | — |
| created_at | TIMESTAMPTZ | không | Lúc đặt | — |
Index: `idx_attendance_cutoffs_school_year_id (school_year_id, effective_from)`.

## classrooms  (module: school-structure, scope: CLASS)
Mục đích: lớp của một campus trong một năm học (năm sau là lớp khác ⇒ phân công cũ không tự áp). Migration: V2.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| campus_id | BIGINT FK campuses | không | Campus của lớp | AUTH-02 |
| school_year_id | BIGINT FK school_years | không | Năm học | — |
| name | VARCHAR(100) | không | UNIQUE cùng `(school_year_id, campus_id)` | — |
| age_group_id | BIGINT FK age_groups | có | Nhóm tuổi của lớp (V7); GV xem kế hoạch năm của nhóm tuổi các lớp mình dạy | EDU-06 |
| room_id | BIGINT FK rooms | có | Phòng học của lớp (V12); một phòng chỉ cho một lớp mỗi năm học (`uk_classrooms_school_year_room`, V13) | — |
| capacity | SMALLINT | có | Sĩ số tối đa (V13); API bắt buộc, ≤ min(`age_groups.max_class_size`, `rooms.capacity`), ≥ số trẻ đang học | — |
| status | VARCHAR(20) | không | `ACTIVE` / `CLOSED` (V13); đóng cùng năm học | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `idx_classrooms_campus_id`, `idx_classrooms_age_group_id`, `idx_classrooms_room_id`. Tên lớp so khớp không phân biệt hoa thường trong cùng năm + điểm trường (service). Lớp có trẻ không đổi nhóm tuổi; có trẻ/GV không đổi điểm trường; có trẻ không xóa được.

## rooms  (module: school-structure, scope: CAMPUS)
Mục đích: phòng của điểm trường (phòng học, bếp, phòng chức năng, phòng ban, kho). Migration: `V12__create_rooms.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| campus_id | BIGINT FK campuses | không | Điểm trường | AUTH-02 |
| code | VARCHAR(20) | không | Mã/số phòng (vd. `P.101`); UNIQUE `(campus_id, LOWER(code))` khi chưa xóa | — |
| name | VARCHAR(50) | không | Tên phòng; UNIQUE `(campus_id, LOWER(name))` khi chưa xóa | — |
| room_type | VARCHAR(20) | không | `CLASS` / `KITCHEN` / `FUNCTION_ROOM` / `DEPARTMENT` / `STORAGE` | — |
| capacity | SMALLINT | có | Sức chứa; bắt buộc với `CLASS`; không hạ dưới sĩ số tối đa của lớp năm chưa kết thúc đang dùng | — |
| manager_user_id | BIGINT FK user_accounts | có | Người phụ trách (nhân sự của điểm trường); phòng học không lưu (= GV chủ nhiệm) | — |
| status | VARCHAR(20) | không | `ACTIVE` / `INACTIVE` / `DELETED` (xóa mềm khi lớp năm cũ còn trỏ tới) | — |
| note | VARCHAR(500) | không | Ghi chú | — |
| deleted_at | TIMESTAMPTZ | có | Lúc xóa mềm | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |

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
| is_homeroom | BOOLEAN | không | GV chủ nhiệm (chỉ TEACHER; mỗi lớp 2 GV, 1 chủ nhiệm) (V15) | — |
| school_year_id | BIGINT FK school_years | có | Năm học của phân công tổ trưởng (1 tổ trưởng/nhóm tuổi/năm, toàn trường) (V15); NULL với phân công cũ | EDU-02 |
| assigned_by | BIGINT FK user_accounts | có | Người phân công (V15) | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Ràng buộc: `ck_staff_assignments_scope` (đúng cột scope theo vai trò), `ck_staff_assignments_valid_range`, `ck_staff_assignments_homeroom`. Kết thúc phân công = đặt `valid_to`, không xóa (giữ lịch sử); phân công chưa từng có hiệu lực (bắt đầu từ hôm nay/ngày đầu năm sắp tới) thì xóa. Phân công qua API: từ max(hôm nay, ngày đầu năm học) tới ngày cuối năm học.
Index: `user_id`, `campus_id`, `classroom_id`, `age_group_id`, `school_year_id`.

## age_groups  (module: school-structure, scope: SCHOOL)
Mục đích: nhóm tuổi (khối) dùng chung toàn trường. Migration: `V7__create_age_groups.sql`, seed 4 nhóm `ag-2`..`ag-5` theo UI.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| code | VARCHAR(20) | không | Mã ổn định (`ag-3`); UNIQUE | — |
| name | VARCHAR(100) | không | Tên đầy đủ, vd. `Mẫu giáo bé (3–4 tuổi)` | — |
| short_name | VARCHAR(50) | không | Tên ngắn, vd. `Mẫu giáo bé` | — |
| sort_order | SMALLINT | không | Thứ tự hiển thị | — |
| age_from_months, age_to_months | SMALLINT | có | Độ tuổi theo tháng, khoảng `[from, to)` trong 0–72; các nhóm không chồng nhau (service) (V13) | — |
| meal_sessions | VARCHAR(40) | không | Bữa trong ngày `MORNING,LUNCH,AFTERNOON` (V13, chuỗi rỗng = chưa cấu hình) | — |
| max_class_size | SMALLINT | có | Sĩ số tối đa mỗi lớp (V13); không hạ dưới sĩ số/số trẻ lớp năm chưa kết thúc | — |
| nutrition_note | VARCHAR(500) | không | Ghi chú dinh dưỡng (V13) | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
V13 điền số liệu cho `ag-2`..`ag-5` theo dữ liệu mẫu FE. Tên UNIQUE theo `LOWER(name)`. Không xóa nhóm có lớp hoặc tổ trưởng.

## school_year_rooms / school_year_campuses / school_year_age_groups  (module: school-structure, scope: SCHOOL)
Mục đích: bản chụp phòng, điểm trường, nhóm tuổi lúc năm học kết thúc (CFG-YR-02). Xem năm đã kết thúc đọc bản chụp; sửa/xóa ở năm sau không đổi năm cũ. Migration: `V17__create_school_year_snapshots.sql` (tạo bản chụp cho năm đã kết thúc trước V17: phòng theo `created_at`/`deleted_at`, các trường khác lấy giá trị lúc chạy migration).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| school_year_id | BIGINT FK school_years (ON DELETE CASCADE) | không | Năm học được chụp; UNIQUE cùng id gốc | CFG-YR-02 |
| room_id / campus_id / age_group_id | BIGINT | không | Id bản ghi gốc (không FK: gốc có thể bị xóa hẳn) | — |
| các cột còn lại | như bảng gốc | | Giá trị lúc kết thúc năm (mã, tên, loại, sức chứa, người phụ trách, trạng thái, địa chỉ, bữa ăn, sĩ số tối đa…) | — |
Ghi khi năm chuyển CLOSED (kết thúc tay hoặc tự đóng khi kích hoạt năm mới), trong cùng transaction.

## children  (module: child, scope: CHILD)
Mục đích: hồ sơ gốc của trẻ (khung tối thiểu; cột nghiệp vụ thêm sau). Migration: `V3__create_children.sql`. Dữ liệu nhạy cảm — không log họ tên/ngày sinh.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| full_name | VARCHAR(150) | không | Họ tên | — |
| date_of_birth | DATE | không | Ngày sinh | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |

## class_placements  (module: child, scope: CLASS)
Mục đích: xếp lớp năm học mới — kết quả dự kiến của trẻ đang học ở năm ACTIVE cho năm PLANNED; áp dụng vào `enrollments` khi kích hoạt năm. Migration: `V16__create_class_placements.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| school_year_id | BIGINT FK school_years | không | Năm học mới (PLANNED) | — |
| child_id | BIGINT FK children | không | Trẻ; UNIQUE `(school_year_id, child_id)` | — |
| from_classroom_id | BIGINT FK classrooms | không | Lớp năm cũ lúc xếp | — |
| action | VARCHAR(20) | không | `PROMOTE` / `STAY` / `LEAVE` / `GRADUATE` | — |
| to_classroom_id | BIGINT FK classrooms | có | Lớp năm mới; bắt buộc với PROMOTE/STAY, NULL với LEAVE/GRADUATE (`ck_class_placements_to_classroom`) | — |
| updated_by | BIGINT FK user_accounts | có | Người xếp | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
FK `school_year_id` ON DELETE CASCADE (xóa năm PLANNED xóa bản xếp lớp). Trẻ của lớp cũ chưa có nhóm tuổi chỉ được LEAVE. Khi kích hoạt: enrollment cũ kết thúc hôm trước ngày bắt đầu (max(hôm nay, ngày đầu năm)); PROMOTE/STAY tạo enrollment mới từ ngày đó.

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

## attendance_records  (module: attendance, scope: CLASS)
Mục đích: điểm danh và tham gia ăn của một trẻ trong một ngày (ATT-01, ATT-02). Migration: `V18__create_attendance.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK children | không | Trẻ; UNIQUE `(child_id, date)` — lưu lặp chỉ cập nhật | ATT-01b |
| classroom_id / campus_id | BIGINT FK | không | Lớp, điểm trường lúc ghi (scope) | AUTH-03 |
| date | DATE | không | Ngày điểm danh | — |
| status | VARCHAR(20) | không | `PRESENT` / `EXCUSED` / `UNEXCUSED` | — |
| meals | VARCHAR(40) | không | Bữa trẻ ăn, dạng `LUNCH,AFTERNOON`; vắng = rỗng; chỉ bữa của nhóm tuổi | ATT-02 |
| recorded_by, recorded_at | BIGINT FK user_accounts, TIMESTAMPTZ | không | Lần ghi gần nhất | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `(classroom_id, date)`.

## attendance_record_history  (module: attendance, scope: CLASS)
Mục đích: lịch sử ghi/sửa từng bản ghi điểm danh (chỉ thêm). Migration: V18.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| record_id | BIGINT FK attendance_records (ON DELETE CASCADE) | không | | — |
| action | VARCHAR(20) | không | `RECORDED` / `UPDATED` | — |
| status, meals | như `attendance_records` | không | Giá trị sau lần ghi | — |
| user_id, at | BIGINT FK user_accounts, TIMESTAMPTZ | không | Ai ghi, lúc nào | — |
| unlock_id | BIGINT FK attendance_day_unlocks | có | Ghi trong lần mở lại ngày | ATT-06 |

## attendance_day_unlocks  (module: attendance, scope: CLASS)
Mục đích: mở lại điểm danh một lớp/ngày đã khóa (HT mọi lớp, PHT lớp điểm trường mình). Migration: V18.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| classroom_id / campus_id | BIGINT FK | không | Lớp được mở lại | AUTH-01, AUTH-02 |
| date | DATE | không | Ngày học được mở lại (từ đầu năm học tới hôm nay) | — |
| valid_until | TIMESTAMPTZ | không | Hạn mở lại: sau lúc mở, không quá hết ngày kết thúc năm học; thu hồi ⇒ = lúc thu hồi | ATT-06 |
| reason | VARCHAR(300) | không | Lý do | — |
| unlocked_by, unlocked_at | BIGINT FK user_accounts, TIMESTAMPTZ | không | | — |
| revoked_by, revoked_at | BIGINT FK user_accounts, TIMESTAMPTZ | có | Kết thúc sớm | — |
Mỗi lớp/ngày tối đa một lần mở còn hiệu lực (service kiểm). Index: `(classroom_id, date)`.

## daily_health_entries  (module: attendance, scope: CHILD)
Mục đích: sức khỏe trong ngày — mỗi lần giáo viên lớp ghi nhận khi tiếp nhận trẻ đã lưu Có mặt (chỉ thêm, không sửa). Giáo viên quan sát, không chẩn đoán. Migration: `V19__create_daily_health_entries.sql`. Dữ liệu sức khỏe — không log.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| request_id | VARCHAR(64) | không | FE sinh mỗi lần bấm lưu; UNIQUE `(recorded_by, request_id)` — gửi lặp trả bản đã lưu | — |
| child_id / classroom_id / campus_id | BIGINT FK | không | Trẻ, lớp, điểm trường lúc ghi (scope) | AUTH-03 |
| date | DATE | không | Ngày ghi nhận (chỉ hôm nay) | — |
| temperature | NUMERIC(3,1) | có | Thân nhiệt đúng giá trị nhập; không tự đánh giá ngưỡng | — |
| observation | VARCHAR(20) | không | `NO_CONCERN` / `NOTED` | — |
| symptoms | VARCHAR(100) | không | Biểu hiện khi NOTED, dạng `COUGH,TIRED` | — |
| note | VARCHAR(500) | không | Ghi chú; NOTED cần biểu hiện hoặc ghi chú | — |
| actions | VARCHAR(100) | không | Xử trí khi NOTED (`MONITOR`, `MEDICAL_ROOM`, `PARENT_INFORMED`, `EARLY_PICKUP`, `REFERRAL`) | — |
| referral_place / referral_time / referral_escort | VARCHAR(150) / TIME / VARCHAR(100) | có | Bắt buộc nơi + giờ khi có `REFERRAL` | — |
| notification_status | VARCHAR(20) | không | `PENDING` (chưa có module thông báo phụ huynh) | — |
| recorded_by, recorded_at | BIGINT FK user_accounts, TIMESTAMPTZ | không | | — |
Index: `(child_id, date)`, `(classroom_id, date)`.

## password_tokens (module: identity-access, scope: ACCOUNT)
Token dùng 1 lần cho luồng mật khẩu (OTP quên mật khẩu, đổi mật khẩu lần đầu, reset). Tạo ở V4 tên `web_auth_challenges`, V6 đổi tên. PK token_hash VARCHAR(64), purpose OTP/FIRST_LOGIN/RESET; user_id FK và keycloak_user_id nullable chỉ cho OTP decoy; lookup_hash/otp_hmac VARCHAR(64) là HMAC, provider_cipher TEXT mã hóa capability provider. created_at/expires_at/resend_available_at/consumed_at TIMESTAMPTZ; attempts 0–5; processing_owner VARCHAR(43). CHECK ngăn password capability thiếu account/provider proof. Không lưu OTP/email plaintext. Index `idx_password_tokens_user`/`idx_password_tokens_expiry` (V5 xóa index lookup_hash). Bảng `web_auth_sessions` (V4) đã xóa ở V5 theo ADR-0015.

## otp_send_limits (module: identity-access, scope: SYSTEM)
Giới hạn số lần gửi email OTP (forgot + resend). Tạo ở V4 tên `web_auth_rate_limits`, V6 đổi tên. PK bucket_hash VARCHAR(64) là HMAC của loại quota + lookup + cửa sổ; used INTEGER ≥0, expires_at TIMESTAMPTZ có index cleanup `idx_otp_send_limits_expiry`. Atomic PostgreSQL upsert không vượt budget. Không lưu IP/email plaintext.

## Kế hoạch giáo dục năm học  (module: education-plan, scope: SCHOOL theo nhóm tuổi)
Migration: `V8__create_edu_year_plans.sql`, `V9__relax_edu_theme_draft_constraints.sql`, `V20__drop_unused_edu_plan_tables.sql` (ADR-0017, theo CAN_DB trang 1; lệch so với CAN_DB ghi ở cuối mục).

| Bảng | Cột chính | Ràng buộc | Rule ID |
| --- | --- | --- | --- |
| edu_year_plans | code VARCHAR(30), school_year_id, age_group_id, title VARCHAR(150), description VARCHAR(500) (rỗng được), status, owner_id/owner_title, sent_by/sent_at, issued_by/issued_at (người, thời điểm ban hành), created_at, updated_at | UNIQUE code; UNIQUE (school_year_id, age_group_id); status ∈ DRAFT/PENDING_VP/REJECTED/APPROVED; FK user_accounts | EDU-01..05 |
| edu_goal_rows | year_plan_id, domain_id, sort_order, kind, text, goal_no | kind ∈ sub/group/goal; `goal_no` có ⇔ kind = goal; xóa theo kế hoạch | EDU-09 |
| edu_goal_contents | goal_row_id, sort_order, text | xóa theo dòng | — |
| edu_themes | year_plan_id, sort_order, name VARCHAR(150), abbr VARCHAR(20) mặc định rỗng, start_date, end_date, festivals, note | Không ràng buộc viết tắt/ngày ở DB (V9): bản nháp lưu được khi còn soạn dở; service kiểm khi gửi/ban hành | EDU-04, EDU-05 |
| edu_breaks | year_plan_id, sort_order, label VARCHAR(150), start_date, end_date | Không CHECK ngày (V9), như chủ đề | — |
| edu_content_theme_ticks | content_id, theme_id | PK 2 cột; xóa theo nội dung/chủ đề | EDU-09 |
| edu_plan_events | year_plan_id, action, action_label, actor_id, actor_name, actor_role_label, tone, note, occurred_at | tone ∈ '' / ok / warn / err (theo UI) | — |
| edu_domains | name VARCHAR(150), display_label, short_label, sort_order, active | UNIQUE name; seed 5 lĩnh vực chuẩn | EDU-09 |
| edu_expected_results | age_group_id, domain_id, sub_domain, text, sort_order | chưa seed dữ liệu | — |

Lệch so với CAN_DB (đã chốt 22/10):
- Bỏ 2 cột `updated_at` lặp.
- `users` → `user_accounts`.
- Cột audit dùng `created_at`/`updated_at` như các bảng khác, thay cho `createdDate`/`updatedDate`/`createdBy`/`updatedBy`.
- Thêm `sort_order` cho `edu_breaks`.
- `edu_plan_events` chưa có cột `branch_plan_id`/`theme_plan_id`/`week_plan_id` (thêm khi có các bảng đó).
- V20 (Tiền chốt 22/10): bỏ `edu_plan_approvals` (trùng `issued_by`/`issued_at` + sự kiện `ISSUE`), bỏ `edu_approval_steps` (thứ tự người ký khi in làm ở task Cấu hình kế hoạch), bỏ cột `attachment_key` (UI không có đính kèm).
