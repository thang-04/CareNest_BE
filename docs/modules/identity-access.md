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
- API: `GET /accounts/me`.
- Code: `security/CurrentUser`, `CurrentUserService` (JWT `sub` → `user_accounts`, chặn chưa cấp/INACTIVE), `CurrentUserArgumentResolver`; `controller|service/account/`.

## PENDING
P-14 (scope Hiệu phó), P-16 (Admin xem dữ liệu trẻ).

## Known pitfalls
- Kiểm tra quyền chỉ ở UI/controller annotation mà quên lọc scope trong query ⇒ lộ dữ liệu campus khác. Scope phải áp ở service/query.
- Không hard-code role→campus; dùng assignment có hiệu lực theo thời gian.
- Service không tự đọc `SecurityContextHolder`/`Jwt`; nhận `CurrentUser` từ controller.
- User Keycloak thiếu email/lastName bị chặn đăng nhập nếu realm dùng user profile mặc định (ENV-001).

## Related issues
ENV-001 (user profile Keycloak).

## Đọc thêm khi
Đổi model quyền/scope ⇒ ADR-0002, ADR-0003, `docs/architecture/SECURITY.md` (mức L4).
