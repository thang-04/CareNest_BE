# Tác động FE của thay đổi auth theo khảo sát 27/10

Nguồn: đọc `CareNest_FE` nhánh `dev` @ `8896d90` (chỉ đọc, không sửa). APP không thuộc phạm vi. Plan: `docs/plans/active/2026-10-27-survey-auth-alignment.md`. Rule: AUTH-20, AUTH-21, AUTH-22, AUTH-23 (`docs/business/BUSINESS_RULES.md`). Đường dẫn dưới đây tính từ `CareNest_FE/src`.

## P0 — chặn chế độ gateway
1. `services/authProfile.js:2` `WEB_ROLES` có KITCHEN_STAFF, thiếu ADMIN (ADMIN rơi vào trang "chưa có màn hình"). Đổi thành ADMIN, PRINCIPAL, VICE_PRINCIPAL, TEACHER; thêm trường suy ra: `managedCampusIds`, `allCampusView` (permissions có `ALL_CAMPUS_VIEW`), `teamLeaderAgeGroupIds`.
2. `models/User.js:15-29`: thêm ADMIN ("Quản trị hệ thống"); bỏ TEAM_LEADER khỏi role đăng nhập.
3. Lỗi đăng nhập: `pages/auth/LoginPage.jsx:91` hiện nguyên `desc` tiếng Anh. Thêm bộ dịch `desc` dùng chung (cả `pages/account/ResetPasswordPage.jsx:87`):
   - `This account cannot sign in on the Web app` → "Tài khoản này dùng ứng dụng di động CareNest (phụ huynh, nhân viên bếp)."
   - `Account is not provisioned` / `Account is inactive` → liên hệ nhà trường, không retry.
4. Refresh 403: `contexts/AuthContext.jsx:33-34` coi mọi lỗi khác 401 là `unavailable` (nút "thử lại" vô ích); `services/http/axiosClient.js:52` chỉ báo 401. Đổi: 403 ⇒ về `anonymous` + thông báo đã dịch. BE đã xóa cookie khi refresh 403.
5. `routes/ProtectedRoute.jsx:17-30`: sửa câu "Tài khoản chưa có màn hình phù hợp" thành: Web dành cho BGH, giáo viên, quản trị; phụ huynh và bếp dùng App.
6. ADMIN trên Web: thêm menu (`layouts/menuConfig.js:134`), `/school/staff` gác bằng `ACCOUNT_MANAGE | ACCOUNT_STATUS_MANAGE` (`routes/AppRoutes.jsx:318`, `utils/school-config/schoolConfigPermissions.js:145`); Admin chỉ tạo được HT (`utils/school-config/schoolConfigValidation.js:187`).

## P1 — quyết định khảo sát
7. Mock auth (mặc định đang là mock): `mocks/authMockRepository.js` trả 403 cùng `desc` cho bếp/phụ huynh; `/accounts/me` mock theo đúng hình BE (`permissions` là mảng mã, không phải object F/R/N; bỏ `areas`); tổ trưởng = GV + phân công `AGE_GROUP_TEAM_LEADER`.
8. Thay mọi `TEAM_LEADER` (role) bằng `isTeamLeader(user, ageGroupId)`:
   - Route/menu: `routes/AppRoutes.jsx:183,195-198,485`, `layouts/menuConfig.js:185-195` (bỏ menu riêng, dùng menu GV + mục tổ trưởng), `layouts/quickActions.js:23,36`, `pages/HomePage.jsx:10`.
   - Kế hoạch giáo dục: `components/education-plan/planUi.jsx:89,93,117`, `pages/education-plan/*` (Goal*, PlanReviewQueue, EducationHomeRedirect), `hooks/education-plan/useEducationPlan.jsx:26`. `eduConfig.js` giữ `TEAM_LEADER` làm khóa bước duyệt.
   - Bỏ TEAM_LEADER khỏi helper phạm vi (tổ trưởng không xem thêm lớp/trẻ): `utils/attendance/attendancePermissions.js`, `utils/pickup/pickupPermissions.js`, `utils/children/childrenPermissions.js`, `utils/assessment/assessmentPermissions.js`, `utils/facility/facilityPermissions.js`, `mocks/schoolMockRepository.js:61,92`.
   - Màn nhân sự: bỏ `roleOf`/`baseRole` (`pages/school-config/StaffAccountsPage.jsx:25`, `components/school-config/StaffAccountModals.jsx:14`); gán tổ trưởng chỉ HT (`TeacherAssignmentPage.jsx:88-92`, `AgeGroupsClassesPage.jsx:60`).
9. Bếp rời Web: bỏ menu `menuConfig.js:206-214`, route bếp (`AppRoutes.jsx:185,433-452,476-477`, xóa `/kitchen/preparation/update`), `HomePage.jsx:12`, nhánh bếp trong các trang/permission util bếp. Danh sách nhân sự hiển thị "Tài khoản bếp dùng chung · <điểm trường>" (AUTH-22). BGH vẫn xem các màn bếp/kho chỉ đọc.
10. Bỏ `areas` của PHT: `models/school-config/schoolConfigConstants.js:221-380`, `utils/school-config/schoolConfigPermissions.js:60-142`, `routes/RoleGuard.jsx:21-39` (AreaGate), `layouts/menuConfig.js:228-247`, `pages/school-config/VicePrincipalAssignmentPage.jsx` (còn phân công campus). Xem liên cơ sở: helper `canViewAllCampuses`, `canEditCampusData(campusId)`; campus khác hiện banner "Đang xem <điểm trường> · chỉ xem", ẩn nút sửa.
11. Màn phân quyền: nối `services/account/permissionService.js` (đang 501) vào `GET /permissions`, `GET|PUT /role-permissions`, `GET|PUT /accounts/{id}/permission-overrides`, `GET /permission-change-logs`. Cột ma trận lấy từ API (7 phần tử, gồm `TEAM_LEADER`; ẩn PARENT/ADMIN, PRINCIPAL cố định) thay hằng `utils/account/permissionMatrix.js:4`; cột rỗng hiện "Chưa có quyền cấu hình cho tổ trưởng". Nhãn `ALL_CAMPUS_VIEW`: "Xem dữ liệu trường ở mọi điểm trường (chỉ xem)". Override dùng `roles` của API (có `TEAM_LEADER`) thay `staff.role`. Cổng màn hình theo `PERMISSION_MANAGE`.
12. Menu/route theo `permissions`: thêm khóa `permission` cho menu và RoleGuard (nhân sự `ACCOUNT_*`, phụ huynh `PARENT_ACCOUNT_MANAGE`, phân công `STAFF_ASSIGNMENT_MANAGE`, phân quyền `PERMISSION_MANAGE`). Module dinh dưỡng/giáo dục giữ gác theo role tới khi BE có mã mảng.
13. API nhân sự: `services/school-config/api/schoolConfigApi.js:42-47`, `services/masterDataService.js:9` gọi `/users` ⇒ `/accounts`, `/staff-assignments`, `/campuses`, `/classrooms`. Ẩn chức năng BE chưa có (đổi vai trò, sửa hồ sơ).

## P2 — dọn
F/R/N còn sót (`models/school-config/permissionCatalog.js`, `schoolConfigConstants.js:134-217`), `TeamLeaderDashboard.jsx`, `KitchenDashboard.jsx`, `MealPreparationUpdatePage.jsx`, chip đăng nhập demo của bếp, docs FE `docs/integration/AUTH_FLOW.md`.

## Test FE cần sửa (`tests/*.test.mjs`, node:test)
`auth/profile-challenge.test.mjs:31-54`, `auth/service.test.mjs` (thêm ca 403 Web), `menu/nutrition-flow.test.mjs:40-72`, `integration/selective-merge/permission-center.test.mjs` (6 role/4 cột, TEAM_LEADER, 501), `menu/demo-seed-coverage.test.mjs:21`; `kitchen/*.test.mjs` nếu bỏ kiểm role bếp.

## Menu sau thay đổi
- ADMIN: Trang chủ · Tài khoản nhân sự (tạo HT, khóa/mở) · Hồ sơ/mật khẩu.
- PRINCIPAL: như hiện tại + phân công GV/tổ trưởng + Vai trò & quyền; bếp/kho chỉ xem.
- VICE_PRINCIPAL: dữ liệu campus mình; mục quản lý theo quyền; có `ALL_CAMPUS_VIEW` thì có bộ lọc điểm trường, campus khác chỉ xem.
- TEACHER: lớp mình. TEACHER + tổ trưởng: thêm soạn mục tiêu năm/kế hoạch chủ đề và duyệt giáo án nhóm tuổi; không xem thêm lớp/trẻ.

## Phụ thuộc BE / câu hỏi mở
1. Nhận biết tổ trưởng ở FE cần `/accounts/me` trả phân công `AGE_GROUP_TEAM_LEADER` + `ageGroupId` — nhánh G94-226 đã làm (`CROSS_REPO_MAP` của nhánh đó), chưa vào dev.
2. PHT có `ALL_CAMPUS_VIEW` chưa có API danh sách campus/lớp/trẻ campus khác (`GET /campuses`, `/classrooms`, `/children` là danh sách phục vụ quản lý, theo campus phụ trách). Hiện chỉ xem được `GET /children/{id}`.
3. Ma trận Web có giữ cột KITCHEN_STAFF (quyền dùng ở App) không.
4. Chưa có API "tài khoản có ngoại lệ"; FE dùng change log hay cần BE thêm.
5. AUTH-22 một tài khoản bếp/cơ sở: BE chưa chặn tạo tài khoản bếp thứ hai.
6. Trước khi có mã mảng, cả hai PHT đều thấy module dinh dưỡng và giáo dục.
