---
title: Phân quyền cấu hình được (phần A) — nền cho cấp tài khoản nhân sự/phụ huynh
status: in-progress
owner: thangnd
jira: G94-60 / BE-FEAT-01
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access]
rules: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-09, AUTH-10, AUTH-11, AUTH-12, AUTH-13, AUTH-14]
risk: high
created: 2026-10-16
updated: 2026-10-16
---
# Phân quyền cấu hình được (phần A)

Luồng gồm 3 phần, mỗi phần một plan: **A** phân quyền cấu hình được (plan này) → **B** cấp tài khoản nhân sự + phân công → **C** tài khoản phụ huynh + liên kết trẻ. Phác thảo B/C ở cuối, plan riêng khi A xong.

## Context
- Quyền hard-code `@PreAuthorize("hasRole(...)")` (`AccountController.java:49,63`, `ChildController.java:28`); không có bảng permission (`docs/modules/identity-access.md:23`) trong khi ADR-0003 yêu cầu permission theo role cấu hình được.
- FE có màn ma trận quyền / gán PHT / kích hoạt phụ huynh nhưng mock (`CareNest_FE/src/models/school-config/permissionCatalog.js`); sidebar cố định theo role.
- Nhánh này chỉ có controller Account/Auth/Password/Child; observation/summary/AI-document nằm ở nhánh G94-200 (chưa merge) ⇒ quyền của chúng do nhánh đó tự thêm vào enum khi merge.

## Quyết định đã chốt
1. HT cấp tài khoản + phân quyền nghiệp vụ; Admin giữ kỹ thuật (tạo PRINCIPAL, khóa/mở, sự cố) — Team chốt 2026-10-16.
2. Quyền hiệu lực = phần cố định của role (ADMIN/PRINCIPAL/PARENT, trong code) ∪ (ma trận role cấu hình ∪ GRANT − REVOKE của user); scope campus/lớp/trẻ luôn áp dụng — user, 2026-10-16.
3. Danh mục chỉ gồm quyền BE thực sự kiểm tra; module mới tự thêm quyền — user, 2026-10-16.
4. Kiểm tra bằng `@PreAuthorize("@permissionGuard.has('X')")`, tính 1 lần/request từ DB, có hiệu lực ngay; không đưa quyền vào JWT — user, 2026-10-16.
5. Chống leo quyền: `PERMISSION_MANAGE` chỉ PRINCIPAL, không giao được; ma trận chỉ cho VICE_PRINCIPAL/TEACHER/KITCHEN_STAFF; không tự sửa quyền/trạng thái/role của mình; PHT chỉ tạo TEACHER/KITCHEN_STAFF/PARENT trong campus mình, PRINCIPAL chỉ Admin tạo (phần B) — user, 2026-10-16.
6. Lịch sử đầy đủ `permission_change_log` + UI xem — user, 2026-10-16.
7. Phụ huynh: username = SĐT chuẩn hóa + mật khẩu tạm, tạo cùng liên kết trẻ, không SMS (phần C) — user, 2026-10-16.
8. Migration phân quyền dùng V7; nhánh G94-200 đổi V7–V9 (chưa commit) thành V8–V10 trước khi merge — user, 2026-10-16.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor HT · outcome: bật/tắt quyền theo role, ngoại lệ từng người, xem lịch sử · rule AUTH-06 (sửa), AUTH-10..14 (mới, CONFIRMED team chốt 2026-10-16).
- **Đối chiếu:** `USER_ROLES.md` ghi HT "không quản trị tài khoản kỹ thuật"; AUTH-06 PROPOSED giao Admin; plan `2026-10-04-keycloak-auth.md:237` "chỉ ADMIN cấp tài khoản" ⇒ thay bằng quyết định 1.
- **Ảnh hưởng:** identity-access · mọi endpoint nghiệp vụ chuyển sang guard · `GET /accounts/me` thêm `permissions` (FE: sidebar/route guard; APP: chưa dùng) · dữ liệu cũ: seed giữ đúng hành vi `hasRole`.

### Câu hỏi mở
| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Ai cấp tài khoản/phân quyền | HT; Admin kỹ thuật | user (team chốt) | 2026-10-16 | AUTH-06, USER_ROLES, ADR-0017 |
| Mức phân quyền | Ma trận role + ngoại lệ GRANT/REVOKE | user | 2026-10-16 | AUTH-10 |
| Nhánh/Jira | G94-60 / BE-FEAT-01 | user | 2026-10-16 | plan này |

## Acceptance criteria
- **AC-1** (`AUTH-10`): Given HT tắt `CHILD_PROFILE_VIEW` của TEACHER, When GV gọi `GET /children/{id}`, Then 403 ở request kế tiếp (không cần đăng nhập lại).
- **AC-2** (`AUTH-10`): Given GV có REVOKE `CHILD_PROFILE_VIEW`, When gọi `GET /children/{id}`, Then 403; GV khác vẫn 200.
- **AC-3** (`AUTH-11`): Given PHT/GV/ADMIN, When gọi bất kỳ endpoint quản lý quyền, Then 403.
- **AC-4** (`AUTH-11`): Given HT, When `PUT /role-permissions/PRINCIPAL|PARENT|ADMIN`, Then 403; When gán quyền không cấu hình được cho role, Then 400.
- **AC-5** (`AUTH-12`): Given HT, When sửa ngoại lệ của chính mình, Then 400; When đích là PRINCIPAL/ADMIN hoặc không có role cấu hình được, Then 403.
- **AC-6** (`AUTH-14`): Given HT đổi ma trận/ngoại lệ, Then mỗi quyền đổi có 1 dòng lịch sử (trước/sau, người, lý do); `GET /permission-change-logs` trả phân trang.
- **AC-7** (`AUTH-13`): REVOKE không trừ phần cố định của role; GRANT ngoài `configurableRoles` bị bỏ qua; KITCHEN_STAFF không bao giờ có quyền dữ liệu trẻ.
- **AC-8** (`AUTH-07`): Seed V7 cho ra đúng tập role của `hasRole` cũ trên mọi endpoint; mọi code trong SpEL tồn tại trong enum.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Cơ chế kiểm tra | Bean guard đọc `CurrentUser.permissions` | Authority trong JWT converter; client role Keycloak | Hiệu lực ngay, không phụ thuộc token, dễ test (ADR-0017) |
| Nơi tính quyền | `CurrentUserService` đọc repo + `EffectivePermissionResolver` thuần | Service riêng gọi mỗi request | Đúng ranh giới ArchUnit (security đọc repo), không log thừa mỗi request |
| Role của user đích | Keycloak `role-mappings/realm` (view-users) | Cột role trong DB | Keycloak là nguồn role (ADR-0011) |
| Lưu code quyền | Chuỗi, bỏ qua code lạ khi đọc | `@Enumerated` | Xóa code khỏi enum không làm vỡ load entity |

## Phases
### Phase 1 — Hệ phân quyền BE
- Files: C `db/migration/V7__create_permissions.sql` · C `utils/enums/{Permission,PermissionEffect,PermissionState}` · C `entity/account/{RolePermission,UserPermissionOverride,PermissionChangeLog}` · C `repository/account/*` · C `security/authorization/{EffectivePermissionResolver,PermissionGuard}` · C `service/account/PermissionService` + impl · C `controller/account/PermissionController` · C `dto/account/*Permission*` · C `mapper/account/PermissionMapper` · M `CurrentUser`, `CurrentUserService`, `SecurityConfig`, `AccountController`, `ChildController`, `AccountServiceImpl`, `MyAccountResponse`, `IdentityProviderClient`(+adapter).
- Tests: AC-1..8 (xem Test matrix).
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.
### Phase 2 — Docs + snapshot
- Files: M `BUSINESS_RULES.md`, `USER_ROLES.md`, `identity-access.md`, `AUTH_CONTRACT.md`, `SECURITY.md`, `DATA_DICTIONARY.md`, `CROSS_REPO_MAP.md`, `openapi.yaml` · C ADR-0017.
- Exit: `node scripts/check-ai-layer.mjs` 0 lỗi; `node scripts/verify.mjs` PASS.
### Phase 3 — FE (repo CareNest_FE, cần user cho phép trong lượt đó)
- `permissions` trong auth profile, `hasPermission`, PermissionGuard route, menu theo quyền, màn ma trận/ngoại lệ/lịch sử dùng API thật.
- **Dừng chờ user review sau mỗi phase.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2 | AUTH-10 | `ChildControllerTest`, `CurrentUserServiceTest` (WebMvc/unit) | 1 | |
| AC-3 | AUTH-11 | `PermissionControllerTest` (WebMvc) | 1 | |
| AC-4, AC-5, AC-6 | AUTH-11/12/14 | `PermissionServiceImplTest` (unit) | 1 | |
| AC-7 | AUTH-13 | `EffectivePermissionResolverTest`, `PermissionTest` (unit) | 1 | |
| AC-8 | AUTH-07 | `PermissionSchemaIntegrationTest` (Testcontainers), `ControllerSecurityCoverageTest` | 1 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Quyền ghi đang dựa policy "xem" (nhánh G94-200) | Cấp quyền ghi cho PHT ⇒ ghi mọi lớp trong campus | Quyền ghi chỉ `configurableRoles = TEACHER` tới khi có policy ghi | Bỏ role khỏi `configurableRoles` |
| User nhiều role (GV + phụ huynh) | REVOKE không trừ phần PARENT cố định; policy scope theo role | Ghi pitfall; scope vẫn chặn trẻ ngoài liên kết/lớp | — |
| Thêm query mỗi request | Chậm nhẹ | Chỉ query khi user có role cấu hình; có index | — |
| Đụng số migration với G94-200 | Flyway lỗi | Quyết định 8 | Đổi số trước merge |
| Seed lệch hành vi cũ | Mất/mở quyền ngoài ý muốn | AC-8 | Revert migration bằng migration mới |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer: FE `src/services/authProfile.js` (`/accounts/me`), FE `RolePermissionListPage/FormPage` (endpoint mới); APP chưa đọc `permissions`
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >8 file ⇒ đã tách A/B/C thành plan riêng; phase 1 vẫn nhiều file do một khối nguyên tử (guard + bảng + seed)
- [x] Không dùng rule PENDING/OPEN như đã chốt (P-14, P-16 giữ nguyên)
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan

## Phác thảo phần B / C (plan riêng)
- **B:** `POST /accounts`, `PATCH /accounts/{id}/status` mở theo `ACCOUNT_MANAGE`/`ACCOUNT_STATUS_MANAGE` + kiểm tra role được tạo (ADMIN→PRINCIPAL; HT→VP/T/Bếp/PH; PHT→T/Bếp/PH trong campus); `GET /accounts`; `POST /staff-assignments`, `PATCH /staff-assignments/{id}/end`; cấp lại mật khẩu tạm; FE màn Tài khoản nhân sự, gán PHT, phân công GV.
- **C:** `POST /children/{childId}/guardians` (tạo/ghép tài khoản PARENT theo SĐT + `guardian_child_links`, bù trừ Keycloak); `POST /accounts/{id}/temporary-password`; FE làm lại `ParentActivationPage` (bỏ SMS) + phiếu in.

## Progress log
### 2026-10-16 — Plan duyệt
- User duyệt plan (phiên brainstorming 2026-10-16); stash việc G94-200, chuyển nhánh G94-60, merge `origin/main` (`5b30dfa`).
- Khác plan: danh mục co lại còn quyền của Account/Child do nhánh này chưa có observation/summary/AI-document; migration V7 thay vì V10 (quyết định 8); `role_permissions` dùng khóa `id` + UNIQUE(role, permission_code) thay PK ghép (đơn giản JPA).

### 2026-10-16 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 244 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`
- Snapshot `docs/api/openapi.yaml` cập nhật có chủ đích (+480 dòng, chỉ thêm): endpoint quyền mới + `permissions` trong `/accounts/me`.
- Khác plan: guard đăng ký bằng `@Bean` trong `SecurityConfig` (không `@Component`) để mọi WebMvcTest có sẵn bean; quyền tính trong `CurrentUserService` (repo + resolver thuần) thay vì gọi `PermissionService` mỗi request — chỉ query khi user có role cấu hình được; ngoại lệ chỉ chặn đích có PRINCIPAL/ADMIN hoặc không có role cấu hình (GV kiêm phụ huynh vẫn có ngoại lệ được).
- Gặp ENV-261016-flyway-duplicate-version-stale-target (target cũ của G94-200) ⇒ `mvnw clean`.
- Tiếp: Phase 2 docs (AUTH-06 sửa, AUTH-10..14, ADR-0017, USER_ROLES, identity-access, AUTH_CONTRACT, SECURITY, DATA_DICTIONARY, CROSS_REPO_MAP) sau khi user review.

### 2026-10-16 — Phase 2 (chờ review)
- User duyệt phase 1 ("tiếp phase 2"). Commit: chưa commit.
- Docs: `BUSINESS_RULES` (AUTH-06 CONFIRMED phân vai HT/Admin, P-16 giữ; AUTH-10..14 mới), ADR-0017, `USER_ROLES` (Principal/Admin, bảng permission thật), `identity-access` (API, bảng, pitfalls), `AUTH_CONTRACT` (`permissions`, mục API phân quyền + lỗi), `SECURITY`, `DATA_DICTIONARY` + `ERD` (3 bảng V7), `CROSS_REPO_MAP`, `CURRENT_STATE`, `KNOWN_ISSUES` KI-003, `.ai/CONTEXT_MAP.yaml`, `.claude/rules/security.md`; gạch giả định "chỉ ADMIN cấp tài khoản" trong plan `2026-10-04-keycloak-auth.md`.
- FE còn tham chiếu cũ (không sửa — chưa được phép): `CareNest_FE/docs/architecture/ROUTE_MAP.md:13-23` (màn Tài khoản/Role & permission/Phân công dưới System Admin + `admin:accounts`), `CareNest_FE/docs/context/REPOSITORY_CONTEXT.md:21`, `CareNest_FE/docs/context/CURRENT_STATE.md:19`.
- Tiếp: Phase 3 FE khi user cho phép sửa `CareNest_FE`; sau đó plan phần B.

### 2026-10-17 — Phase 3 FE (chờ review)
- User cho phép sửa `CareNest_FE`, chọn làm thẳng trên nhánh FE hiện tại `feature/FE-FEAT-48-nutrition-management` (đang có WIP auth + nutrition chưa commit), không commit.
- Code FE: `models/account/permissionConstants.js`, `utils/account/permission{Validation,ManagementPermissions}.js`, `services/account/{permissionService.js, api/permissionApi.js, mock/permissionMockRepository.js}` (mock chép đúng luật BE), `hooks/account/usePermissions.js`, `routes/PermissionGuard.jsx`; `authProfile` thêm `permissions`, `AuthContext.hasPermission`, mock auth gắn quyền hiệu lực; menu mục có `permission` tự ẩn (`menuConfig`, `Sidebar`, `HeaderSearch`); trang `/school/roles` (ma trận), `/school/roles/:role`, `/school/roles/users[/:userId]`, `/school/roles/history`.
- Gỡ mock F/R/N cũ: `permissionCatalog.js` (bản WIP lưu ở scratchpad phiên), get/saveRolePermissions trong school-config service/api/mock/hook/seed, `PermissionLevelBadge`, hằng PERMISSION_LEVEL/MODULES/ROLE_SCOPE_NOTES/CONFIG_ROLE*, `canManageRolePermissions`.
- Docs FE: `ROUTE_MAP.md`, `REPOSITORY_CONTEXT.md`, `CURRENT_STATE.md`, `integration/AUTH_FLOW.md`.
- Verify FE: `node scripts/verify.mjs` → `VERIFY PASS full`; `tests/auth/permissions.test.mjs` 5/5 pass; `check-ai-layer` FE 0 lỗi (4 cảnh báo có sẵn).
- Giới hạn: chưa chạy UI trên trình duyệt; chưa thử FE↔BE thật (`VITE_USE_MOCK=false`). Chọn nhân sự ở màn ngoại lệ lấy master data (mock) — chế độ API thật nhập mã tài khoản tới khi có `GET /accounts` (phần B).
- Tiếp: user review; plan phần B.
