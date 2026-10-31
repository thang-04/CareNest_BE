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
| citizen_id | VARCHAR(12) | có | CCCD phụ huynh, khóa nhận diện phụ huynh; UNIQUE, CHECK 12 chữ số (V39). Nhân sự NULL; tài khoản phụ huynh cũ bổ sung khi hồ sơ trẻ ghi CCCD | AUTH-18 |
| created_at, updated_at | TIMESTAMPTZ | không | Thời điểm tạo/sửa | — |
Không có cột mật khẩu: Keycloak quản lý. Role gốc ở Keycloak (`primary_role` chỉ là bản sao).
Index: `uk_user_accounts_keycloak_user_id`, `uk_user_accounts_username`, `uk_user_accounts_citizen_id`, `idx_user_accounts_primary_role`.
Đã chốt 2026-10-28, chưa làm: AUTH-21 đổi tên đăng nhập phụ huynh khi đổi SĐT (cột `username` hiện "không đổi được" ⇒ BE thiết kế cách đổi, Phase 4 của plan `2026-10-28-child-enrollment-guardian-accounts`) — `docs/modules/identity-access.md`.

## campuses  (module: school-structure, scope: CAMPUS)
Mục đích: điểm trường của trường duy nhất (ADR-0002). Migration: `V2__create_school_structure.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| code | VARCHAR(30) | không | Mã campus; UNIQUE (service so khớp không phân biệt hoa thường) | — |
| name | VARCHAR(150) | không | Tên hiển thị; UNIQUE theo `LOWER(name)` (V14) | — |
| short_name | VARCHAR(50) | không | Tên ngắn (V14, mặc định `''`) | — |
| address | VARCHAR(255) | không | Địa chỉ (V14, mặc định `''`) | — |
| phone | VARCHAR(20) | có | 8–20 chữ số (V14) | — |
| is_main | BOOLEAN | không | Điểm trường chính; đúng một điểm chính (`uk_campuses_main` unique một phần) (V14) | — |
| status | VARCHAR(20) | không | `ACTIVE` / `INACTIVE` (ngừng hoạt động) (V25) | CFG-CP-01 |
| deactivated_at | TIMESTAMPTZ | có | Lúc ngừng hoạt động gần nhất (V25) | CFG-CP-01 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Không xóa được điểm trường chính, điểm trường có lớp hoặc có phân công nhân sự (dùng ngừng hoạt động).

## school_years  (module: school-structure, scope: SCHOOL)
Mục đích: năm học; lớp gắn với 1 năm học. Migration: V2.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| name | VARCHAR(20) | không | Vd. `2026-2027`; UNIQUE | — |
| start_date, end_date | DATE | không | CHECK `end_date > start_date`; service chặn chồng khoảng ngày với năm khác | — |
| status | VARCHAR(20) | không | `PLANNED` / `ACTIVE` / `CLOSED` (V13, mặc định `PLANNED`); CLOSED chỉ đọc | — |
| activated_at, activated_by | TIMESTAMPTZ, BIGINT FK user_accounts | có | Lúc/người kích hoạt (V13) | AUTH-01 |
| closed_at, closed_by | TIMESTAMPTZ, BIGINT FK user_accounts | có | Lúc/người kết thúc; kích hoạt năm mới tự đóng năm ACTIVE cũ (V13) | AUTH-01 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `uk_school_years_active` (unique một phần `WHERE status = 'ACTIVE'`) ⇒ tối đa một năm ACTIVE.

## attendance_cutoffs  (module: school-structure, scope: SCHOOL)
Mục đích: giờ chốt điểm danh/báo ăn theo năm học (ATT-06, P-03 để cấu hình). Migration: `V17__create_attendance_cutoffs.sql`. Mỗi lần lưu thêm một dòng (lịch sử); giờ chốt của ngày D = dòng có `effective_from` ≤ D gần nhất, cùng ngày lấy dòng lưu sau. Xóa năm học PLANNED xóa luôn các dòng của năm đó.
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
Lưu ý SCH-02 (khảo sát team 27/10): trường giữ lớp qua các năm, có thể đổi tên, thêm lớp mới khi cần; mô hình "mỗi năm một bản ghi lớp" hiện tại cần chức năng chuyển năm hoặc đổi thiết kế — chờ thiết kế school-structure. Chưa có cột nhóm tuổi (SCH-01) và phòng (SCH-03).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| campus_id | BIGINT FK campuses | không | Campus của lớp | AUTH-02 |
| school_year_id | BIGINT FK school_years | không | Năm học | — |
| name | VARCHAR(100) | không | UNIQUE cùng `(school_year_id, campus_id)` | — |
| age_group_id | BIGINT FK age_groups | có | Nhóm tuổi của lớp (V10); GV xem kế hoạch năm của nhóm tuổi các lớp mình dạy | EDU-06 |
| room_id | BIGINT FK rooms | có | Phòng học của lớp (V15); một phòng chỉ cho một lớp mỗi năm học (`uk_classrooms_school_year_room`, V16) | — |
| capacity | SMALLINT | có | Sĩ số tối đa (V16); API bắt buộc, ≤ min(`age_groups.max_class_size`, `rooms.capacity`), ≥ số trẻ đang học | — |
| status | VARCHAR(20) | không | `ACTIVE` / `CLOSED` (V16); đóng cùng năm học | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `idx_classrooms_campus_id`, `idx_classrooms_age_group_id`, `idx_classrooms_room_id`. Tên lớp so khớp không phân biệt hoa thường trong cùng năm + điểm trường (service). Lớp có trẻ không đổi nhóm tuổi; có trẻ/GV không đổi điểm trường; có trẻ không xóa được.

## rooms  (module: school-structure, scope: CAMPUS)
Mục đích: phòng của điểm trường (phòng học, bếp, phòng chức năng, phòng ban, kho). Migration: `V15__create_rooms.sql`.
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
| staff_role | VARCHAR(30) | không | `PRINCIPAL` / `VICE_PRINCIPAL` / `TEACHER` / `KITCHEN_STAFF` / `AGE_GROUP_TEAM_LEADER` (V10) | AUTH-01..03, 05, EDU-02 |
| campus_id | BIGINT FK campuses | có | Bắt buộc với VICE_PRINCIPAL/KITCHEN_STAFF, NULL với vai trò khác | AUTH-02, AUTH-05 |
| classroom_id | BIGINT FK classrooms | có | Bắt buộc với TEACHER, NULL với vai trò khác | AUTH-03 |
| age_group_id | BIGINT FK age_groups | có | Bắt buộc với AGE_GROUP_TEAM_LEADER, NULL với vai trò khác (V10) | EDU-02 |
| valid_from | DATE | không | Ngày bắt đầu hiệu lực (tính cả ngày này) | — |
| valid_to | DATE | có | Ngày kết thúc (tính cả ngày này); NULL = đang hiệu lực | — |
| is_homeroom | BOOLEAN | không | GV chủ nhiệm (chỉ TEACHER; mỗi lớp 2 GV, 1 chủ nhiệm) (V18) | — |
| school_year_id | BIGINT FK school_years | có | Năm học của phân công tổ trưởng (1 tổ trưởng/nhóm tuổi/năm, toàn trường) (V18); NULL với phân công cũ | EDU-02 |
| assigned_by | BIGINT FK user_accounts | có | Người phân công (V18) | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Ràng buộc: `ck_staff_assignments_scope` (đúng cột scope theo vai trò), `ck_staff_assignments_valid_range`, `ck_staff_assignments_homeroom`. Kết thúc phân công = đặt `valid_to`, không xóa (giữ lịch sử); phân công chưa từng có hiệu lực (bắt đầu từ hôm nay/ngày đầu năm sắp tới) thì xóa. Phân công qua API: từ max(hôm nay, ngày đầu năm học) tới ngày cuối năm học.
Index: `user_id`, `campus_id`, `classroom_id`, `age_group_id`, `school_year_id`.

## age_groups  (module: school-structure, scope: SCHOOL)
Mục đích: nhóm tuổi (khối) dùng chung toàn trường. Migration: `V10__create_age_groups.sql`, seed 4 nhóm `ag-2`..`ag-5` theo UI.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| code | VARCHAR(20) | không | Mã ổn định (`ag-3`); UNIQUE | — |
| name | VARCHAR(100) | không | Tên đầy đủ, vd. `Mẫu giáo bé (3–4 tuổi)` | — |
| short_name | VARCHAR(50) | không | Tên ngắn, vd. `Mẫu giáo bé` | — |
| sort_order | SMALLINT | không | Thứ tự hiển thị | — |
| age_from_months, age_to_months | SMALLINT | có | Độ tuổi theo tháng, khoảng `[from, to)` trong 0–72; các nhóm không chồng nhau (service) (V16) | — |
| meal_sessions | VARCHAR(40) | không | Bữa trong ngày `MORNING,LUNCH,AFTERNOON` (V16, chuỗi rỗng = chưa cấu hình) | — |
| max_class_size | SMALLINT | có | Sĩ số tối đa mỗi lớp (V16); không hạ dưới sĩ số/số trẻ lớp năm chưa kết thúc | — |
| nutrition_note | VARCHAR(500) | không | Ghi chú dinh dưỡng (V16) | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
V16 điền số liệu cho `ag-2`..`ag-5` theo dữ liệu mẫu FE. Tên UNIQUE theo `LOWER(name)`. Không xóa nhóm có lớp hoặc tổ trưởng.

## school_year_rooms / school_year_campuses / school_year_age_groups  (module: school-structure, scope: SCHOOL)
Mục đích: bản chụp phòng, điểm trường, nhóm tuổi lúc năm học kết thúc (CFG-YR-02). Xem năm đã kết thúc đọc bản chụp; sửa/xóa ở năm sau không đổi năm cũ. Migration: `V20__create_school_year_snapshots.sql` (tạo bản chụp cho năm đã kết thúc trước V20: phòng theo `created_at`/`deleted_at`, các trường khác lấy giá trị lúc chạy migration).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| school_year_id | BIGINT FK school_years (ON DELETE CASCADE) | không | Năm học được chụp; UNIQUE cùng id gốc | CFG-YR-02 |
| room_id / campus_id / age_group_id | BIGINT | không | Id bản ghi gốc (không FK: gốc có thể bị xóa hẳn) | — |
| các cột còn lại | như bảng gốc | | Giá trị lúc kết thúc năm (mã, tên, loại, sức chứa, người phụ trách, trạng thái, địa chỉ, bữa ăn, sĩ số tối đa…) | — |
Ghi khi năm chuyển CLOSED (kết thúc tay hoặc tự đóng khi kích hoạt năm mới), trong cùng transaction.

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
Ràng buộc: `ck_children_status`, `ck_children_gender`, `ck_children_left`, `ck_children_left_on`; `ck_children_complete` (có giới tính, chờ xếp lớp thì có điểm trường) là **NOT VALID**: áp cho mọi dòng thêm/sửa, không kiểm trẻ có trước V39 (chỉ dữ liệu demo — trẻ đó phải nhập đủ khi sửa hồ sơ). V39 sinh `code`, `status`, `enrolled_on`, `intake_campus_id` cho trẻ có sẵn (plan mục 3.9).
FE đọc `campusId` của trẻ = điểm trường theo lớp nếu có lớp, không thì `intake_campus_id`.
Index: `uk_children_code`, `idx_children_intake_campus_id`, `idx_children_date_of_birth` (kiểm trùng hồ sơ — CHD-04).

## child_code_sequences  (module: child, scope: SCHOOL)
Mục đích: số thứ tự mã trẻ theo năm; giao dịch tiếp nhận tăng bằng upsert nên nhiều người lưu cùng lúc không cấp trùng mã. Migration: V39.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| code_prefix | VARCHAR(10) PK | không | `HS` + 2 số cuối năm, vd. `HS26` | — |
| last_number | INTEGER | không | Số thứ tự đã cấp gần nhất; CHECK > 0 | — |

## class_placements  (module: child, scope: CLASS)
Mục đích: xếp lớp năm học mới — kết quả dự kiến của trẻ đang học ở năm ACTIVE cho năm PLANNED; áp dụng vào `enrollments` khi kích hoạt năm. Migration: `V19__create_class_placements.sql`.
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
| reason | VARCHAR(300) | có | Lý do chuyển lớp (bắt buộc khi chuyển, V39) | — |
| created_by | BIGINT FK user_accounts | có | Người xếp/chuyển lớp (V39); NULL ở dữ liệu cũ | — |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Xếp lớp mở enrollment mới; chuyển lớp đóng enrollment đang mở rồi mở enrollment mới. `uk_enrollments_child_open` (V39, partial unique `valid_to IS NULL`): mỗi trẻ tối đa 1 enrollment đang mở; chồng thời gian giữa các enrollment đã đóng vẫn do service kiểm.
Index: `child_id`, `classroom_id`, `uk_enrollments_child_open`.

## guardian_child_links  (module: child, scope: CHILD)
Mục đích: liên kết phụ huynh–trẻ, nguồn scope phụ huynh; tạo/gỡ qua `/children/{id}/guardians` (AUTH-18, AUTH-19). Migration: V3, V9 (CHECK quan hệ), V39 (6 loại).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| user_id | BIGINT FK user_accounts | không | Tài khoản phụ huynh | AUTH-04 |
| child_id | BIGINT FK children | không | | AUTH-04 |
| relationship | VARCHAR(30) | không | `FATHER` / `MOTHER` / `GRANDFATHER` / `GRANDMOTHER` / `SIBLING` / `GUARDIAN` (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ — như hồ sơ trẻ); `OTHER` chỉ còn ở dữ liệu trước V39 (CHECK `ck_guardian_child_links_relationship`) | AUTH-18 |
| created_at | TIMESTAMPTZ | không | | — |
Index: `uk_guardian_child_links_user_child`, `idx_guardian_child_links_child_id`.

## attendance_records  (module: attendance, scope: CLASS)
Mục đích: điểm danh và tham gia ăn của một trẻ trong một ngày (ATT-01, ATT-02). Migration: `V21__create_attendance.sql`.
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
Mục đích: lịch sử ghi/sửa từng bản ghi điểm danh (chỉ thêm). Migration: V21.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| record_id | BIGINT FK attendance_records (ON DELETE CASCADE) | không | | — |
| action | VARCHAR(20) | không | `RECORDED` / `UPDATED` | — |
| status, meals | như `attendance_records` | không | Giá trị sau lần ghi | — |
| user_id, at | BIGINT FK user_accounts, TIMESTAMPTZ | không | Ai ghi, lúc nào | — |
| unlock_id | BIGINT FK attendance_day_unlocks | có | Ghi trong lần mở lại ngày | ATT-06 |

## attendance_day_unlocks  (module: attendance, scope: CLASS)
Mục đích: mở lại điểm danh một lớp/ngày đã khóa (HT mọi lớp, PHT lớp điểm trường mình). Migration: V21.
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
Mục đích: sức khỏe trong ngày — mỗi lần giáo viên lớp ghi nhận khi tiếp nhận trẻ đã lưu Có mặt (chỉ thêm, không sửa). Giáo viên quan sát, không chẩn đoán. Migration: `V22__create_daily_health_entries.sql`. Dữ liệu sức khỏe — không log.
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

## health_check_campaigns  (module: health, scope: CAMPUS)
Mục đích: đợt khám sức khỏe (`EXAM`, bác sĩ khám, thường 1 lần/năm học) và kỳ cân đo (`MEASUREMENT`, giáo viên cân đo, khoảng 3 tháng/lần) của một điểm trường. Trạng thái (`UPCOMING`/`IN_PROGRESS`/`CLOSED`/`CANCELLED`) tính từ ngày và mốc đóng/hủy, không lưu. Đợt thuộc năm học của ngày bắt đầu (G94-180/HLT-08). Migration: `V40__create_health_checks.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| kind | VARCHAR(20) | không | `EXAM` / `MEASUREMENT`; không đổi sau khi tạo | HLT-01, HLT-08 |
| campus_id | BIGINT FK campuses | không | Điểm trường (scope); không đổi sau khi tạo | AUTH-01, AUTH-02 |
| name | VARCHAR(150) | không | Tên đợt/kỳ | — |
| start_date, end_date | DATE | không | Khoảng ngày nhập kết quả; `end_date >= start_date` | HLT-01 |
| examiner | VARCHAR(150) | không | Đơn vị khám/bác sĩ; kỳ cân đo luôn rỗng (CHECK) | HLT-08 |
| note | VARCHAR(500) | không | Ghi chú | — |
| created_by, created_at, updated_at | BIGINT FK user_accounts, TIMESTAMPTZ | không | | — |
| closed_at, closed_by | TIMESTAMPTZ, BIGINT FK user_accounts | có | Đóng: khóa kết quả, trẻ chưa có kết quả thành vắng; cùng có hoặc cùng trống | — |
| cancelled_at | TIMESTAMPTZ | có | Hủy (chỉ khi chưa có kết quả); không đồng thời đóng | — |
Index: UNIQUE `uk_health_check_campaigns_campus_open` (`campus_id`) WHERE chưa đóng, chưa hủy — mỗi điểm trường một đợt/kỳ chưa đóng, tính chung hai loại; `(campus_id, start_date)`.

## health_check_campaign_history  (module: health, scope: CAMPUS)
Mục đích: lịch sử thao tác của đợt/kỳ, chỉ thêm. Migration: V40.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| campaign_id | BIGINT FK health_check_campaigns | không | | — |
| action | VARCHAR(20) | không | `CREATED` / `UPDATED` / `CLOSED` / `REOPENED` / `CANCELLED` | — |
| user_id | BIGINT FK user_accounts | không | Người thao tác | — |
| reason | VARCHAR(300) | có | Lý do (bắt buộc khi mở lại) | — |
| created_at | TIMESTAMPTZ | không | | — |

## health_check_absences  (module: health, scope: CLASS)
Mục đích: trẻ chưa có kết quả khi đóng đợt/kỳ ("Vắng"), kèm lớp lúc đóng; mở lại thì xóa. Migration: V40.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| campaign_id, child_id | BIGINT FK | không | Khóa chính ghép | — |
| classroom_id | BIGINT FK classrooms | không | Lớp của trẻ lúc đóng | — |

## health_measurements  (module: health, scope: CHILD)
Mục đích: một lần đo của trẻ trong đợt/kỳ (HLT-13 lưu từng lần đo). Kỳ cân đo: số đo GV nhập; đợt khám: phiếu khám Phụ lục II (`exam`, HLT-08). Lưu/sửa là công bố cho phụ huynh (`published_at`, HLT-11); phiếu khám nháp chưa công bố, chưa tính là kết quả. Dữ liệu sức khỏe — không log. Migration: V40, V41.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| child_id | BIGINT FK children | không | | — |
| campaign_id | BIGINT FK health_check_campaigns | không | Đợt/kỳ; UNIQUE `(child_id, campaign_id)` | HLT-01 |
| classroom_id | BIGINT FK classrooms | không | Lớp của trẻ tại ngày đo (scope, báo cáo theo lớp) | HLT-09 |
| date | DATE | không | Ngày đo, trong khoảng của kỳ, không ở tương lai; UNIQUE `(child_id, date)` | HLT-13 |
| height_cm, weight_kg | NUMERIC(4,1) | có (chỉ phiếu khám nháp) | 40–150 cm, 3–60 kg (CHECK) | HLT-01 |
| head_circumference_cm | NUMERIC(3,1) | có | 30–60 cm; chỉ lưu khi trẻ chưa quá 60 tháng tại ngày đo (tới trước ngày đủ 61 tháng) | HLT-01 |
| note | VARCHAR(500) | không | Ghi chú, không ghi chẩn đoán | — |
| measured_by | BIGINT FK user_accounts | không | GV nhập | HLT-09 |
| published_at | TIMESTAMPTZ | có (chỉ phiếu khám nháp) | Lúc công bố (lưu, kết luận, sửa) | HLT-11 |
| published_by | BIGINT FK user_accounts | có | Người công bố lần gần nhất (V41) | HLT-11 |
| exam | JSONB | có | Phiếu khám Phụ lục II đã chuẩn hóa: hành chính, tiền sử, sinh tồn, dinh dưỡng, phát triển, tiêm chủng, lâm sàng, kết luận (`HealthExamForm`); null với số đo của kỳ cân đo (V41) | HLT-08 |
| exam_status | VARCHAR(20) | có | `DRAFT` / `CONCLUDED`; có khi và chỉ khi có `exam` (V41) | HLT-08, HLT-11 |
| concluded_at | TIMESTAMPTZ | có | Lần kết luận đầu tiên; có khi và chỉ khi `CONCLUDED` (V41) | HLT-08 |
| created_at, updated_at | TIMESTAMPTZ | không | | — |
Index: `(campaign_id, classroom_id)`.

## health_measurement_edits  (module: health, scope: CHILD)
Mục đích: lịch sử sửa số đo, chỉ thêm (HLT-13). Migration: V40.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| measurement_id | BIGINT FK health_measurements | không | | — |
| user_id | BIGINT FK user_accounts | không | Người sửa | — |
| reason | VARCHAR(300) | không | Lý do sửa (bắt buộc) | HLT-13 |
| changes | JSONB | không | `[{ field, label, from, to }]` các ô đổi, chuỗi hiển thị (phiếu khám: số định danh che, chỉ 3 số cuối; bản ghi trước V41 không có `label`) | HLT-13 |
| created_at | TIMESTAMPTZ | không | | — |
Index: `(measurement_id, created_at)`.

## health_follow_ups  (module: health, scope: CHILD)
Mục đích: phiếu theo dõi sức khỏe, mở khi kết luận phiếu khám "theo dõi sát" (chuyển khám chuyên khoa, nguy cơ lao...). Trạng thái, vòng trao đổi, hạn tính khi đọc từ `health_follow_up_events`, không lưu. Migration: V41.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| child_id | BIGINT FK children | không | Mỗi trẻ tối đa một phiếu mở (UNIQUE có điều kiện `closed_at IS NULL`) | HLT-16 |
| campus_id | BIGINT FK campuses | không | Điểm trường của trẻ lúc mở (phạm vi BGH khi trẻ không còn lớp) | HLT-16 |
| opened_at, updated_at | TIMESTAMPTZ | không | | — |
| closed_at | TIMESTAMPTZ | có | Lúc đóng | HLT-16 |
| closed_by | BIGINT FK user_accounts | có | Người đóng; null khi hệ thống tự đóng (trẻ ra trường) | HLT-16 |
| close_reason | VARCHAR(300) | có | Lý do đóng; có khi và chỉ khi đã đóng | HLT-16 |
| close_outcome | VARCHAR(30) | có | `DOCTOR_NORMAL` / `KNOWN_CONDITION` / `NO_SPECIALIST_RESULT`; null khi tự đóng | HLT-10, HLT-16 |
| close_condition_text | VARCHAR(100) | có | Tên vấn đề đã biết; có khi và chỉ khi `KNOWN_CONDITION` | HLT-16 |
Index: `uk_health_follow_ups_child_open`, `(child_id, opened_at)`.

## health_follow_up_events  (module: health, scope: CHILD)
Mục đích: dòng thời gian của phiếu theo dõi; thứ tự `id` là thứ tự thời gian. Migration: V41.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| follow_up_id | BIGINT FK health_follow_ups | không | | — |
| action | VARCHAR(30) | không | `OPENED` / `MEASUREMENT_ADDED` / `PARENT_CONTACTED` / `EXTERNAL_RESULT` / `CLOSED` | HLT-16 |
| user_id | BIGINT FK user_accounts | có | Người ghi; null khi hệ thống ghi | — |
| created_at | TIMESTAMPTZ | không | Lúc ghi; vòng trao đổi hạn 7 ngày từ lúc lưu kết quả mở vòng | HLT-16 |
| measurement_id | BIGINT FK health_measurements | có | Lần khám vào phiếu (mỗi lần khám tối đa một phiếu — UNIQUE có điều kiện), hoặc lần khám bác sĩ trao đổi tại buổi khám | HLT-16 |
| event_date | DATE | có | Ngày khám / ngày trao đổi / ngày khám chuyên khoa | HLT-16 |
| new_finding | BOOLEAN | có | Lần khám theo mẫu theo dõi có bất thường mới (mở vòng mới) | HLT-16 |
| at_exam | BOOLEAN | không | Trao đổi do bác sĩ với người đi cùng tại buổi khám | HLT-16 |
| note | VARCHAR(500) | có | Nội dung trao đổi / kết quả chuyên khoa / lý do đóng | HLT-16 |
| facility | VARCHAR(150) | có | Chuyên khoa, nơi khám | HLT-16 |
| next_visit_on | DATE | có | Ngày hẹn khám lại | HLT-16 |
| outcome, condition_text | VARCHAR | có | Kết quả đóng (sự kiện `CLOSED`) | HLT-16 |
| auto_reason | VARCHAR(30) | có | `CHILD_LEFT_SCHOOL` khi hệ thống tự đóng | HLT-16 |
Index: `(follow_up_id, id)`, `uk_health_follow_up_events_measurement`.

## health_follow_up_images  (module: health, scope: CHILD)
Mục đích: ảnh kết quả khám chuyên khoa (tối đa 3 ảnh/lần ghi), tệp private ở S3 theo `storage_key` (prefix `CARENEST_HEALTH_MEDIA_PREFIX`); chỉ đọc qua API có kiểm phạm vi. Migration: V41.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| event_id | BIGINT FK health_follow_up_events | không | Sự kiện `EXTERNAL_RESULT` | HLT-16 |
| position | SMALLINT | không | 0–2; UNIQUE `(event_id, position)` | HLT-16 |
| storage_key | VARCHAR(36) | không | UUID object S3; UNIQUE | — |
| content_type | VARCHAR(30) | không | `image/jpeg` / `image/png` / `image/webp` (kiểm chữ ký tệp) | — |
| size_bytes | INTEGER | không | 1 B – 1 MB | — |
| created_at | TIMESTAMPTZ | không | | — |

## meal_counts  (module: nutrition, feature `meals`, scope: CAMPUS)
Mục đích: sổ báo ăn một bữa của một điểm trường trong ngày, tổng hợp từ điểm danh lúc qua giờ chốt (lần đầu mở sổ sau giờ chốt). Migration: `V26__create_meal_counts.sql`.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| campus_id / school_year_id | BIGINT FK | không | Điểm trường, năm học của sổ | AUTH-02 |
| date, session | DATE, VARCHAR(20) | không | Ngày, bữa (`MORNING`/`LUNCH`/`AFTERNOON`); UNIQUE `(campus_id, date, session)` | — |
| status | VARCHAR(30) | không | `PENDING_CONFIRMATION` / `CONFIRMED` | NUT-02 |
| needs_reconfirmation | BOOLEAN | không | Số đã đổi sau xác nhận, chờ PHT xác nhận lại; bếp vẫn dùng số cũ | NUT-03, ATT-07 |
| aggregated_at | TIMESTAMPTZ | không | Lúc tổng hợp | NUT-01 |
| confirmed_by, confirmed_at | BIGINT FK user_accounts, TIMESTAMPTZ | có | HT hoặc PHT điểm trường xác nhận lần gần nhất | NUT-02 |
| confirmed_by_role | VARCHAR(20) | có | Vai trò người xác nhận: `PRINCIPAL` / `VICE_PRINCIPAL` (V29) | NUT-02 |

## meal_count_lines  (module: nutrition, scope: CLASS)
Mục đích: số suất từng lớp của một bữa. `pending = true` là số mới chờ xác nhận lại; xác nhận ⇒ chép vào dòng `pending = false` rồi xóa dòng chờ. Migration: V26.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| meal_count_id | BIGINT FK meal_counts (ON DELETE CASCADE) | không | | — |
| classroom_id | BIGINT FK classrooms | không | UNIQUE `(meal_count_id, classroom_id, pending)` | — |
| present / absent / missing | INTEGER ≥ 0 | không | Sĩ số theo điểm danh lúc tổng hợp | — |
| normal / substitute | INTEGER ≥ 0 | không | Suất thường / suất thay thế (chưa có dữ liệu dị ứng ⇒ thay thế = 0) | NUT-01 |

## meal_count_history  (module: nutrition, scope: CAMPUS)
Mục đích: lịch sử sổ báo ăn (chỉ thêm). Migration: V26.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| meal_count_id | BIGINT FK meal_counts (ON DELETE CASCADE) | không | | — |
| action | VARCHAR(30) | không | `AGGREGATED` / `CONFIRMED` / `ADJUSTED` / `ATTENDANCE_CHANGED` | NUT-03 |
| classroom_id | BIGINT FK | có | Lớp (điều chỉnh, điểm danh đổi) | — |
| delta_normal, delta_substitute | INTEGER | không | Chênh lệch số suất | — |
| note | VARCHAR(200) | có | Lý do (bắt buộc khi điều chỉnh) | NUT-03 |
| user_id, at | BIGINT FK user_accounts (có thể null: tự động), TIMESTAMPTZ | | | — |
## child_profiles  (module: child, scope: CHILD)
Mục đích: hành chính của trẻ, 1–1 với `children`; phiếu khám sức khỏe đọc từ đây; danh sách trẻ không trả phần này. Migration: V39. Dữ liệu nhạy cảm (CHD-05).
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
Mục đích: phụ huynh trên hồ sơ trẻ, có trước khi có tài khoản (AUTH-20); nguồn scope của phụ huynh vẫn là `guardian_child_links`. Migration: V39.
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
Ràng buộc: UNIQUE (`child_id`, `position`), (`child_id`, `phone`), (`child_id`, `citizen_id`) dạng DEFERRABLE INITIALLY DEFERRED (Sửa hồ sơ đổi thông tin giữa hai phụ huynh trong một lần lưu); UNIQUE (`child_id`, `user_id`). `ck_child_guardians_citizen_id_required` **NOT VALID**: dòng thêm/sửa phải có CCCD; dòng V39 chuyển từ liên kết cũ chưa có CCCD (nhập bổ sung). V39 chuyển mỗi liên kết có sẵn có tên đăng nhập là SĐT thành 1 dòng (tối đa 3 mỗi trẻ).
`accountStatus` trả FE: `NOT_ACTIVATED` khi chưa có `guardian_child_links` với trẻ; có thì `user_accounts.status`.
Index: `idx_child_guardians_phone`, `idx_child_guardians_citizen_id`, `idx_child_guardians_user_id` (kiểm một người một tài khoản trên mọi hồ sơ — AUTH-18).

## child_health_declarations  (module: child, scope: CHILD)
Mục đích: khai báo sức khỏe khi tiếp nhận, 1–1 với trẻ; dị ứng có hiệu lực ngay cho bếp, điểm danh. Migration: V39. Dữ liệu sức khỏe nhạy cảm.
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
Mục đích: dị ứng của khai báo sức khỏe, thứ tự theo `id`; bếp và điểm danh đọc bảng này. Migration: V39.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK child_health_declarations | không | | CHD-06 |
| name | VARCHAR(100) | không | Thực phẩm/chất gây dị ứng | CHD-06 |
Index: `idx_child_allergies_child_id`.

## child_health_declaration_history  (module: child, scope: CHILD)
Mục đích: lịch sử khai báo sức khỏe, chỉ thêm. Migration: V39.
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| child_id | BIGINT FK children | không | | — |
| action | VARCHAR(30) | không | `DECLARED` / `UPDATED` / `UPDATED_FROM_FOLLOW_UP` (đóng phiếu theo dõi "vấn đề đã biết") / `UPDATED_FROM_EXAM` (kết luận phiếu khám, V41) | HLT-06, HLT-08, HLT-16 |
| user_id | BIGINT FK user_accounts | không | Người lưu | — |
| note | VARCHAR(500) | có | Lý do thay đổi | — |
| allergy_change, history_change | JSONB | có | `{ from, to }` khi dị ứng / tiền sử đổi | CHD-06, HLT-06 |
| created_at | TIMESTAMPTZ | không | | — |
Index: `idx_child_health_declaration_history_child_id` (`child_id`, `created_at`).

## child_history  (module: child, scope: CHILD)
Mục đích: lịch sử hồ sơ trẻ, chỉ thêm. Migration: V39.
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
Mục đích: lần gửi SMS tài khoản phụ huynh; FE hiện lần gửi gần nhất của từng phụ huynh (`smsStatus`, `smsAt`). Không lưu nội dung, mật khẩu. Migration: V39.
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

## Kế hoạch giáo dục năm học  (module: education-plan, scope: SCHOOL theo nhóm tuổi)
Migration: `V11__create_edu_year_plans.sql`, `V12__relax_edu_theme_draft_constraints.sql`, `V23__drop_unused_edu_plan_tables.sql`, `V24__add_edu_year_plan_editing_lock.sql`, `V27__create_edu_settings.sql`, `V28__create_edu_class_docs.sql`, `V30__add_edu_year_plan_edit_sessions.sql`, `V31__add_edu_learning_resources.sql`, `V32__add_edu_plan_versions.sql` (ADR-0017, theo CAN_DB trang 1; lệch so với CAN_DB ghi ở cuối mục).

| Bảng | Cột chính | Ràng buộc | Rule ID |
| --- | --- | --- | --- |
| edu_year_plans | code VARCHAR(30), school_year_id, age_group_id, title VARCHAR(150) (rỗng được khi soạn), description VARCHAR(500), status, owner_id/owner_title, sent_by/sent_at, issued_by/issued_at, editing_by/editing_since/editing_at/editing_from, edit_session_id UUID, content_revision BIGINT DEFAULT 0, last_save_request_id UUID, created_request_id UUID, version_no INTEGER DEFAULT 1 (V32), source_plan_id (V32, phiên bản được chép), created_at, updated_at | UNIQUE code; UNIQUE (school_year_id, age_group_id, version_no) và UNIQUE partial (school_year_id, age_group_id) WHERE status <> 'APPROVED' (V32 thay UNIQUE cũ); CHECK version_no ≥ 1, (version_no = 1) ⇔ source_plan_id NULL; UNIQUE (owner_id, created_request_id); status ∈ DRAFT/EDITING/PENDING_VP/REJECTED/APPROVED; phiên đủ trường hoặc rỗng hoàn toàn; EDITING từ DRAFT/REJECTED, PENDING_VP giữ trạng thái khi có phiên; FK user_accounts | EDU-01..05, EDU-10/22/23/25 |
| edu_plan_sub_domains | year_plan_id, domain_id, sort_order, text, lineage_id | V35: lĩnh vực con; FK plan CASCADE; ID/sequence chung ba bảng | EDU-09 |
| edu_plan_expected_results | year_plan_id, domain_id, sort_order, text, lineage_id, sub_domain_id, sub_domain_key | V35: nhóm KQMĐ; sub tùy chọn; FK ghép cùng plan/domain; key = COALESCE(sub_domain_id,0) | EDU-09 |
| edu_plan_goals | year_plan_id, domain_id, sort_order, text, lineage_id, goal_no, sub_domain_id, expected_result_id, sub_domain_key, curriculum_goal_id | V35: MT; cha tùy chọn; goal_no NOT NULL; FK ghép kiểm plan/domain/sub kể cả NULL; FK cha CASCADE, deferred | EDU-09/25/28 |
| edu_goal_contents | goal_id, sort_order, text, lineage_id (V32) | V35: FK edu_plan_goals CASCADE; ID nội dung giữ nguyên | EDU-09 |
| edu_themes | year_plan_id, lineage_id (V32), sort_order, name VARCHAR(150), abbr VARCHAR(20) mặc định rỗng, start_date, end_date, festivals, note | Không ràng buộc viết tắt/ngày ở DB (V12): bản nháp lưu được khi còn soạn dở; service kiểm khi gửi/ban hành | EDU-04, EDU-05 |
| edu_breaks | year_plan_id, lineage_id (V32), sort_order, label VARCHAR(150), start_date, end_date | Không CHECK ngày (V12), như chủ đề | — |
| edu_content_theme_ticks | content_id, theme_id | PK 2 cột; xóa theo nội dung/chủ đề | EDU-09 |
| edu_plan_events | year_plan_id, action, action_label, actor_id, actor_name, actor_role_label, tone, note, occurred_at | tone ∈ '' / ok / warn / err (theo UI) | — |
| edu_domains | name VARCHAR(150), display_label, short_label, sort_order, active, standard | UNIQUE name; seed 5 lĩnh vực chuẩn (standard, không xóa); lĩnh vực trường thêm bỏ đi thì active = false (V27) | EDU-09, EDU-11 |
| edu_class_docs | doc_key (khóa Web: `bp_<lớp>_<kế hoạch năm>`, `tp_<lớp>_<chủ đề>`, `wp_<lớp>_<thứ Hai>`, phiên bản ≥2 `wp_<lớp>_<thứ Hai>_<kế hoạch năm>`), kind (branch/theme/week), classroom_id, year_plan_id, theme_id, week_start, status (DRAFT, PENDING_TL/VP/HT, REJECTED, APPROVED), content (JSONB object: nội dung soạn), approvals (JSONB array), history (JSONB array), source_doc_id (V32, bản chép từ phiên bản cũ, ON DELETE SET NULL), adjustments (V32, JSONB object ngày → "Kế hoạch điều chỉnh và bổ sung", chỉ kind week), created_by, updated_by, created_at, updated_at, version | Một bảng cho 3 loại tài liệu. UNIQUE doc_key; UNIQUE (classroom_id, year_plan_id, kind, theme_id, week_start) — cũng là chỉ mục tra theo lớp; CHECK kind, status, khóa đúng theo loại (week_start là thứ Hai), kiểu JSONB; FK classroom, year_plan (ON DELETE CASCADE), theme, user | EDU-12..18 |
| edu_settings | 1 dòng (id = 1): signers (JSON: year, branch, week → danh sách vai trò ký theo thứ tự), week_rows (JSON: key, label, canMerge, allWeek, extraLabel), updated_by, updated_at | CHECK id = 1; seed mặc định TT → PHT và 8 dòng tuần (V27) | EDU-11 |

Lệch so với CAN_DB (đã chốt 22/10):
- Bỏ 2 cột `updated_at` lặp.
- `users` → `user_accounts`.
- Cột audit dùng `created_at`/`updated_at` như các bảng khác, thay cho `createdDate`/`updatedDate`/`createdBy`/`updatedBy`.
- Thêm `sort_order` cho `edu_breaks`.
- `edu_plan_events` chưa có cột `branch_plan_id`/`theme_plan_id`/`week_plan_id` (thêm khi có các bảng đó).
- V23 (Tiền chốt 22/10; số cũ V10 rồi V20 — đổi khi merge nhánh điểm danh G94-236 vì trùng `V10__add_school_year_lifecycle` (nay V13), +3 khi merge `dev`): bỏ `edu_plan_approvals` (trùng `issued_by`/`issued_at` + sự kiện `ISSUE`), bỏ `edu_approval_steps` (thứ tự người ký khi in làm ở task Cấu hình kế hoạch), bỏ cột `attachment_key` (UI không có đính kèm).

## Học liệu hoạt động / trình chiếu (2026-10-27)
User yêu cầu tất cả tệp dùng S3 private và đồng ý AWS SDK v2. Metadata edu_learning_resources trỏ class_doc_id; lưu activity key/signature, tên/loại/kích thước, uploader, request UUID và storage key opaque. Key/signature cùng null khi gỡ. Không binary trong JSONB; không sửa schema nội dung cũ. Tham chiếu [data-model feature002](../../specs/002-class-plan-teaching-media/data-model.md), migration V28. Không expose storage key qua response.

## Danh mục Chương trình GDMN của Bộ (module: education-plan, scope: SYSTEM, chỉ đọc) — V33, V34

| Bảng | Cột chính | Ghi chú |
| --- | --- | --- |
| edu_curriculum_sources | code (unique), title, document_no, issued_date, issuer, official_url, retrieved_from_url, retrieved_at, scope_note, active | Văn bản nguồn (VBHN-01-2021-GDMN) |
| edu_curriculum_sub_domains | source_id, age_group_id, domain_id, text, sort_order (unique theo nguồn + nhóm tuổi; unique tên theo nguồn + nhóm tuổi + lĩnh vực) | V37: lĩnh vực con của Bộ (17 dòng) |
| edu_curriculum_expected_results | source_id, age_group_id, domain_id, sub_domain_id (FK, tùy chọn), group_no, text, sort_order | V37: nhóm kết quả mong đợi = tiêu đề hàng "n. …" mục D (102 dòng) |
| edu_curriculum_goals | source_id, age_group_id, domain_id, expected_result_id (FK NOT NULL, V37), goal_no, goal_text, section_ref, sort_order (unique theo nguồn + nhóm tuổi) | Mục D "Kết quả mong đợi": câu đánh số = mục tiêu; V37 bỏ sub_domain/group_no/group_text (lấy qua nhóm) |
| edu_curriculum_goal_contents | curriculum_goal_id (CASCADE), text, sort_order | Gạch đầu dòng dưới mục tiêu = nội dung |
| edu_curriculum_contents | source_id, age_group_id, domain_id, sub_domain_id (FK, V37), category_no, category, label, text, section_ref, sort_order | Mục C "Nội dung giáo dục theo độ tuổi" = gợi ý nội dung |
| edu_plan_goals.curriculum_goal_id | FK edu_curriculum_goals ON DELETE SET NULL | Mục tiêu trong kế hoạch lấy từ Bộ; chép theo phiên bản (EDU-25) |
| edu_plan_sub_domains.curriculum_sub_domain_id, edu_plan_expected_results.curriculum_expected_result_id | FK danh mục cùng tầng ON DELETE SET NULL (V37) | Lĩnh vực con/nhóm trong kế hoạch lấy từ Bộ; chép theo phiên bản; dữ liệu cũ gắn khi chữ trùng câu Bộ và có mục tiêu con lấy từ Bộ |

V36 bỏ bảng `edu_expected_results` (danh mục gợi ý chưa từng có dữ liệu, không client nào đọc; gợi ý theo Bộ dùng `edu_curriculum_*`, EDU-28).

V35 thay bảng `edu_goal_rows` bằng ba bảng trên, giữ id/lineage/sort_order và chuyển FK nội dung. Ba bảng dùng `edu_plan_rows_seq`; `kind` chỉ còn ở DTO. Không tự tạo cha khi dữ liệu cũ thiếu cấp. FK cha được deferred để chuyển cha trong cùng transaction; service flush cập nhật trước xóa tiêu đề cũ.

## role_permissions  (module: identity-access, scope: SYSTEM)
Mục đích: ma trận quyền theo role do Hiệu trưởng bật/tắt (ADR-0017); có dòng = role có quyền. Migration: `V7__create_permissions.sql` (seed = quyền mặc định của enum `Permission`).
| Column | Type | Null | Ý nghĩa | Rule ID |
| --- | --- | --- | --- | --- |
| id | BIGINT identity | không | Khóa chính | — |
| role | VARCHAR(20) | không | Chỉ `VICE_PRINCIPAL`/`TEACHER`/`KITCHEN_STAFF`/`TEAM_LEADER` (CHECK, `TEAM_LEADER` từ V38); role khác cố định trong code. `TEAM_LEADER` không phải realm role: áp cho GV có phân công `AGE_GROUP_TEAM_LEADER` còn hiệu lực | AUTH-11, AUTH-23 |
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
