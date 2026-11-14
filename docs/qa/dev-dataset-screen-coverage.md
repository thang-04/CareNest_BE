# Ma trận màn hình ↔ dữ liệu dev (dev dataset coverage)

Mục tiêu: mọi màn của mọi vai trò mở ra **có dữ liệu và thao tác được** trên bộ dữ liệu dev (`scripts/dev/gen-dev-dataset.mjs`).
Nguồn đối chiếu (2026-11-14): FE `origin/dev` (`src/routes/AppRoutes.jsx`, `src/layouts/menuConfig.js`, `src/config/integrationModules.js`, service/hook), APP `origin/dev` (`lib/routing/*`, `lib/features/*`), BE (`docs/api/API_INDEX.md`, `docs/system/FE_API_CALLS.md`, controller → service → repository → `@Table`, `docs/database/DATA_DICTIONARY.md`).

## 0. Quy ước và lưu ý chung

| Ký hiệu | Nghĩa |
| --- | --- |
| HT / PHT / TT / GV / BẾP / PH | Hiệu trưởng / Phó HT (theo điểm trường) / Tổ trưởng (GV có `staff_assignments.staff_role=AGE_GROUP_TEAM_LEADER`) / Giáo viên / Nhân viên bếp / Phụ huynh |
| BGH | HT + PHT |
| `T` | Ngày chạy (hôm nay theo `carenest.time-zone`). **2026-11-14 là thứ Bảy** ⇒ các màn "hôm nay" báo "không phải ngày học" |
| `N` | Ngày học gần nhất ≤ T (không T7/CN, không `school_holidays`) |
| `W` | Tuần mặc định = `schoolWeekStart(T)` (thứ Hai; T7/CN ⇒ **tuần sau**) — FE `utils/week.js` |
| [NỀN] | Bảng phạm vi mọi màn đều đọc: `school_years` (1 ACTIVE), `campuses`, `age_groups`, `classrooms`, `rooms`, `staff_assignments`, `enrollments`, `children`, `child_guardians`, `user_accounts`, `permission_grants` |
| (demo) | Module FE còn `source: 'demo'` trong `integrationModules.js` ⇒ dữ liệu mẫu trong trình duyệt + nhãn "Dữ liệu demo", **không đọc DB** |

Lưu ý quan trọng:

1. **Ngày tương đối**: generator phải sinh dữ liệu theo `T` lúc chạy (không cố định ngày), và cần dữ liệu cho cả `T` lẫn `N` + `W`. Demo nên chạy vào ngày học; nếu T rơi vào cuối tuần, các màn mặc định T trống hợp lệ, còn màn tuần nhảy sang tuần sau ⇒ tuần sau cũng phải có dữ liệu.
2. **Module demo trên Web (không cần DB)**: đón trẻ `/pickup*`, bàn giao bữa `/attendance/meal-handover`, thực đơn mẫu cũ / dị ứng / AI gợi ý thực đơn (`/menu/menus|allergy-menus|ai-suggestion|plans|flow`), bếp chung (`/kitchen` index, `/kitchen/required-quantity`, `/kitchen/missing-food`, `KitchenFlowPage`), giao diện `/settings/appearance`, phần sửa hồ sơ/đổi vai trò nhân sự (`staff-accounts`). Thẻ "Bữa trưa hôm nay" trên trang chủ dùng `kitchenService.getPublishedMenu` (module `kitchen` demo).
3. **Chế độ API làm rỗng nhóm duyệt KH giáo dục trên trang chủ/Chờ duyệt**: `getEducationLessons()` trả `[]` khi không mock ⇒ nhóm LESSON_REVIEW / THEME_PLAN / LESSON_PLAN luôn rỗng; hàng chờ thật nằm ở `/education/plan-reviews`.
4. **Vai trò không có màn Web**: ADMIN và PARENT đăng nhập Web được nhưng gặp màn 403 "Tài khoản chưa có màn hình phù hợp" (`ProtectedRoute`). BẾP trên Web chỉ có Trang chủ (KitchenFlowPage – demo), Tài khoản, Thông báo (AUTH-21).
5. **App Flutter `origin/dev` chưa gọi API**: mọi màn dùng `fake_*_source.dart`, `pubspec` không có HTTP client; màn theo vai trò chỉ mở qua `/preview` (debug). Mục 2 ghi endpoint/bảng **sẽ** cấp dữ liệu khi nối, để dataset sẵn sàng.
6. **Màn gắn mốc năm học** (đánh giá học kỳ, cuối năm, xếp loại nhóm lớp, khen thưởng) chỉ mở sau kỳ ⇒ ở tháng 11 năm ACTIVE rỗng **hợp lệ**. Muốn có dữ liệu: sinh thêm năm **2025-2026 CLOSED** (chỉ đọc, chọn qua bộ chọn năm học) có đủ các bảng này.
7. Bảng quyền: không xóa dòng role seed của migration (`V7/V8/V9/V38/V114` — TEACHER, TEAM_LEADER, VICE_PRINCIPAL, KITCHEN_STAFF). Menu và route `permit()` ẩn theo `bePermissions` từ `GET /accounts/me`.

## 1. Web — route → API → bảng → điều kiện

### 1.1 Chung, tài khoản, trang chủ

| Route | Màn hình | Vai trò | API chính | Bảng DB | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- | --- |
| `/login` | Đăng nhập | mọi TK nhân sự | `POST /auth/login`, `GET /auth/csrf`, `GET /accounts/me` | `user_accounts`, [NỀN], Keycloak | TK có trong realm + `user_accounts.status=ACTIVE`; 1 TK INACTIVE (bị chặn), 1 TK phải đổi MK lần đầu | đăng nhập |
| `/forgot-password` → `/verify-otp` → `/reset-password` | Quên MK / OTP / Đặt MK | khách | `POST /auth/password/forgot`, `GET/POST /auth/password/otp/{id}(/verify,/resend)`, `POST /auth/password/reset` | `password_tokens`, `otp_send_limits`, `user_accounts` | TK có email; cần dịch vụ gửi email OTP (bản demo hiện mã nếu `IS_OTP_DEMO`) | gửi OTP, đặt MK |
| `/` (HT) | Trang chủ HT | HT | `GET /assessment/reward-lists`, `GET /meal-counts?date=T`, `GET /attendance/summary` (T và T-7), `GET /daily-health/overview?date=T`, `GET /school-years/{id}/cutoff`, `GET /assessment/overview?period=DAY&date=T` | `reward_lists`, `meal_counts`(+`meal_count_lines`), `attendance_records`, `daily_health_entries`, `attendance_cutoffs`, `daily_observations` | T là ngày học; điểm danh T đã ghi một phần lớp (thấy lớp chưa điểm danh); T-7 có điểm danh để so sánh; có trẻ `NOTED`; sau giờ chốt có `meal_counts` PENDING_CONFIRMATION; đánh giá ngày một phần lớp | mở nhanh tới màn nguồn |
| `/` (PHT) | Trang chủ PHT | PHT | như HT (phạm vi điểm trường) + `GET /children`, `GET /children/parent-accounts`; báo thiếu thực phẩm (demo) | + `children`, `child_guardians` | như HT + trẻ trong điểm trường có PH chưa kích hoạt TK | — |
| `/` (TT) | Trang chủ TT | TT | pending LESSON_REVIEW (rỗng ở API) + widget lớp | — | xem lưu ý 3 | — |
| `/` (GV, cũng phần lớp của TT) | Trang chủ GV / "Lớp hôm nay" | GV, TT | `GET /attendance/classes`, `GET /classes/{id}/attendance?date=T`, `GET /classes/{id}/daily-assessment`, `GET /assessment/evaluations`; đón trẻ (demo) | `attendance_records`, `leave_requests`, `daily_observations`, `child_evaluations` | GV có assignment TEACHER còn hiệu lực ở lớp ACTIVE năm ACTIVE; T có đơn nghỉ, trẻ chưa điểm danh/đánh giá | lối tắt |
| `/` (BẾP) | KitchenFlowPage (demo) | BẾP | — | — | không cần DB | — |
| `/approvals` | Chờ duyệt | HT, PHT, TT | HT: reward PENDING_PRINCIPAL + `meal_counts` chờ xác nhận; PHT: reward PENDING_VP + `meal_counts`; TT: rỗng (API) | `reward_lists`, `meal_counts` | T sau giờ chốt có sổ PENDING_CONFIRMATION của điểm trường; reward chỉ có cuối năm (lưu ý 6) | mở màn xử lý |
| `/notifications`, `/notifications/:id` | Thông báo | mọi vai trò Web | `GET /me/notifications`, `POST /me/notifications/{id}/read`, `POST /me/notifications/read-all` (`GET /notifications/{x}` BE chưa có) | `notifications` | mỗi TK ≥5 dòng, có `read_at IS NULL` (badge chuông) và đã đọc; `type` đúng vai trò (mục 3) | đánh dấu đã đọc |
| `/account/profile` | Hồ sơ cá nhân | mọi vai trò | `GET /accounts/me`, `GET/PUT /accounts/me/photo(/url)` | `user_accounts` (photo_* → S3) | ảnh tùy chọn | sửa hồ sơ, ảnh |
| `/account/password` | Đổi MK | mọi vai trò | `PUT /me/password` | Keycloak | — | đổi MK |
| `/account/signatures` | Chữ ký của tôi | mọi vai trò (HT/PHT dùng ký biên bản) | `GET/POST /signatures/me`, `PATCH /{id}/default`, `DELETE`, `GET /{id}/content|url` | `user_signatures` (+S3) | HT, mỗi PHT có ≥1 chữ ký `default` (ảnh trên S3) | tải, đặt mặc định, xóa |
| `/settings/appearance` | Giao diện | mọi vai trò | (demo) | — | — | — |

### 1.2 Cấu hình trường, phân quyền

| Route | Màn hình | Vai trò | API chính | Bảng DB | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- | --- |
| `/school/years` | Năm học | HT; PHT xem | `GET /school-years` | `school_years`, `school_holidays` | 2025-2026 CLOSED, 2026-2027 ACTIVE, 2027-2028 PLANNED | tạo, đóng, xóa PLANNED |
| `/school/years/new`, `/:id/setup(/:step)`, `/:id/edit` | Thiết lập / sửa năm học | HT | `GET /school-years/{id}`, `/readiness`, `/holidays`, `/semester-locks`, `/cutoff`; PUT tương ứng | `school_years` (ngày HK1/HK2), `school_holidays`, `school_year_rooms|campuses|age_groups`, `classrooms` | năm PLANNED đang dựng dở (readiness còn mục thiếu); năm ACTIVE đã đặt ngày học kỳ, ngày nghỉ | lưu bước, sao chép lớp, kích hoạt |
| `/school/classes` | Nhóm tuổi & lớp | HT, PHT, GV, TT | `GET /school-structure`, `/age-groups`, `/team-leader-assignments`, `/age-groups/{id}/team-leader-assignments/history` | `age_groups`, `classrooms`, `rooms`, `staff_assignments` | mỗi nhóm tuổi có lớp ACTIVE + phòng + 2 GV; tổ trưởng có lịch sử đổi; lớp năm cũ CLOSED | thêm/xóa nhóm tuổi, lớp; giao tổ trưởng |
| `/school/rooms` | Phòng ban | HT, PHT (mảng rooms) | `GET /locations` | `rooms` | đủ `room_type` CLASS/KITCHEN/FUNCTION_ROOM/DEPARTMENT/STORAGE; 1 INACTIVE | thêm, sửa, ngừng, xóa |
| `/school/staff` | Tài khoản nhân sự / Nhân sự điểm trường | HT; PHT xem | `GET /accounts`, `/accounts/{id}` | `user_accounts`, `staff_assignments` | TK ACTIVE/INACTIVE, GV chưa phân lớp, GV nghỉ việc (assignment hết hạn), TK bếp mỗi điểm trường | tạo, khóa/mở, MK tạm (sửa hồ sơ/đổi vai trò còn demo) |
| `/school/placement` | Xếp lớp năm học mới | HT, PHT (`YEAR_PLACEMENT_MANAGE`) | `GET /school-years/{planned}/placements` | `class_placements`, `classrooms` (năm PLANNED), `enrollments` | năm PLANNED có lớp; một phần trẻ đã xếp (PROMOTE/STAY/LEAVE/GRADUATE), phần còn lại chưa | tự xếp, lưu |
| `/school/cutoff` | Cấu hình thời gian | HT | `GET /school-years/{id}/cutoff` | `attendance_cutoffs` | ≥2 bản (cũ + hiện hành) | lưu |
| `/school/campuses`, `/:id`, `/new`, `/:id/edit` | Điểm trường | HT (PHT xem) | `GET /campuses`, `/campuses/{id}` | `campuses`, `rooms`, `classrooms`, `staff_assignments` | 2 ACTIVE (1 `is_main`); tùy chọn 1 INACTIVE | thêm, sửa, ngừng |
| `/school/vice-principals` | Phân công PHT | HT | `GET /school-years/{id}/vice-principal-assignments` | `staff_assignments` (VICE_PRINCIPAL), `permission_grants` | mỗi điểm trường 1 PHT | lưu |
| `/school/teachers` | Phân công GV / tổ trưởng | HT, PHT có `STAFF_ASSIGNMENT_MANAGE` | `GET /teacher-assignments`, `/team-leader-assignments`, `/classes/{id}/teacher-assignments/history` | `staff_assignments` | mỗi lớp 2 GV (1 `is_homeroom`); 1 GV dạy thay (`is_substitute`, `valid_to`); 1 GV đổi lớp giữa năm | lưu |
| `/school/roles` (= `/permissions`, `/permissions/roles`) | Vai trò & quyền | HT (`PERMISSION_MANAGE`) | `GET /permissions`, `/role-permissions` | `permission_grants` (dòng role) | dòng seed còn nguyên | bật/tắt ô |
| `/permissions/overrides` | Ngoại lệ quyền | HT | `GET /accounts`, `/accounts/{id}/permission-overrides`, `/campuses/options` | `permission_grants` (dòng user) | ≥1 GRANT (đã có: GV Lá 4 + `CHILD_PROFILE_EDIT`), ≥1 REVOKE (GV Chồi 2 − `ASSESSMENT_CONFIRM`) | sửa ngoại lệ |
| `/permissions/history` | Lịch sử phân quyền | HT | `GET /permission-change-logs` | `permission_change_log` | vài dòng ON/OFF và GRANT/REVOKE/INHERIT ứng với ngoại lệ trên | xem |

### 1.3 Quản lý trẻ, sức khỏe

| Route | Màn hình | Vai trò | API chính | Bảng DB | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- | --- |
| `/children` | Danh sách trẻ | HT, PHT, GV, TT | `GET /children`, `/school-structure` | `children`, `enrollments`, `classrooms` | trẻ ACTIVE mọi lớp; ≥5 PENDING_PLACEMENT; ≥3 LEFT | lọc |
| `/children/:id` | Hồ sơ trẻ | HT, PHT, GV, TT | `GET /children/{id}`, `/profile`, `/guardians`, `/photo/url`, `/guardians/{uid}/photo/url` | `children`, `child_profiles`, `child_guardians`, `child_health_declarations`, `child_allergies`, `child_history`, `child_placements` | 1–3 PH/trẻ; dị ứng REPORTED; lịch sử sửa (`child_history.changes`); ảnh trẻ/PH (S3, tùy chọn) | — |
| `/children/:id/edit` | Sửa hồ sơ | HT, PHT (`CHILD_PROFILE_EDIT`, GV được cấp) | `GET /children/{id}`, `/parent-accounts/by-citizen-id`; `PUT /children/{id}`, `POST /withdraw|readmit` | như trên | — | lưu, thôi học, nhập lại |
| `/children/new` | Tiếp nhận trẻ | HT, PHT (`CHILD_ENROLL`) | `GET /children/duplicate-check`, `/placement-classes`, `/parent-accounts/by-citizen-id`; `POST /children/guardian-check`, `/children/enrollments` | `children`, `child_*`, `child_code_sequences`, `user_accounts` | có trẻ LEFT (test trùng/nhập lại); PH có TK + CCCD đang có con học (gắn con thứ 2); lớp còn chỗ | tiếp nhận |
| `/children/import` | Nhập Excel | HT, PHT | `POST /children/enrollments/import/preview`, `/import` | như trên | cần file xlsx mẫu (upload) | nhập |
| `/children/placement` | Xếp lớp | HT, PHT (`CHILD_PLACEMENT`) | `GET /children` (PENDING_PLACEMENT), `/children/placement-classes`; `POST /children/{id}/placement` | `children`, `classrooms.capacity`, `enrollments` | ≥5 trẻ chờ xếp ở mỗi điểm trường; lớp chưa đầy | xếp lớp |
| `/children/new-year-class` | Lớp năm học mới | HT, PHT, GV, TT | `GET /school-years/{planned}/prep-classes` | `class_placements`, `classrooms`, `staff_assignments` (năm PLANNED) | năm PLANNED có lớp, GV phân sẵn, trẻ đã xếp | xem |
| `/children/activation` | Tài khoản phụ huynh | HT, PHT (`PARENT_ACCOUNT_MANAGE`) | `GET /children/guardian-overview`, `/children/{id}/guardians(/pending)`, `/account-recipient`; `POST /guardians/activate`, `PATCH /parent-accounts/{id}/status`, `POST /parent-accounts/{id}/temporary-password` | `child_guardians`, `user_accounts` (PARENT), `parent_sms_logs` | PH chưa kích hoạt (`user_id NULL`), ACTIVE, INACTIVE (khóa tay), `locked_by_system`; PH nhiều con | kích hoạt (SMS), khóa/mở, MK tạm |
| `/children/:id/health-declaration` | Khai báo sức khỏe | HT, PHT, GV, TT | `PUT /children/{id}/health-declaration` | `child_health_declarations`, `child_allergies`, `child_health_declaration_history` | đủ 3 state REPORTED/NONE_REPORTED/NOT_PROVIDED; 1 trẻ `tb_contact_state=REPORTED` | lưu |
| `/children/:id/health` | Sổ sức khỏe trẻ | HT, PHT, GV, TT | `GET /children/{id}/health`, `/health/trend-analysis`; `POST .../approve` | `health_measurements`, `health_measurement_edits`, `health_interpretations` | ≥3 lần đo đã công bố (`published_at`) ở các kỳ khác nhau; 1 diễn giải DRAFT + 1 APPROVED | đo mới, phân tích AI, duyệt |
| `/children/:id/health/new`, `/:mid/edit` | Nhập số đo | GV, TT | `GET /health-checks/{id}/classes/{cid}/children`; `POST/PUT /children/{id}/health/measurements` | như trên | kỳ MEASUREMENT IN_PROGRESS chứa lớp | lưu |
| `/children/health-checks` | Đợt khám, kỳ cân đo | HT, PHT, GV, TT | `GET /health-checks` | `health_check_campaigns` | mỗi điểm trường đủ: UPCOMING (bắt đầu > T), IN_PROGRESS (chứa T), CLOSED, CANCELLED; cả EXAM và MEASUREMENT | — |
| `/children/health-checks/new`, `/:id/edit` | Tạo / sửa đợt | HT, PHT | `GET /health-checks/schedule-warnings`; `POST/PUT /health-checks` | như trên | — | tạo, sửa |
| `/children/health-checks/:campaignId` | Chi tiết đợt | HT, PHT, GV, TT | `GET /health-checks/{id}`, `/classes/{cid}/children` | + `health_measurements`, `health_check_absences`, `health_check_campaign_history` | đợt IN_PROGRESS có lớp làm xong một phần; đợt CLOSED có trẻ vắng | đóng, mở lại, hủy |
| `/children/health-checks/:id/exam/:childId` | Phiếu khám | HT, PHT, GV, TT | `GET/PUT .../exam`, `POST .../exam/review-preview` | `health_measurements.exam`, `exam_status` | phiếu DRAFT và CONCLUDED | lưu, kết luận |
| `/children/health-trends` | Xu hướng sức khỏe | HT, PHT, GV, TT | `GET /children`, `/children/{id}/health` | như sổ sức khỏe | như sổ sức khỏe, nhiều trẻ | phân tích AI |
| `/children/growth-report` | Tổng hợp cân đo | HT, PHT | `GET /health-checks/growth-report`, `/health-checks` | `health_measurements`, `health_check_campaigns` | ≥1 kỳ MEASUREMENT CLOSED có số đo mọi lớp, có trẻ SDD/thấp còi/thừa cân | in |

### 1.4 Điểm danh, suất ăn, đón trẻ

| Route | Màn hình | Vai trò | API chính | Bảng DB | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- | --- |
| `/attendance` | Điểm danh & báo ăn + sức khỏe đầu ngày | GV, TT (`ATTENDANCE_RECORD` / `DAILY_HEALTH_RECORD`) | `GET /attendance/classes`, `/attendance/day-info`, `/classes/{id}/attendance?date`, `/classes/{id}/children/{cid}/daily-health`; PUT điểm danh; `POST /daily-health-entries`; `GET /meal-counts/class` (BE dev chưa merge) | `attendance_records`(+`_history`), `leave_requests`, `daily_health_entries`, `attendance_cutoffs`, `school_holidays`, `attendance_day_unlocks`, `child_allergies` | T là ngày học; lớp có trẻ có đơn nghỉ hôm nay (tự EXCUSED); sửa được trước giờ chốt; ngày cũ chỉ sửa khi có unlock còn hạn; trẻ dị ứng (suất thay thế) | lưu điểm danh, ghi sức khỏe |
| `/attendance/summary` | Tổng hợp điểm danh | HT, PHT, GV, TT (`ATTENDANCE_VIEW`) | `GET /attendance/summary?from=T-13&to=T`, `/classes/{id}/attendance/summary` | `attendance_records` | 14 ngày gần nhất có đủ mọi lớp, trộn PRESENT/EXCUSED/UNEXCUSED | lọc, in |
| `/attendance/day-unlocks` | Mở lại điểm danh | HT, PHT (`ATTENDANCE_UNLOCK`) | `GET/POST /attendance/day-unlocks`, `POST /{id}/revoke` | `attendance_day_unlocks` | 1 còn hiệu lực (`valid_until` > now), 1 đã thu hồi, 1 hết hạn | mở, thu hồi |
| `/attendance/meal-count` | Sổ báo ăn (xác nhận suất) | HT, PHT (`MEAL_COUNT_CONFIRM`) | `GET /meal-counts?date` (mặc định T), `POST /meal-counts/{id}/confirm`, `POST /meal-counts/adjustments` | `meal_counts`, `meal_count_lines`, `meal_count_history` | ngày đã qua: CONFIRMED; T sau giờ chốt: PENDING_CONFIRMATION (BE tự lập khi mở hoặc cron 23:50); 1 sổ `needs_reconfirmation=true` | xác nhận, điều chỉnh |
| `/attendance/health` | Sức khỏe trong ngày | HT, PHT | `GET /daily-health/overview?date`, `/children/{id}/daily-health` | `daily_health_entries` | N: đa số bình thường, vài `NOTED` (symptoms, nhiệt độ MILD/HIGH) rải nhiều lớp | xem |
| `/attendance/leave-requests` | Đơn nghỉ của PH | HT, PHT, GV, TT | `GET /leave-requests?from&to` (mặc định tháng chứa T) | `leave_requests` | trong tháng: đơn đã qua, đơn hôm nay, đơn tương lai, đơn nhiều ngày, đơn đã hủy (`cancelled_from`); `created_by` là TK PH | xem (không duyệt — ATT-05) |
| `/pickup`, `/pickup/:childId/verify`, `/pickup/result` | Đón trẻ | GV, TT (`PICKUP_RECORD`) | (demo); BE thật: `GET/POST /pickups`, `GET /pickups/children/{id}` | (`pickup_records`) | Web không cần DB; App sẽ cần (mục 2) | — |

### 1.5 Kế hoạch giáo dục

| Route | Màn hình | Vai trò | API chính | Bảng DB | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- | --- |
| `/education` | Chuyển hướng theo vai trò | HT, PHT, TT, GV | `GET /education-plans/config`, `/goals`, `/class-docs` | — | — | — |
| `/education/school` | Kế hoạch toàn trường | HT | `GET /education-plans/goals` (SCHOOL_YEAR + TEAM), `/class-docs` | `edu_year_plans`, `edu_team_plans`, `edu_class_docs` | mỗi nhóm tuổi có KH năm APPROVED + KH tổ | xem |
| `/education/school-year-plans` (`/new`, `/:id`, `/:id/edit`) | Kế hoạch năm học | HT xem; PHT, TT, GV | `GET/POST/PUT /education-plans/goals?planLevel=SCHOOL_YEAR`, `/{id}/changes`, `submit|return|issue|unlock|versions|start-editing|editing-heartbeat|finish-editing`, `POST /goals/file-preview` | `edu_year_plans`, `edu_plan_sub_domains|expected_results|goals`, `edu_goal_contents`, `edu_themes`, `edu_breaks`, `edu_content_theme_ticks`, `edu_plan_events`, `edu_document_authors`, `edu_curriculum_*` | mỗi nhóm tuổi 1 bản APPROVED v1 có chủ đề phủ cả năm (có chủ đề đã qua, đang chạy chứa T, sắp tới); 1 nhóm có v2 DRAFT; 1 bản REVIEW; 1 bản bị trả (DRAFT + event `return`) | soạn, gửi, trả, ban hành, mở khóa, tạo phiên bản |
| `/education/goals` (`/:id`, `/:id/edit`) | KH giáo dục tổ | HT xem; PHT, TT, GV | `GET /goals?planLevel=TEAM`, `/goals/review-queue`, `POST /goals/{id}/review/{item}/resolve` | `edu_team_plans`, `edu_team_review_items` + bảng con như trên | mỗi nhóm tuổi 1 bản tổ APPROVED nối nguồn KH năm; 1 bản REVIEW chờ PHT; 1 bản có review item chưa resolve | như trên |
| `/education/branches/:classId` | Dự kiến chủ đề – chủ đề nhánh | HT xem; PHT, TT, GV | `GET /class-docs`, `PUT /class-docs/branch/{key}`, `POST .../actions`, `POST /class-docs/branch/calendar-preview|file-preview`; AI tên nhánh (BE chưa có) | `edu_class_docs` (kind=branch), `edu_plan_branch_templates` | mỗi lớp có `bp_` APPROVED; vài lớp DRAFT / REVIEW | lưu, gửi, duyệt, trả |
| `/education/theme-plans/:classId` | KH giáo dục chủ đề | như trên | `GET /class-docs` | `edu_class_docs` (theme, week, dossier, semester) | chủ đề đang chạy có `tp_` APPROVED; tuần trước `wp_` APPROVED; tuần này REVIEW; tuần sau DRAFT | — |
| `/education/class/:classId/themes/:themeId` (`/teach`) | KH chủ đề lớp / Trình chiếu | như trên | `GET /class-docs`, `/class-docs/releases/{id}`, `GET /class-docs/day/{key}/learning-resources(/{id}/url)` | `edu_class_docs`, `edu_class_doc_releases`, `edu_resource_attachments` | hồ sơ chủ đề đã ban hành có ≥1 release (`print_snapshot`); học liệu gắn giáo án | soạn, gửi, ký, trình chiếu |
| `/education/class/:classId/weeks/:weekStart` (`/review`) | KH tuần | như trên | `PUT /class-docs/week/{key}`, `POST .../actions` | `edu_class_docs` (kind=week) | `wp_` tuần W và W+1 | lưu, gửi, duyệt |
| `/education/day-plans/:classId` | KH giáo dục ngày (giáo án) | như trên | `GET/PUT /class-docs` (kind=day), learning-resources upload/links/attach/order | `edu_class_docs` (kind=day), `edu_resource_*` | giáo án N và cả tuần APPROVED (bắt buộc cho đánh giá theo MT, lịch hoạt động PH); vài giáo án REVIEW chờ TT | soạn, gửi, gắn học liệu |
| `/education/resources` | Kho học liệu | TT, GV | `GET /education-plans/resources`, `/{id}/usages`, `/{id}/url`; PATCH, DELETE | `edu_resource_items`, `edu_resource_item_tags`, `edu_resource_attachments` | mỗi GV ≥3 mục: LINK (không cần S3) và FILE (`storage_key` S3 `hoclieu/`) | sửa, xóa |
| `/education/plan-reviews` | Duyệt KH giáo dục | HT xem; PHT, TT | `GET /goals/review-queue` + `GET /class-docs` (status REVIEW, `waitingRole`) | `edu_year_plans`/`edu_team_plans` status=REVIEW (người duyệt bước hiện tại), `edu_class_docs` REVIEW (+approvals theo `edu_settings.workflow_policy`) | TT mỗi nhóm tuổi: ≥2 tài liệu lớp REVIEW đang chờ TEAM_LEADER; PHT: ≥2 chờ VICE_PRINCIPAL + 1 KH tổ REVIEW | duyệt, trả lại |
| `/education/overdue` | Kế hoạch quá hạn | HT | `GET /goals`, `/class-docs`, `/duration-policy` | như trên + `edu_settings` | ≥1 tài liệu quá hạn (vd. `wp_` tuần này còn DRAFT sau hạn) | mở khóa / gia hạn |
| `/education/settings` | Cấu hình kế hoạch | HT | `GET/PUT /education-plans/config`, `/workflow-policy`, `/duration-policy` | `edu_settings` (id=1), `edu_domains` | dòng seed migration | lưu |
| `/education/curriculum-sources` | Nguồn chương trình của Bộ | HT, PHT, TT, GV | `GET /education-plans/curriculum/sources|goals|contents` | `edu_curriculum_*` | seed migration V33/V34 | xem |
| `/education/class*`, `/goals/new`, `.../days`, `.../semesters/:no`, `.../review` | Đường dẫn cũ | — | chuyển hướng | — | — | — |

### 1.6 Đánh giá trẻ (route `permit()` theo mã quyền BE)

| Route | Màn hình | Vai trò | API chính | Bảng DB | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- | --- |
| `/assessment/daily` | Đánh giá hằng ngày | GV, TT (xem `ASSESSMENT_VIEW`, ghi `ASSESSMENT_RECORD`) | `GET /attendance/classes`, `/classes/{id}/daily-assessment?date`, `PUT .../daily-assessment/general` | `daily_observations`, `class_daily_comments`, `attendance_records`, `daily_health_entries` | T ngày học; chỉ trẻ PRESENT chấm được; ngày cũ khóa trừ khi có `assessment_day_unlocks` | lưu, nhận xét lớp |
| `/assessment/daily/skills` | Đánh giá theo mục tiêu | GV, TT (`ASSESSMENT_RECORD`) | `PUT .../daily-assessment/skills` | `daily_activity_results`, `edu_class_docs` (day APPROVED) | giáo án ngày T APPROVED có hoạt động gắn MT; không có ⇒ khóa | lưu |
| `/assessment/day-unlocks` | Mở lại ngày đánh giá | HT, PHT (`ASSESSMENT_UNLOCK`) | `GET/POST /assessment/day-unlocks`, `/{id}/revoke` | `assessment_day_unlocks` | 1 còn hiệu lực, 1 đã thu hồi | mở, thu hồi |
| `/assessment/overview` | Tổng quan đánh giá lớp | HT, PHT, TT | `GET /assessment/overview?period=DAY|WEEK|MONTH&date=T`, `/overview/classes/{id}` | `daily_observations`, `daily_activity_results`, `class_daily_comments`, `pickup_records` | T (và tuần/tháng) đánh giá một phần lớp ⇒ tỷ lệ hoàn thành khác nhau | xem chi tiết lớp |
| `/assessment/quality-report` | Chất lượng tháng | mọi vai trò có VIEW | `GET /assessment/quality-report`, `/program-completion`, `/class-monthly-comments` | `attendance_records`, `behaviour_tickets`, `daily_health_entries`, `class_monthly_comments`, `child_evaluations` | tháng 10/2026 trọn tháng + tháng 11 đến T; 1 lớp có nhận xét tháng CONFIRMED, 1 lớp AWAITING_REVIEW | xác nhận nhận xét tháng |
| `/assessment/class-ratings` | Xếp loại nhóm lớp | HT sửa; PHT (lớp điểm trường); GV xem | `GET/PUT /assessment/class-ratings?period` | `class_ratings` | chỉ mở sau kỳ ⇒ năm ACTIVE rỗng; năm CLOSED có SEMESTER_1/2/YEAR | lưu |
| `/assessment/themes` | Đánh giá cuối chủ đề | mọi vai trò có VIEW | `GET /assessment/theme-evaluations/classes|themes`, `GET/PUT`, `POST /confirm` | `theme_evaluations` + `edu_themes` (KH năm APPROVED) | chủ đề đã kết thúc (end < T): 1 confirmed, 1 nháp/chưa mở; chủ đề đang chạy | lưu, xác nhận |
| `/assessment/children` (`/:childId`, `/progress`) | Hồ sơ / Tiến độ phát triển | mọi vai trò có VIEW | `GET /assessment/children`, `/children/{id}/development`, `/assessment/evaluations?periodType=MONTH`, `/evaluations/{id}`; confirm, content, redraft | `child_evaluations`, `child_evaluation_events`, `daily_observations`, `health_measurements`, `theme_evaluations` | tháng 9, 10/2026 đã kết thúc ⇒ BE tự tạo nháp AWAITING_REVIEW khi mở; dataset nên có sẵn một phần CONFIRMED | xác nhận, sửa, nháp lại (AI) |
| `/assessment/semester` (`/:id`, `/:id/ai-draft`) | Đánh giá học kỳ | mọi vai trò có VIEW (AI draft: RECORD) | `GET/PUT /assessment/semester-evaluations`, `/confirm`; evaluations SEMESTER | `semester_assessments`, `child_evaluations` | chỉ khi HK1 đã kết thúc ⇒ năm ACTIVE rỗng tháng 11; dùng năm CLOSED | lưu, xác nhận |
| `/assessment/year-end` (`/:id`, `/:id/ai-draft`) | Đánh giá cuối năm | như trên | evaluations YEAR, `GET/PUT /evaluations/{id}/year-sheet`, `program-completion` | `child_evaluations` (goal_results, note_for_*, domain_comments) | mở từ ngày 1 tháng cuối năm học ⇒ chỉ năm CLOSED | lưu, xác nhận |
| `/assessment/tickets` | Phiếu bé ngoan | mọi vai trò có VIEW | `GET /assessment/behaviour-tickets/board|overview|summary`, `POST` | `behaviour_tickets` | tuần trước và tháng trước đã phát (WEEKLY, MONTHLY) cho một số trẻ; tuần này chưa phát | phát phiếu |
| `/assessment/rewards` (`/:classId`) | Đề xuất khen thưởng | mọi vai trò có VIEW (PHT xét, HT quyết) | `GET /assessment/reward-lists`, `/classes/{id}`; PUT; `POST /{id}/review|decision` | `reward_lists`, `reward_list_items`, `reward_list_events`, `child_evaluations` (YEAR confirmed) | gate `yearEndOpensOn` ⇒ năm ACTIVE trống; năm CLOSED có đủ DRAFT/PENDING_VP/PENDING_PRINCIPAL/APPROVED/RETURNED | lưu, gửi, xét, duyệt, trả |
| `/assessment/periodic*` | Đường dẫn cũ | — | chuyển hướng `/assessment/children` | — | — | — |

### 1.7 Bán trú (chỉ HT, PHT — bếp không có Web)

| Route | Màn hình | Vai trò | API chính | Bảng DB | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- | --- |
| `/boarding/today` | Bán trú hôm nay | HT, PHT | `GET /nutrition/day-board?date=T`, `POST /nutrition/stock/days/{date}/sync` | `meal_plan_weeks|items` (tuần của T và tuần sau), `meal_counts`, `food_samples`, `stock_daily_demands`, `stock_movements`, `fresh_checks` | tuần của T PUBLISHED; tuần sau DRAFT (hiện cảnh báo chưa công bố); T có `meal_counts`; kiểm nhận tươi lần 1 đã ghi; có mẫu RECORDED chờ duyệt | đồng bộ kho |
| `/menu/weekly` | Kế hoạch tuần & công bố | HT, PHT | `GET /nutrition/meal-plans`, `/{weekStart}`; `PUT`, `POST /publish`; `GET /nutrition/menu-templates`, `/nutrition/dishes`, `/age-groups` | `meal_plan_weeks`, `meal_plan_items`, `dishes`, `menu_template_sets|items`, `age_group_meal_configs` | các tuần đã qua PUBLISHED; **W** PUBLISHED; W+1 DRAFT (demo công bố); có ngày `off` | lưu, áp mẫu, công bố |
| `/menu/statistics` | Định lượng & đi chợ | HT, PHT | `POST /nutrition/menu-statistics` (tuần W hoặc mẫu) | `meal_plan_items`, `dish_ingredients`, `foods` (giá, đơn vị mua), `age_group_meal_configs` | món có định lượng cho mọi nhóm tuổi; thực phẩm có `unit_price` | in |
| `/menu/nutrition` | Cân đối khẩu phần | PHT (menu); HT mở bằng link | KH tuần + age-group-configs + `POST menu-statistics` | như trên | config hiệu lực mỗi nhóm tuổi (kcal, P:L:G, vi chất) | lưu điều chỉnh |
| `/menu/templates` (`/new`, `/:id`) | Mẫu thực đơn | HT, PHT | `GET/POST/PUT /nutrition/menu-templates`, `PATCH /{id}/status` | `menu_template_sets`, `menu_template_items` | ≥1 bộ ACTIVE mỗi khối NURSERY/KINDERGARTEN đủ 4 tuần × 5 ngày × bữa; 1 bộ INACTIVE | tạo, sửa, ngừng |
| `/menu/foods` (`/:id`, `/new`, `/:id/edit`) | Thực phẩm | HT, PHT (sửa: `NUTRITION_MANAGE`) | `GET /nutrition/foods`, `/{id}`, `/nutrition/allergens`; POST/PUT/PATCH status, `POST /import` | `foods` | đủ FRESH/DRY, ANIMAL/PLANT, có `allergen_codes`, vi chất; vài INACTIVE | thêm, sửa, ngừng, nhập |
| `/menu/dishes` (`/:id`, `/new`, `/:id/edit`) | Món ăn | HT, PHT | `GET /nutrition/dishes`, `/{id}`; POST/PUT, `/copy`, `PATCH /status` | `dishes`, `dish_ingredients` | mọi `dish_type`; định lượng mọi nhóm tuổi; món tham khảo (`reference`); INACTIVE | thêm, sửa, sao chép |
| `/menu/setup` | Cấu hình nhóm tuổi | HT, PHT | `GET /nutrition/age-group-configs`, `/{ag}/versions`; `POST /batch` | `age_group_meal_configs`, `age_groups.meal_sessions` | mỗi nhóm tuổi 1 config hiệu lực + 1 version cũ; `meal_sessions` khác rỗng | lưu |
| `/menu/suppliers` (`/:id`) | Nhà cung cấp | HT, PHT | `GET/POST/PUT /nutrition/suppliers`, `PATCH /status` | `suppliers` | ≥4 ACTIVE (tươi, khô), 1 INACTIVE | thêm, sửa, ngừng |
| `/kitchen/published-menu` | Thực đơn bếp nhận (Bếp hôm nay) | HT, PHT | `GET /nutrition/kitchen/day?date=T` | `meal_plan_items` (PUBLISHED), `meal_counts`, `dishes`, `dish_ingredients`, `food_samples`, `meal_photos` | T ngày học thuộc tuần PUBLISHED, có số suất | xem, in |
| `/kitchen/meal-count` | Sổ báo ăn đã xác nhận | HT, PHT | `GET /meal-counts?date` | `meal_counts` CONFIRMED, `meal_count_lines` | N đã CONFIRMED | xem |
| `/kitchen/forecast` (`/new`, `/:id`) | Bản dự trù hàng tươi | HT, PHT | `GET/POST/DELETE /nutrition/fresh-forecasts`, `/preview` | `fresh_forecasts`, `fresh_forecast_lines`, `meal_plan_items`, `foods` FRESH | 1 bản cho kỳ chứa T (kỳ phải PUBLISHED) + 1 bản kỳ trước | lập, xóa |
| `/kitchen/fresh-receipts` | Kiểm nhận hàng tươi | HT, PHT | `GET/PUT /nutrition/fresh-checks?date=T` | `fresh_checks`, `fresh_check_lines`, `fresh_forecasts` | T có bản dự trù phủ; lần 1 đã ghi (có SHORT/OVER), lần 2 chưa | ghi kiểm nhận |
| `/kitchen/market-day` | Bảng đi chợ ngày | HT, PHT | `GET /nutrition/day-market?date` | `fresh_forecasts`, `meal_counts`, `meal_plan_items` | như dự trù | in |
| `/kitchen/dry-stock`, `/stock-history`, `/stock-shortfalls`, `/stock-receipts` (`/new`, `/:id`, `/:id/edit`) | Kho khô (tồn lô, lịch sử, phần thiếu, phiếu nhập) | HT, PHT (`STOCK_MANAGE`) | `GET /nutrition/stock/lots|movements|shortfalls|receipts(/{id})`; POST/PUT/DELETE, `/confirm`, `/cancel` | `stock_receipts`, `stock_receipt_lines` (= lô), `stock_movements`, `stock_daily_demands`, `suppliers`, `foods` DRY | phiếu CONFIRMED (có lô sắp hết hạn), 1 DRAFT, 1 CANCELLED; DEDUCT các ngày đã chốt suất; ≥1 dòng `shortfall_qty > 0` | lập, xác nhận, hủy phiếu |
| `/kitchen/inspections` | Kiểm thực ba bước | HT, PHT | `GET /inspections/day?date=T`, `PUT /day/{tab}`, `POST /sign|reopen`, `GET /inspections/signatures/{key}/url` | `inspection_sheets` (tab FRESH/DRY/PROCESS/SERVE/SAMPLE, DRAFT/SIGNED), `food_samples`, `user_signatures` | T: FRESH SIGNED, PROCESS DRAFT, còn lại chưa; ngày trước đủ SIGNED; người ký có chữ ký | ghi, ký, mở lại |
| `/kitchen/food-samples` | Lưu mẫu thực phẩm | HT, PHT (`FOOD_SAFETY_APPROVE`) | `GET /nutrition/food-samples`, `POST /{id}/approve|approve-disposal` | `food_samples` | T: RECORDED chờ duyệt; mẫu ≥24h: `disposal_status=RECORDED` chờ duyệt hủy; cũ: APPROVED/APPROVED | duyệt mẫu, duyệt hủy |
| `/kitchen/meal-photos` | Ảnh bữa ăn | HT, PHT | `GET /nutrition/meal-photos?date`, `/{id}/url`; `GET/PUT /nutrition/meal-photo-windows` | `meal_photos`, `edu_settings.meal_photo_windows` | T và vài ngày trước có ảnh (+object S3 `meals/...`); khung giờ đã cấu hình | sửa khung giờ |
| `/kitchen`, `/kitchen/required-quantity`, `/kitchen/missing-food`, `/kitchen/suppliers*` | (demo / chuyển hướng) | — | — | — | không cần DB | — |
| `/menu/menus*`, `/menu/allergy-menus*`, `/menu/ai-suggestion`, `/menu/plans*`, `/menu/flow`, `/attendance/meal-handover` | (demo / chuyển hướng) | — | — | — | không cần DB | — |

## 2. App Flutter — màn theo vai trò

Hiện trạng: toàn bộ dữ liệu giả (lưu ý 5). Cột "Endpoint khi nối" lấy theo `BE:docs/api/API_INDEX.md`; "chưa có" = BE chưa có endpoint cho PH (App docs ghi "Endpoint: chưa có").

### 2.1 Chung (mọi vai trò)

| Route | Màn | Endpoint khi nối | Bảng | Điều kiện để có dữ liệu |
| --- | --- | --- | --- | --- |
| `/login`, `/forgot-password(/otp,/new-password)` | Đăng nhập, quên MK | `/auth/*` | `user_accounts`, `password_tokens` | TK PH đăng nhập bằng SĐT; PH lần đầu phải tải ảnh chân dung (S3) |
| `/{role}/notifications` (+ chi tiết) | Thông báo | `GET /me/notifications`, read, read-all; `PUT/DELETE /me/devices` | `notifications`, `device_tokens` | mỗi TK PH/GV/bếp ≥5 thông báo, có chưa đọc |
| `/{role}/account`, `.../profile`, `.../password` | Tài khoản, thông tin cá nhân | `GET /accounts/me`, `/accounts/me/photo`, `PUT /me/password` | `user_accounts` | — |

### 2.2 Phụ huynh (`/parent*`, chọn con qua `?child=`)

| Route | Màn | Endpoint khi nối | Bảng | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- |
| `/parent` | Trang chủ (hôm nay của con) | chưa có | `attendance_records`, `daily_health_entries`, `meal_plan_items`, `meal_photos`, `pickup_records`, `daily_observations` | PH có ≥2 con (1 TK xem mọi con — PAR-06); con có điểm danh, sức khỏe, ảnh bữa, đón trẻ ngày N | chọn con |
| `/parent/child` | Bé của tôi (Hôm nay / Sức khỏe / Phát triển) | `GET /children/{id}/health/published`; phát triển: chưa có | `health_measurements` (published), `health_interpretations` APPROVED, `daily_observations`, `child_evaluations` CONFIRMED, `behaviour_tickets` | ≥3 lần đo đã công bố; nhận xét tháng CONFIRMED; phiếu bé ngoan | — |
| `/parent/leave-notices`, `/new` | Xin nghỉ, đơn đã gửi | `GET /leave-requests/me`, `POST /leave-requests`, `PATCH /{id}/cancel` | `leave_requests` | mỗi PH mẫu có đơn đã qua, tương lai, đã hủy | gửi, hủy |
| `/parent/menu` | Thực đơn tuần | chưa có | `meal_plan_weeks` PUBLISHED, `meal_plan_items`, `dishes`, `meal_photos` | tuần W PUBLISHED; ảnh bữa các ngày đã qua (S3) | — |
| `/parent/activities` | Hoạt động hằng ngày | chưa có | `edu_class_docs` kind=day APPROVED | giáo án ngày N của lớp con đã duyệt | — |
| `/parent/attendance` | Điểm danh tháng | chưa có | `attendance_records`, `leave_requests` | cả tháng có điểm danh, có ngày vắng có phép | — |
| `/parent/child-profile` | Hồ sơ bé | `GET /children/{id}/profile`, photo | `children`, `child_profiles`, `child_guardians`, `child_allergies`, `child_health_declarations` | hồ sơ đầy đủ, có dị ứng | sửa liên lạc của mình |

### 2.3 Giáo viên (`/teacher*`)

| Route | Màn | Endpoint khi nối | Bảng | Điều kiện để có dữ liệu | Thao tác |
| --- | --- | --- | --- | --- | --- |
| `/teacher` | Trang chủ lớp | như "Lớp hôm nay" Web | như 1.1 | như 1.1 | — |
| `/teacher/class` | Lớp học (sĩ số) | `GET /children`, `/school-structure` | `enrollments`, `children` | lớp đủ 20–25 trẻ | — |
| `/teacher/attendance` | Điểm danh & báo ăn | như `/attendance` Web | như 1.4 | như 1.4 | lưu |
| `/teacher/leave-notices` | Đơn nghỉ của lớp | `GET /leave-requests` | `leave_requests` | đơn hôm nay/tương lai của lớp | xem |
| `/teacher/reviews` (`/:childId`) | Đánh giá hằng ngày | `GET/PUT /classes/{id}/daily-assessment` | như 1.6 | như 1.6 | lưu |
| `/teacher/pickup` (`/:childId`) | Đón trẻ, xác minh | `GET/POST /pickups`, `GET /pickups/children/{id}`, ảnh PH | `pickup_records`, `child_guardians` (ảnh S3), `attendance_records` | T: trẻ PRESENT; vài trẻ đã PICKED_UP; ảnh PH để đối chiếu | ghi đón |
| `/teacher/health` (`/:childId`) | Đo sức khỏe | `GET /health-checks/{id}/classes/{cid}/children`, `POST/PUT measurements` | như 1.3 | kỳ MEASUREMENT IN_PROGRESS chứa lớp | lưu |
| `/teacher/schedule` | Lịch hoạt động | `GET /class-docs` (day) | `edu_class_docs` | giáo án N APPROVED | — |
| `/teacher/meal-photos` (`/send`) | Ảnh bữa ăn | `POST /nutrition/meal-photos` cần `KITCHEN_RECORD` (BE: bếp) | `meal_photos` | khung giờ cấu hình; App giả gán cho GV — lệch NUT-16 | chụp, gửi |

### 2.4 Bếp (`/kitchen*`, 1 TK chung mỗi điểm trường — AUTH-22)

| Route | Màn | Endpoint khi nối | Bảng | Điều kiện để có dữ liệu |
| --- | --- | --- | --- | --- |
| `/kitchen` | Trang chủ bếp | `GET /nutrition/kitchen/day` | như `/kitchen/published-menu` | T thuộc tuần PUBLISHED, có số suất |
| `/kitchen/menu` | Thực đơn theo nhóm tuổi | `GET /nutrition/kitchen/day` / meal-plans | `meal_plan_items` | tuần W PUBLISHED |
| `/kitchen/meals` | Suất ăn theo lớp | `GET /meal-counts` | `meal_counts`, `meal_count_lines` | N CONFIRMED, T PENDING/CONFIRMED |
| `/kitchen/quantities` | Định lượng | kitchen/day (định lượng theo món × suất) | `dish_ingredients`, `meal_counts` | như trên |
| `/kitchen/receipts`, `/receipts/shortage` | Nhận nguyên liệu, báo thiếu | (BE: PHT ghi trên Web — NUT-12; báo thiếu qua Zalo — NUT-22) | `fresh_checks` | lệch rule — không cần dữ liệu riêng |
| `/kitchen/cooking` | Trạng thái nấu | (NUT-14 REMOVED) | — | — |
| (chưa có màn) | Lưu mẫu, ảnh bữa | `POST /nutrition/food-samples`, `/dispose`, `POST /nutrition/meal-photos` | `food_samples`, `meal_photos` | dữ liệu do bếp ghi ⇒ dataset phải tự chèn để màn duyệt Web có việc |

### 2.5 PHT, HT trên App (chỉ debug, ngoài phạm vi AUTH-21 — giữ tham khảo)

| Route | Màn | Bảng sẽ đọc |
| --- | --- | --- |
| `/vice-principal`, `/approvals`, `/approvals/meals(/:id)`, `/approvals/rewards`, `/meal-confirm`, `/kitchen`, `/menu`, `/menu/nutrition`, `/children` | Trang chủ cơ sở, chờ duyệt, điều chỉnh suất, khen thưởng, chốt suất, trạng thái bếp, thực đơn, cân đối, trẻ cơ sở | `meal_counts`, `reward_lists`, `meal_plan_*`, `age_group_meal_configs`, `children` |
| `/principal`, `/approvals`, `/approvals/rewards(/:id)`, `/children(/class)`, `/menu(/nutrition)`, `/campuses` | Tổng quan trường, chờ HT duyệt, khen cuối năm, trẻ toàn trường, thực đơn theo cơ sở, cơ sở & PHT | như trên + `campuses`, `staff_assignments` |

## 3. Checklist trạng thái phải có — theo bảng

| Bảng | Trạng thái / mốc phải có | Phục vụ màn |
| --- | --- | --- |
| `school_years` | 2025-2026 CLOSED (dữ liệu cuối năm đầy đủ), 2026-2027 ACTIVE (đã đặt ngày HK1/HK2), 2027-2028 PLANNED dựng dở | năm học, xếp lớp năm mới, màn cuối kỳ |
| `school_holidays`, `attendance_cutoffs` | ngày nghỉ trong năm (≥1 ngày trong 14 ngày gần nhất để test); ≥2 bản giờ chốt | điểm danh, giờ chốt |
| `campuses`, `rooms`, `classrooms` | 2 ACTIVE (1 main); đủ RoomType, 1 INACTIVE; lớp ACTIVE năm hiện tại + lớp năm PLANNED; lớp CLOSED năm cũ | cấu hình |
| `staff_assignments` | PHT mỗi cơ sở; 2 GV/lớp (1 homeroom); TT mỗi nhóm tuổi; bếp mỗi cơ sở; 1 GV dạy thay (`valid_to`); 1 assignment đã kết thúc (GV nghỉ việc / đổi lớp); GV chưa phân lớp; phân công năm PLANNED | phân công, phạm vi mọi màn |
| `user_accounts` | ACTIVE, INACTIVE, phải đổi MK lần đầu, PH `locked_by_system`, PH có `photo_key` | đăng nhập, TK nhân sự/PH |
| `permission_grants`, `permission_change_log` | dòng role seed; GRANT + REVOKE cá nhân; log tương ứng | phân quyền |
| `children` | ACTIVE, ≥5 PENDING_PLACEMENT/cơ sở, ≥3 LEFT | danh sách, xếp lớp, tiếp nhận |
| `child_guardians` | chưa kích hoạt (`user_id NULL`), đã liên kết; PH nhiều con; 1–3 PH/trẻ | TK phụ huynh, App PH |
| `child_health_declarations`, `child_allergies` | REPORTED / NONE_REPORTED / NOT_PROVIDED; `tb_contact_state=REPORTED`; dị ứng 2 mức | khai báo, suất thay thế |
| `class_placements` | PROMOTE/STAY/LEAVE/GRADUATE một phần trẻ năm PLANNED | xếp lớp năm mới, lớp năm mới |
| `attendance_records` | mọi ngày học từ đầu năm tới N (PRESENT/EXCUSED/UNEXCUSED); **T một phần lớp** | điểm danh, tổng hợp, trang chủ |
| `attendance_day_unlocks` | 1 hiệu lực, 1 thu hồi, 1 hết hạn | mở lại điểm danh, sửa ngày cũ |
| `leave_requests` | quá khứ, hôm nay, tương lai, nhiều ngày, đã hủy — đều trong tháng của T | đơn nghỉ Web/App |
| `daily_health_entries` | N và T: đa số bình thường + vài NOTED, nhiệt độ MILD/HIGH | sức khỏe trong ngày, trang chủ |
| `pickup_records` | T: PICKED_UP một phần, WAITING phần còn lại | App GV/PH (Web demo) |
| `meal_counts` (+lines, history) | ngày đã qua CONFIRMED; T PENDING_CONFIRMATION (nếu sau giờ chốt); 1 `needs_reconfirmation` | sổ báo ăn, chờ duyệt, bán trú |
| `meal_plan_weeks`, `meal_plan_items` | tuần đã qua PUBLISHED, **W PUBLISHED**, **W+1 DRAFT**, có ngày `off` | KH tuần, bếp, PH thực đơn, dự trù |
| `menu_template_sets/items` | ACTIVE mỗi khối đủ 4 tuần; 1 INACTIVE | mẫu thực đơn |
| `foods`, `dishes`, `dish_ingredients`, `age_group_meal_configs`, `suppliers` | danh mục đủ loại, có INACTIVE; định lượng mọi nhóm tuổi; config hiệu lực + version cũ | danh mục, định lượng, cân đối |
| `stock_receipts/lines/movements`, `stock_daily_demands` | CONFIRMED / DRAFT / CANCELLED; lô sắp hết hạn; DEDUCT theo ngày đã chốt; shortfall > 0 | kho khô |
| `fresh_forecasts/lines`, `fresh_checks/lines` | bản dự trù kỳ chứa T + kỳ trước; T lần 1 đã ghi (SHORT), lần 2 chưa | hàng tươi, bán trú hôm nay |
| `food_samples` | T RECORDED; ≥24h `disposal RECORDED`; cũ APPROVED/APPROVED | lưu mẫu, kiểm thực |
| `inspection_sheets` | T: một số tab SIGNED, một tab DRAFT; ngày trước đủ SIGNED | kiểm thực |
| `meal_photos`, `edu_settings.meal_photo_windows` | ảnh T và vài ngày trước (S3); khung giờ có | ảnh bữa, PH thực đơn |
| `user_signatures` | HT, PHT có chữ ký default (S3) | ký biên bản / chữ ký |
| `health_check_campaigns` (+history, absences) | UPCOMING, IN_PROGRESS (chứa T), CLOSED, CANCELLED × EXAM/MEASUREMENT × cơ sở | đợt khám |
| `health_measurements` (+edits) | số đo đã công bố nhiều kỳ; phiếu khám DRAFT + CONCLUDED; có sửa sau công bố | sổ SK, tổng hợp cân đo, PH |
| `health_interpretations` | DRAFT + APPROVED | xu hướng, PH |
| `edu_year_plans`, `edu_team_plans` (+con, events, authors) | APPROVED mỗi nhóm tuổi; v2 DRAFT; REVIEW; bị trả (event `return`); review item chưa resolve | KH năm/tổ, duyệt |
| `edu_class_docs`, `edu_class_doc_releases` | branch APPROVED; theme đang chạy APPROVED; week tuần trước APPROVED / W REVIEW / W+1 DRAFT; **day N APPROVED**; REVIEW chờ TT và chờ PHT; 1 quá hạn; dossier có release | KH lớp, duyệt, đánh giá MT, PH hoạt động |
| `edu_resource_items/tags/attachments` | LINK + FILE mỗi GV; gắn vào giáo án | kho học liệu, trình chiếu |
| `daily_observations`, `class_daily_comments` | mọi ngày học tới N; T một phần lớp | đánh giá ngày, tổng quan |
| `daily_activity_results` | các ngày có giáo án APPROVED | đánh giá theo MT, tổng quan |
| `assessment_day_unlocks` | 1 hiệu lực, 1 thu hồi | mở lại đánh giá |
| `child_evaluations` (+events) | MONTH tháng 9–10: CONFIRMED + AWAITING_REVIEW; năm CLOSED: SEMESTER, YEAR CONFIRMED | hồ sơ phát triển, PH |
| `class_monthly_comments` | tháng 10: CONFIRMED (1 lớp) / chưa xác nhận | chất lượng tháng |
| `theme_evaluations` | chủ đề đã kết thúc: confirmed + nháp | đánh giá cuối chủ đề |
| `behaviour_tickets` | WEEKLY tuần trước, MONTHLY tháng trước | phiếu bé ngoan, PH |
| `class_ratings`, `semester_assessments`, `reward_lists/items/events` | chỉ năm CLOSED (reward: DRAFT/PENDING_VP/PENDING_PRINCIPAL/APPROVED/RETURNED) | màn cuối kỳ |
| `notifications` | theo vai trò, có `read_at NULL`: GV `EDU_RETURNED`, `EDU_STEP_APPROVED`, `ATTENDANCE_DAY_UNLOCKED`, `HEALTH_CHECK_OPENED`, `CHILD_PLACED`; TT/PHT `EDU_SUBMITTED`, `EDU_DEADLINE_NEAR`; PHT/HT `MEAL_COUNT_RECONFIRM`, `GUARDIAN_PHONE_CHANGE_REQUESTED`, `CHILD_TB_CONTACT`; PH `CHILD_PICKED_UP`, `HEALTH_RESULT_PUBLISHED`, `GUARDIAN_PHONE_CHANGE_DECIDED` | thông báo, chuông |
| `guardian_phone_change_requests`, `parent_sms_logs` | 1 PENDING, 1 APPROVED (`sms_status` SENT), 1 REJECTED; log SENT/FAILED | TK phụ huynh (Web dev chưa có màn duyệt) |

## 4. Màn cần file, media hoặc dịch vụ ngoài

| Phụ thuộc | Màn | Dữ liệu cần | Không có thì hiển thị |
| --- | --- | --- | --- |
| S3 — ảnh trẻ / PH / TK | hồ sơ trẻ, TK phụ huynh, xác minh đón trẻ (App), hồ sơ cá nhân | `photo_key` + object thật (JPG/PNG/WEBP ≤2 MiB) | ảnh tùy chọn: avatar chữ cái; key trỏ object thiếu ⇒ URL presign lỗi ảnh vỡ — chỉ gán key khi đã upload |
| S3 — ảnh bữa ăn | `/kitchen/meal-photos`, bếp hôm nay, App PH thực đơn | `meal_photos.storage_key` `meals/...` + object | danh sách có dòng nhưng ảnh vỡ; không dòng ⇒ "chưa có ảnh" |
| S3 — chữ ký | `/account/signatures`, ký `/kitchen/inspections` | `user_signatures` + object | không ký được biên bản (bắt buộc chữ ký) |
| S3 — học liệu FILE | `/education/resources`, trình chiếu, giáo án ngày | `edu_resource_items.kind=FILE` + object `hoclieu/` (`docs/system/EDUCATION_MEDIA_S3.md`) | mở/tải lỗi; dùng LINK để không phụ thuộc S3 |
| Upload xử lý tại chỗ | nhập trẻ Excel, nhập thực phẩm, xem trước file mục tiêu/chủ đề nhánh | file mẫu xlsx/docx do người test chọn | không cần dữ liệu DB |
| SMS (`integration/sms`) | kích hoạt TK PH, MK tạm, duyệt đổi SĐT | provider SMS; log `parent_sms_logs` | gửi FAILED (lưu log); phiếu in dự phòng vẫn dùng được |
| Email OTP | quên mật khẩu (Web) | mail provider | không nhận OTP (bản demo hiện mã nếu `IS_OTP_DEMO`) |
| Keycloak | mọi đăng nhập | realm import từ dataset (`docker/keycloak/*/carenest-users-0.json`) | không đăng nhập được; đổi dataset phải tạo lại DB keycloak |
| AI (`integration/ai`, OpenAI-compatible) | phân tích xu hướng sức khỏe, nháp nhận xét tháng/học kỳ/cuối năm (`redraft`, `ai-draft`); tên nhánh, bước hoạt động (BE chưa có — pending); AI thực đơn (demo) | dataset nên có sẵn `health_interpretations`, `child_evaluations.draft_content` | AI tắt ⇒ mẫu nhận xét nền (OBS-04); nút phân tích báo lỗi dịch vụ |
| Push (`integration/push`) | thông báo App | `device_tokens` | chỉ có thông báo trong App (bảng `notifications`) |

## 5. Còn mở

- App `origin/dev` chưa có tầng API: dataset chưa thể kiểm bằng App; nhánh `feature/G94-194-FE-FEAT-49-app-ui-redraw` (14/11) chưa đối chiếu.
- `GET /meal-counts/class` (suất lớp cho GV) và `GET /notifications/{id}` FE gọi nhưng BE dev chưa có.
- Màn duyệt đổi SĐT phụ huynh (`/parent-accounts/phone-change-requests`) BE có, Web dev chưa gọi.
- Có sinh năm 2025-2026 CLOSED đầy đủ để phủ màn cuối kỳ hay chấp nhận các màn đó trống hợp lệ — cần người dùng quyết.
