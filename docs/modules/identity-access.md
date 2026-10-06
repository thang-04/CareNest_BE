# Module — identity-access

Feature: `account` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (ADR-0011)

## Mục đích
Đăng nhập, tài khoản, role, permission; cung cấp kiểm tra access scope cho mọi module.

## Owns
UserAccount, Role, Permission, role↔permission; kiểm tra access scope (`AccessScopeService` — tên minh họa), `CurrentUser`.

## KHÔNG owns
StaffAssignment (school-structure), GuardianChildLink (child), dữ liệu nghiệp vụ.

## Rules
AUTH-01..08 (`docs/business/BUSINESS_RULES.md`). Role/permission: `docs/business/USER_ROLES.md`.

## Phụ thuộc
- Dùng: common/utils.
- Được dùng bởi: mọi module domain (check permission + scope).
- Kiểm tra scope (tên minh họa trong guide: `AccessScopeService`) đặt ở `security/` hoặc service dùng chung, đọc assignment qua service `organization` và liên kết phụ huynh qua service `child`; các service đó không gọi ngược lại để tránh vòng.

## API & bảng
- Auth: Keycloak + Resource Server (ADR-0011, `docs/contracts/AUTH_CONTRACT.md`, `docs/system/KEYCLOAK.md`). Role toàn cục ở Keycloak; không có bảng Role/Permission trong DB.
- Bảng: `user_accounts` (`docs/database/DATA_DICTIONARY.md`).
- API cấp/khóa tài khoản (ADMIN): `POST /accounts`, `PATCH /accounts/{userId}/status` — `AccountProvisioningService` → `integration/keycloak/IdentityProviderClient` (adapter `KeycloakIdentityProviderClient`, RestClient + service account, token cache) với bù trừ khi lỗi.
- API: `GET /accounts/me` — profile, roles, tóm tắt phạm vi (phân công đang hiệu lực khớp role trong token, id trẻ liên kết). `AccountService` đọc `StaffAssignmentService`/`GuardianChildLinkService` (như `security/authorization`).
- Code: `security/CurrentUser`, `CurrentUserService` (JWT `sub` → `user_accounts`, chặn chưa cấp/INACTIVE), `CurrentUserArgumentResolver`; `controller|service/account/`.
- Kiểm tra scope: `security/authorization/` — `CampusAccessPolicy`, `ClassroomAccessPolicy`, `ChildAccessPolicy` (role trong token ∧ phân công/liên kết còn hiệu lực; bảng quy tắc ở `docs/architecture/SECURITY.md`). Đây là tên thật thay cho `AccessScopeService` (tên minh họa trong docs cũ).

## PENDING
P-14 (scope Hiệu phó), P-16 (Admin xem dữ liệu trẻ).

## Known pitfalls
- Kiểm tra quyền chỉ ở UI/controller annotation mà quên lọc scope trong query ⇒ lộ dữ liệu campus khác. Scope phải áp ở service/query.
- Không hard-code role→campus; dùng assignment có hiệu lực theo thời gian.
- Service không tự đọc `SecurityContextHolder`/`Jwt`; nhận `CurrentUser` từ controller.
- Chặn tài khoản chưa cấp/INACTIVE nằm ở `ActiveAccountInterceptor` (mọi `/api/**`), không dựa vào việc controller có tham số `CurrentUser`. WebMvcTest có token phải `@MockitoBean CurrentUserService`.
- Lỗi phía Keycloak khi cấp tài khoản: 409 trùng, 400 bị từ chối (policy mật khẩu, ký tự họ tên), 503 không khả dụng; mọi lỗi sau khi đã tạo user Keycloak đều phải bù trừ (kể cả lỗi DB bất kỳ).
- Gọi `checkCanXxx` trước khi load resource theo id; load trước rồi kiểm sẽ lộ id tồn tại (404 vs 403).
- Cấp tài khoản không có transaction chung Keycloak ↔ DB: giữ thứ tự tạo Keycloak → gán role → ghi DB, lỗi thì xóa user Keycloak. Không đặt `@Transactional` bao quanh lời gọi HTTP.
- Không log request/DTO chứa mật khẩu tạm (`CreateAccountRequest`, `NewIdentityUser` đã che trong `toString`).
- User Keycloak thiếu email/lastName bị chặn đăng nhập nếu realm dùng user profile mặc định (ENV-001).

## Related issues
ENV-001 (user profile Keycloak).

## Đọc thêm khi
Đổi model quyền/scope ⇒ ADR-0002, ADR-0003, `docs/architecture/SECURITY.md` (mức L4).
