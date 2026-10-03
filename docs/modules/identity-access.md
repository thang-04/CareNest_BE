# Module — identity-access

Feature: `account` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

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
Chưa có. Auth mechanism (session/JWT, refresh) chưa chốt — `docs/contracts/AUTH_CONTRACT.md` là SKELETON.

## PENDING
P-14 (scope Hiệu phó), P-16 (Admin xem dữ liệu trẻ).

## Known pitfalls
- Kiểm tra quyền chỉ ở UI/controller annotation mà quên lọc scope trong query ⇒ lộ dữ liệu campus khác. Scope phải áp ở service/query.
- Không hard-code role→campus; dùng assignment có hiệu lực theo thời gian.

## Related issues
Chưa có.

## Đọc thêm khi
Đổi model quyền/scope ⇒ ADR-0002, ADR-0003, `docs/architecture/SECURITY.md` (mức L4).
