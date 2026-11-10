---
title: Đổi vai trò nhân sự tại chỗ, sửa hồ sơ, vá phạm vi quản lý tài khoản
status: in-progress
owner: thangnd
jira: G94-60 / BE-FEAT-01   # AUTH-25 chưa có task Jira riêng — hỏi user khi commit
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, school-structure]
rules: [AUTH-06, AUTH-11, AUTH-12, AUTH-15, AUTH-16, AUTH-17, AUTH-19, AUTH-23, AUTH-25]
risk: high
created: 2026-11-02
updated: 2026-11-02
---
# Đổi vai trò nhân sự tại chỗ, sửa hồ sơ, vá phạm vi quản lý tài khoản

## Context
Audit 02/11 (sau khi pull `main` 04ae082) luồng tạo tài khoản/phân quyền:
1. Không đổi được vai trò nhân sự (AUTH-25 CONFIRMED, chưa có code): chỉ có `POST /accounts/{id}/staff-role/revoke` (về PARENT, đòi có con đang học — `AccountProvisioningServiceImpl.java:216-220`). Username = SĐT nên không tạo được tài khoản thứ hai ⇒ GV→bếp/PHT bế tắc. Chưa có API sửa họ tên/email ⇒ màn Nhân sự FE (FLOW-006, D4) chưa nối được.
2. Thu hồi vai trò không xóa `user_permission_overrides`; resolver chỉ bỏ qua khi role không khớp (`EffectivePermissionResolver.java:42-51`) ⇒ gán lại vai trò nhân sự làm GRANT cũ (vd `MEAL_COUNT_CONFIRM` SCHOOL) sống lại không ai duyệt.
3. `GET /accounts` của HT trả mọi tài khoản (phụ huynh, Admin, HT khác) vì `roles = null` (`AccountQueryServiceImpl.java:66-69`), trong khi chi tiết các dòng đó 403 (`StaffAccessPolicy.java:99`) ⇒ lệch danh sách/chi tiết, lộ họ tên + SĐT phụ huynh ở màn Nhân sự. AUTH-17: HT xem PHT/GV/bếp.
4. Admin không gộp được tài khoản phụ huynh thành HT: `addStaffRoleToParent` gọi `checkCanManageParent` (`AccountProvisioningServiceImpl.java:266`), Admin không có campus ⇒ 403; AUTH-15 cho phép thêm vai trò vào tài khoản phụ huynh.
5. Phân công PHT/bếp vào tài khoản đang khóa không bị chặn (`StaffingServiceImpl.java:46-61`); GV đã chặn (409 `2305`).

Ngoài phạm vi: audit + báo HT của AUTH-19 (cần bảng audit D12, user chốt làm sau P0/P1).

## Quyết định đã chốt
1. Phạm vi = vá 2–5 + AUTH-25 (phương án B) — user, 2026-11-02.
2. Đổi vai trò: kết thúc mọi phân công đang mở (kể cả tổ trưởng) ở hôm nay, xóa mọi quyền ngoại lệ + ghi `permission_change_log`; request được kèm phân công campus mới cho PHT/bếp (GV không kèm lớp, như AUTH-15). Xóa ngoại lệ áp cả cho thu hồi vai trò — user, 2026-11-02.
3. Người đổi: HT đổi giữa PHT/GV/bếp toàn trường; PHT (có `ACCOUNT_MANAGE`) chỉ GV↔bếp của người đang phân công trong campus mình; không lên/xuống HT qua API (Admin làm trên Keycloak); không tự đổi mình — user, 2026-11-02.
4. Hồ sơ sửa được: họ tên + email, đồng bộ Keycloak; SĐT (= username) không sửa (AUTH-18 hoãn đổi SĐT) — user, 2026-11-02.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT/PHT · đổi vai trò người đang làm mà giữ tài khoản (SĐT, mật khẩu, liên kết con) · AUTH-25 (CONFIRMED chức năng; phạm vi/field chốt 02/11 ở trên), AUTH-12, AUTH-17.
- **Đối chiếu thiết kế ban đầu:** plan `2026-10-17-staff-account-provisioning.md` chọn "không đổi role tại chỗ"; D4 (01/11) đảo lại ⇒ AUTH-25.
- **Ảnh hưởng:** identity-access (service, Keycloak adapter), school-structure (kết thúc/tạo phân công qua `StaffAssignmentService`). FE: màn Nhân sự `/school/staff` (`staffAccountApi.js` — chưa nối, FLOW-006) dùng 2 endpoint mới; APP không dùng. Dữ liệu cũ: ngoại lệ còn sót của tài khoản đã thu hồi vai trò được xóa khi thêm lại vai trò nhân sự.

### Câu hỏi mở
Không còn.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Phạm vi đợt sửa | B: vá + AUTH-25 | user | 2026-11-02 | plan này |
| Phân công/ngoại lệ khi đổi vai trò | Kết thúc + xóa, ghi log | user | 2026-11-02 | AUTH-25 |
| Ai đổi, đổi giữa role nào | HT: PHT/GV/bếp; PHT: GV↔bếp | user | 2026-11-02 | AUTH-25 |
| Field hồ sơ | Họ tên + email | user | 2026-11-02 | AUTH-25 |

## Acceptance criteria
- **AC-1** (`AUTH-25`): Given HT, GV đang dạy lớp có ngoại lệ GRANT · When `PUT /accounts/{id}/staff-role {role: KITCHEN_STAFF, assignment:{campusId}}` · Then Keycloak thêm `KITCHEN_STAFF`, gỡ `TEACHER`; phân công đang mở kết thúc hôm nay; ngoại lệ bị xóa và có log; có phân công bếp mới; `primary_role = KITCHEN_STAFF`; phiên cũ bị hủy.
- **AC-2** (`AUTH-25`, `AUTH-17`): PHT đổi GV→bếp trong campus mình ⇒ 200; PHT đổi sang PHT, hoặc đổi người ngoài campus ⇒ 403; ai cũng không đổi chính mình (400), không đổi sang/từ PRINCIPAL (400), không đổi sang role đang có (400), không đổi tài khoản đang khóa (409).
- **AC-3** (`AUTH-25`): Keycloak lỗi ở bước gỡ role cũ hoặc DB lỗi ⇒ role Keycloak trả về như trước (bù trừ), DB không đổi.
- **AC-4** (`AUTH-25`, `AUTH-19`): `PATCH /accounts/{id}/profile` sửa họ tên/email trên Keycloak rồi DB; email trống ⇒ giữ email cũ; tài khoản phụ huynh-only ⇒ 400; PHT sửa nhân sự kiêm phụ huynh có con ngoài campus mình ⇒ 403 (email dùng cho quên mật khẩu).
- **AC-5** (`AUTH-25`): thu hồi vai trò nhân sự và thêm vai trò nhân sự vào tài khoản phụ huynh đều xóa ngoại lệ cũ, có log.
- **AC-6** (`AUTH-17`): `GET /accounts` của HT chỉ trả PHT/GV/bếp; Admin trả mọi tài khoản.
- **AC-7** (`AUTH-15`): Admin tạo HT bằng SĐT đã là phụ huynh (đã xác nhận) ⇒ thêm vai trò PRINCIPAL thành công.
- **AC-8** (`AUTH-16`): `POST /staff-assignments` cho PHT/bếp đang khóa ⇒ 409 `2326`.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Thứ tự Keycloak khi đổi role | Thêm role mới → gỡ role cũ → DB; lỗi ⇒ bù trừ ngược | Ghi DB trước | Giống `addRealmRole`/`createIdentity`: không gọi HTTP trong transaction; DB lỗi thì Keycloak trả lại như cũ |
| Nơi đặt code | Thêm vào `AccountProvisioningServiceImpl` (cạnh `revokeStaffRole`) | Service mới | Dùng chung helper kết thúc phân công/xóa ngoại lệ với thu hồi; ít file |
| Xóa ngoại lệ | Method mới `PermissionService.clearPermissionOverrides` chạy trong transaction của người gọi | Gọi repository từ service khác | Ghi log cùng chỗ với ngoại lệ (AUTH-14) |
| Email trống khi sửa hồ sơ | Giữ email cũ (PATCH) | Xóa email | Xóa email trên Keycloak user profile không ổn định; chưa có nhu cầu |

## Phases
### Phase 1 — Vá phạm vi (mục 2–5) + AUTH-25
- Files: M `utils/ApiCode.java` · M `integration/keycloak/IdentityProviderClient.java`, `KeycloakIdentityProviderClient.java` · M `entity/account/UserAccount.java` · C `dto/account/ChangeStaffRoleRequest.java`, `UpdateAccountProfileRequest.java` · M `service/account/AccountProvisioningService.java`, `impl/AccountProvisioningServiceImpl.java`, `impl/AccountLifecycleOperations.java`, `PermissionService.java`, `impl/PermissionServiceImpl.java`, `impl/AccountQueryServiceImpl.java`, `impl/StaffingServiceImpl.java` · M `controller/account/AccountController.java` · M test tương ứng · M `docs/api/openapi.yaml` (snapshot).
- Tests: AC-1..5 → `AccountProvisioningServiceImplTest`; AC-6 → `AccountQueryServiceImplTest`; AC-8 → `StaffingServiceImplTest`; Keycloak → `KeycloakIdentityProviderClientTest`; controller → `AccountControllerTest`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Bù trừ Keycloak lỗi khi đổi role | Tài khoản mang 2 role nhân sự hoặc mất role | Log `manualCleanupRequired`; scope vẫn theo phân công + `primary_role` | Admin sửa role trên Keycloak console |
| PHT đổi GV đang là tổ trưởng ⇒ phân công tổ trưởng kết thúc | Nhóm tuổi mất tổ trưởng | Là hệ quả đổi vai trò (tổ trưởng phải là GV, AUTH-23); HT phân công lại | — |
| Lọc `GET /accounts` của HT | Màn nào của HT dùng danh sách này để tìm phụ huynh sẽ trống | FE chỉ dùng với `role` cấu hình được (`permissionApi.js:24`) | Revert commit |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: FE `/school/staff` (chưa nối), `permissionApi.js:24` (không đổi hành vi)
- [x] Mỗi AC có test; phase có Exit
- [x] >8 file: cùng 1 luồng tài khoản, tách plan không giảm rủi ro ⇒ giữ 1 phase
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review

## Progress log
### 2026-11-02 — Phase 1 (done, chờ user review)
- Pull `origin/main` (fast-forward 04ae082). Audit + 4 quyết định user ở trên. Chế độ tự chạy (CLAUDE.md): không chờ duyệt plan.
- Commit: chưa commit (AUTH-25 chưa có mã Jira riêng).
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 663 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; `node scripts/check-ai-layer.mjs` → 0 lỗi (5 cảnh báo E2 có sẵn ở plan khác).
- Test: `AccountProvisioningServiceImplTest` (AC-1..5, AC-7), `AccountQueryServiceImplTest#principalListIsLimitedToStaffRoles` (AC-6), `StaffingServiceImplTest#lockedKitchenAccountCannotBeAssigned` (AC-8), `KeycloakIdentityProviderClientTest#updatingProfile*`, `AccountControllerTest#principalChangesStaffRole|principalUpdatesStaffProfile`.
- Khác plan: endpoint sửa hồ sơ là `PATCH /accounts/{id}/profile` (email trống = giữ ⇒ ngữ nghĩa PATCH); đổi vai trò `PUT /accounts/{id}/staff-role`. Thêm kiểm `checkCanManageLinkedParent` khi sửa hồ sơ (email dùng cho quên mật khẩu ⇒ ngang cấp lại mật khẩu).
- Giả định tự chọn: PHT đổi GV đang là Tổ trưởng ⇒ phân công Tổ trưởng kết thúc theo (hệ quả, không chặn); Admin không đổi vai trò nhân sự qua API (AUTH-06: việc nghiệp vụ của HT); bù trừ hồ sơ khi email cũ trống không xóa được email mới trên Keycloak (chỉ log).
- Docs: AUTH-25 (BUSINESS_RULES), `AUTH_CONTRACT.md`, `identity-access.md`, `CROSS_REPO_MAP.md`, snapshot `openapi.yaml` (+2 endpoint, không bỏ gì).
- Tiếp: FE màn Nhân sự nối 2 endpoint (FLOW-006); AUTH-19 audit chờ D12.
