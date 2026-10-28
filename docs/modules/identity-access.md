# Module — identity-access

Feature: `account` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (ADR-0011)

## Mục đích
Đăng nhập, tài khoản, role, permission; cung cấp kiểm tra access scope cho mọi module.

## Owns
UserAccount, Role, Permission, role↔permission; kiểm tra access scope (`AccessScopeService` — tên minh họa), `CurrentUser`.

## KHÔNG owns
StaffAssignment (school-structure), GuardianChildLink (child), dữ liệu nghiệp vụ.

## Rules
AUTH-01..21 (`docs/business/BUSINESS_RULES.md`). Role/permission: `docs/business/USER_ROLES.md`.

## Phụ thuộc
- Dùng: common/utils.
- Được dùng bởi: mọi module domain (check permission + scope).
- Kiểm tra scope (tên minh họa trong guide: `AccessScopeService`) đặt ở `security/` hoặc service dùng chung, đọc assignment qua service `organization` và liên kết phụ huynh qua service `child`; các service đó không gọi ngược lại để tránh vòng.

## API & bảng
- Auth: Keycloak + Resource Server (ADR-0011, `docs/contracts/AUTH_CONTRACT.md`, `docs/system/KEYCLOAK.md`). Role toàn cục ở Keycloak; permission cấu hình được theo ADR-0017: danh mục enum `Permission`, bảng `role_permissions` (ma trận PHT/GV/bếp), `user_permission_overrides` (GRANT/REVOKE), `permission_change_log` (V7).
- Bảng: `user_accounts` (+`primary_role` V8 — bản sao role Keycloak), `role_permissions`, `user_permission_overrides`, `permission_change_log` (`docs/database/DATA_DICTIONARY.md`). Mã quyền đề xuất cho bán trú/dinh dưỡng/kho/AI và cột `staff_assignments.shared_service`: tài liệu đề xuất MEAL_NUTRITION_AI_SCHEMA §2 (PROPOSED) — chưa có trong repo (link cũ không tồn tại, gỡ 2026-10-28).
- Quản lý tài khoản (AUTH-15..17; quyền `ACCOUNT_MANAGE`/`ACCOUNT_STATUS_MANAGE`, phạm vi `security/authorization/StaffAccessPolicy`): `POST /accounts` (kèm `assignment`), `PATCH /accounts/{userId}/status`, `POST /accounts/{userId}/temporary-password`, `GET /accounts`, `GET /accounts/{userId}` (`AccountQueryService`); phân công `POST /staff-assignments`, `PATCH /staff-assignments/{id}/end` (`StaffingService` của feature account, ghi qua `StaffAssignmentService`) — `AccountProvisioningService` → `integration/keycloak/IdentityProviderClient` (adapter `KeycloakIdentityProviderClient`, RestClient + service account, token cache) với bù trừ khi lỗi.
- API: `GET /accounts/me` — profile, roles, permissions (quyền hiệu lực), tóm tắt phạm vi (phân công đang hiệu lực khớp role trong token, id trẻ liên kết). `AccountService` đọc `StaffAssignmentService`/`GuardianChildLinkService` (như `security/authorization`).
- Code: `security/CurrentUser`, `CurrentUserService` (JWT `sub` → `user_accounts`, chặn chưa cấp/INACTIVE), `CurrentUserArgumentResolver`; `controller|service/account/` (interface + `impl/*Impl`, ADR-0014). Web auth gateway: `AuthController`/`PasswordController` → `AuthService` → `integration/keycloak/AuthGatewayClient` (adapter `KeycloakAuthGatewayClient`); cấu hình `carenest.auth.*` (`AuthProperties`, env `CARENEST_AUTH_*`); cookie/TTL/rate limit ở `utils/Constants.Auth`; enum `CareNestRole`, `UserStatus` ở `utils/enums/`.
- Phân quyền (ADR-0017, chỉ PRINCIPAL — `PERMISSION_MANAGE`): `GET /permissions`, `GET /role-permissions[/{role}]`, `PUT /role-permissions/{role}`, `GET|PUT /accounts/{userId}/permission-overrides`, `GET /permission-change-logs` — `PermissionController` → `PermissionService`. Lớp 1 mọi endpoint nghiệp vụ: `@PreAuthorize("@permissionGuard.has('CODE')")` (`security/authorization/PermissionGuard`, bean khai trong `SecurityConfig`); quyền hiệu lực tính trong `CurrentUserService` bằng `EffectivePermissionResolver` và nằm trong `CurrentUser.permissions`.
- Kiểm tra scope: `security/authorization/` — `CampusAccessPolicy`, `ClassroomAccessPolicy`, `ChildAccessPolicy` (role trong token ∧ phân công/liên kết còn hiệu lực; bảng quy tắc ở `docs/architecture/SECURITY.md`). Đây là tên thật thay cho `AccessScopeService` (tên minh họa trong docs cũ).
- **Tài khoản phụ huynh — đã chốt 2026-10-28, BE chưa làm** (code hiện theo AUTH-18 cũ: `POST /children/{id}/guardians` trả `temporaryPassword` để in phiếu, nhận diện theo SĐT, không nhận CCCD; email liên hệ đã làm 2026-10-28 — Known pitfalls):
  - AUTH-18: gửi mật khẩu tạm qua SMS tới SĐT đăng nhập, không trả mật khẩu cho client (cần chọn nhà cung cấp SMS, đi qua `integration/`); nhận diện theo CCCD phụ huynh. Đã có cột (V10): `user_accounts.citizen_id` (UNIQUE), `parent_sms_logs`; entity/API chưa dùng.
  - AUTH-20: thông tin phụ huynh lưu trên hồ sơ trẻ trước khi có tài khoản; lưu form tiếp nhận tạo/liên kết tài khoản trong cùng giao dịch (bù trừ Keycloak như cấp tài khoản hiện nay); nhập Excel, Sửa hồ sơ kích hoạt sau.
  - AUTH-21: sửa thông tin phụ huynh ở hồ sơ trẻ cập nhật tài khoản; đổi SĐT ⇒ đổi tên đăng nhập sang SĐT mới, cấp mật khẩu tạm mới, gửi SMS tới SĐT mới. `docs/database/DATA_DICTIONARY.md` ghi username Keycloak "không đổi được" ⇒ BE phải thiết kế cách đổi tên đăng nhập (chưa chọn giải pháp).
  - Chốt thêm 2026-10-28: bỏ `POST /children/{childId}/guardians`, giữ phần tạo/liên kết tài khoản dùng chung cho kích hoạt (C-4); cổng SMS giả trước, chọn nhà cung cấp trước khi dùng thật (D-2); quan hệ 6 loại (AUTH-18). Plan: `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md`.

## PENDING
P-14 (scope Hiệu phó), P-16 (Admin xem dữ liệu trẻ). P-18 đã đóng 2026-10-28 (AUTH-20).

## Known pitfalls
- Auth React: ADR-0013 là ngoại lệ Web dùng gateway/extension, không phải PKCE. Phase 2 có source API/session/OTP opt-in và V4; FE thuộc Phase3. Provider setup chỉ qua capability một lần; recovery/current-password exchange đòi assertion HMAC60s sau xác minh.
- Phiên Web (ADR-0015): refresh token mã hóa trong cookie, không bảng phiên; refresh đồng thời gộp trong bộ nhớ (`AuthRefreshCoalescer`, 1 instance), không gọi provider lần hai. OTP resend giữ attempts và quota chung; cleanup60s khi feature bật. Regression incidents: web-auth-client-bean, web-auth-oauth-error-mapping, web-auth-session-otp-boundaries.
- MockMvc `jwt()` tự bỏ qua CsrfFilter; test CSRF kèm Bearer phải dùng header thật. Đừng kế thừa global Bearer trong Swagger auth public hoặc để CsrfToken thành query parameter.
- Kiểm tra quyền chỉ ở UI/controller annotation mà quên lọc scope trong query ⇒ lộ dữ liệu campus khác. Scope phải áp ở service/query.
- Không hard-code role→campus; dùng assignment có hiệu lực theo thời gian.
- Service không tự đọc `SecurityContextHolder`/`Jwt`; nhận `CurrentUser` từ controller.
- Chặn tài khoản chưa cấp/INACTIVE nằm ở `ActiveAccountInterceptor` (mọi `/api/**`), không dựa vào việc controller có tham số `CurrentUser`. WebMvcTest có token phải `@MockitoBean CurrentUserService`.
- Lỗi phía Keycloak khi cấp tài khoản: 409 trùng, 400 bị từ chối (policy mật khẩu, ký tự họ tên), 503 không khả dụng; mọi lỗi sau khi đã tạo user Keycloak đều phải bù trừ (kể cả lỗi DB bất kỳ).
- Gọi `checkCanXxx` trước khi load resource theo id; load trước rồi kiểm sẽ lộ id tồn tại (404 vs 403).
- Cấp tài khoản không có transaction chung Keycloak ↔ DB: giữ thứ tự tạo Keycloak → gán role → ghi DB, lỗi thì xóa user Keycloak. Không đặt `@Transactional` bao quanh lời gọi HTTP.
- Không log request/DTO chứa mật khẩu tạm (`CreateAccountRequest`, `NewIdentityUser` đã che trong `toString`).
- User Keycloak thiếu email/lastName bị chặn đăng nhập nếu realm dùng user profile mặc định (ENV-261004-keycloak-user-profile-account-not-fully-set-up).
- Thêm endpoint mới: dùng `@permissionGuard.has('CODE')`, không `hasRole`; mã phải có trong enum `Permission` (`ControllerSecurityCoverageTest` bắt mã sai). Quyền mới cho role cấu hình được ⇒ migration seed `role_permissions`.
- Quyền ghi hiện dùng chung policy "xem" (vd. duyệt summary dùng `canViewChild`) ⇒ chỉ để `configurableRoles = TEACHER` cho quyền ghi tới khi có policy ghi; cho PHT quyền ghi sẽ ghi được mọi lớp trong campus.
- User nhiều role (GV kiêm phụ huynh): REVOKE chỉ trừ phần ma trận, không trừ phần cố định của PARENT; scope vẫn do policy theo role.
- Test WebMvc dựng `CurrentUser` phải kèm quyền (`TestPermissions.userWithDefaults`); constructor 6 tham số không có quyền nào ⇒ 403.
- `primary_role` là bản sao role Keycloak chỉ ghi khi CareNest tạo tài khoản: đổi role trên Keycloak console làm lệch phạm vi quản lý; tài khoản `primary_role` null chỉ Admin quản lý được.
- Tài khoản phụ huynh chỉ tạo qua `POST /children/{id}/guardians` (username = SĐT, `utils/PhoneNumbers`; mật khẩu `TemporaryPasswordGenerator` trả 1 lần, không log — DTO che `toString`). Khóa/mở, mật khẩu phụ huynh qua `/parent-accounts/...` (`GuardianAccountService`), vòng đời Keycloak dùng chung `AccountLifecycleOperations`.
- PHT chỉ có phạm vi campus: id tài khoản/phân công không tồn tại và ngoài phạm vi phải cùng 403 (`ManagedAccountLoader`); Admin/HT nhận 404.
- Tạo tài khoản: kiểm phạm vi (`StaffAccessPolicy.checkCanCreate`) TRƯỚC khi gọi Keycloak; tài khoản + phân công lưu trong 1 TransactionTemplate, lỗi ⇒ xóa user Keycloak.
- Tên đăng nhập nhân sự không được là SĐT (`PhoneNumbers.normalize` có giá trị ⇒ 400, AUTH-18 sửa 2026-10-28) để không trùng tên đăng nhập phụ huynh. Tạo nhân sự thẳng trên Keycloak console bỏ qua kiểm này; tài khoản nhân sự cũ dạng SĐT vẫn làm `POST /children/{id}/guardians` trả 409. SQL tìm các tài khoản đó trước khi phát hành: `docs/plans/completed/2026-10-28-staff-username-not-phone.md` (Progress log).
- Email phụ huynh chỉ để liên hệ (AUTH-18, 2026-10-28): `POST /children/{id}/guardians` lưu `email` vào `user_accounts.email`, **không** gửi lên Keycloak (email trên Keycloak thành tên đăng nhập thứ hai và bị Keycloak chặn trùng với nhân sự). Quên mật khẩu web (`AuthServiceImpl.forgot`) bỏ qua tài khoản `PARENT`, nếu không thì nhân sự trùng email với phụ huynh mất OTP. Khi làm AUTH-20 (phụ huynh trên hồ sơ trẻ) xét lại chỗ lưu email.

## Related issues
ENV-261004-keycloak-user-profile-account-not-fully-set-up (user profile Keycloak), ENV-261016-flyway-duplicate-version-stale-target (đổi nhánh còn migration cũ trong target).

## Đọc thêm khi
Đổi model quyền/scope ⇒ ADR-0002, ADR-0003, `docs/architecture/SECURITY.md` (mức L4).
