---
title: Cấp tài khoản nhân sự + phân công (phần B của phân quyền)
status: in-progress
owner: thangnd
jira: G94-60 / BE-FEAT-01
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, school-structure]
rules: [AUTH-01, AUTH-02, AUTH-03, AUTH-05, AUTH-06, AUTH-09, AUTH-10, AUTH-11, AUTH-12, AUTH-13]
risk: high
created: 2026-10-17
updated: 2026-10-17
---
# Cấp tài khoản nhân sự + phân công (phần B)

Phần A (phân quyền cấu hình được) xong ở `2026-10-16-permission-management.md`. Phần C (tài khoản phụ huynh) plan riêng.

## Context
- `POST /accounts`, `PATCH /accounts/{id}/status` chỉ ADMIN (`Permission.ACCOUNT_MANAGE/ACCOUNT_STATUS_MANAGE` default ADMIN — `utils/enums/Permission.java`); không có API danh sách tài khoản, phân công, campus/lớp (`docs/modules/school-structure.md` "Chưa có API").
- Tài khoản mới chưa có phân công ⇒ không thuộc campus nào; PRINCIPAL không có phân công PRINCIPAL thì `CampusAccessPolicy` từ chối mọi campus.
- `user_accounts` không có cột role (`V1` comment) ⇒ không lọc danh sách theo role được.

## Quyết định đã chốt
1. Admin tạo PRINCIPAL; HT tạo PHT/GV/bếp; PHT có `ACCOUNT_MANAGE` (HT bật) chỉ tạo GV/bếp trong campus mình — team chốt 2026-10-16 (AUTH-06).
2. Tạo tài khoản **1 bước kèm phân công**: bắt buộc khi người tạo không phải HT/Admin; HT được bỏ trống; tài khoản PRINCIPAL tự có phân công PRINCIPAL — user 2026-10-17.
3. Phân công: HT toàn trường (PHT/bếp→campus, GV→lớp); PHT có `STAFF_ASSIGNMENT_MANAGE` phân công GV/bếp trong campus mình, không phân công PHT — user 2026-10-17.
4. Có khóa/mở, cấp lại mật khẩu tạm, danh sách + tìm kiếm tài khoản; PHT (nếu được bật) chỉ với GV/bếp campus mình; không ai thao tác HT/Admin (trừ Admin) hay chính mình — user 2026-10-17.
5. Thêm API đọc tối thiểu `GET /campuses`, `GET /classrooms?campusId=` (theo phạm vi); không làm tạo/sửa campus/lớp — user 2026-10-17.

**Mặc định agent chọn (đổi được khi duyệt):**
- a. Cột `user_accounts.primary_role` (bản sao role Keycloak để lọc/hiển thị/kiểm phạm vi; quyết định quyền vẫn theo token) — đổi comment V1 bằng migration mới, ghi vào ADR-0017.
- b. Cấp lại mật khẩu tạm dùng quyền `ACCOUNT_MANAGE`; khóa/mở dùng `ACCOUNT_STATUS_MANAGE`.
- c. `POST /accounts` role PARENT giữ cho ADMIN tới khi phần C thay bằng luồng gắn trẻ.
- d. Không đổi role của tài khoản đã có (cần thì tạo tài khoản mới / Admin sửa trên Keycloak).
- e. FE: thêm màn "Nhân sự" mới; màn mock cũ "Phân công PHT/GV" (theo năm học) giữ nguyên, chuyển sang API sau.

## Làm rõ nghiệp vụ
- **Actor:** HT, PHT (theo quyền), Admin (PRINCIPAL). **Outcome:** tạo tài khoản đăng nhập được ngay đúng phạm vi.
- **Đối chiếu:** P-14 (PHT cố định/luân phiên) vẫn PENDING — mô hình cho phép 1 PHT nhiều phân công campus nên không chặn. P-06 (bếp theo campus) PENDING — bếp vẫn phân công theo campus như V2.
- **Ảnh hưởng:** identity-access + school-structure; FE: màn Nhân sự, màn Ngoại lệ quyền dùng `GET /accounts`; APP: không.

### Câu hỏi mở
| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Tạo + phân công | 1 bước kèm phân công | user | 2026-10-17 | AUTH-15 (mới) |
| Ai phân công | HT toàn trường, PHT theo quyền trong campus | user | 2026-10-17 | AUTH-16 (mới) |
| Quản lý tài khoản | Khóa/mở, mật khẩu tạm, danh sách | user | 2026-10-17 | AUTH-17 (mới) |
| Campus/lớp | API đọc tối thiểu | user | 2026-10-17 | school-structure card |

## Acceptance criteria
- **AC-1** (`AUTH-06`): HT tạo GV kèm lớp ⇒ 201, tài khoản đăng nhập thấy đúng lớp; Admin tạo GV ⇒ 403; HT tạo PRINCIPAL ⇒ 403; Admin tạo PRINCIPAL ⇒ có phân công PRINCIPAL.
- **AC-2** (`AUTH-15`): PHT có `ACCOUNT_MANAGE` tạo GV lớp campus mình ⇒ 201; lớp campus khác / thiếu phân công / tạo PHT ⇒ 403/400; PHT không có quyền ⇒ 403.
- **AC-3** (`AUTH-16`): PHT có `STAFF_ASSIGNMENT_MANAGE` gán/kết thúc GV, bếp trong campus mình ⇒ OK; campus khác, phân công PHT, tự phân công mình ⇒ 403/400; phân công trùng đang hiệu lực ⇒ 409.
- **AC-4** (`AUTH-17`, `AUTH-12`): HT khóa/mở, cấp lại mật khẩu tạm cho GV ⇒ OK, phiên cũ bị hủy; với HT khác/Admin/chính mình ⇒ 403/400; PHT chỉ GV/bếp campus mình.
- **AC-5** (`AUTH-08`): `GET /accounts` HT/Admin thấy tất cả; PHT chỉ nhân sự có phân công đang hiệu lực trong campus mình; lọc role/campus/trạng thái/từ khóa, phân trang.
- **AC-6** (`AUTH-02`): `GET /campuses`, `GET /classrooms?campusId=` — PHT chỉ campus được phân công; lớp của năm học đang diễn ra.
- **AC-7**: Keycloak lỗi sau khi tạo user ⇒ xóa user Keycloak, không còn dòng DB (cả phân công).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lọc tài khoản theo role | Cột `primary_role` bản sao | Hỏi Keycloak mỗi lần liệt kê | Phân trang/lọc ở DB; Keycloak vẫn là nguồn role của token |
| Ghi tài khoản + phân công | 1 transaction DB (TransactionTemplate) sau khi tạo Keycloak, lỗi ⇒ bù trừ | 2 request | Không để tài khoản PHT tạo ra mà ngoài campus |
| Quyền mới | `STAFF_ASSIGNMENT_MANAGE` (P cố định; VP cấu hình, seed bật); `ACCOUNT_MANAGE`, `ACCOUNT_STATUS_MANAGE` thêm P cố định, VP cấu hình (seed tắt) | Quyền riêng cho từng role đích | Ít quyền, phạm vi do role người làm quyết |

## Phases
### Phase 1 — BE
- Files: C `V8__account_primary_role_and_staff_permissions.sql` (cột + backfill từ `staff_assignments`/`guardian_child_links` + seed VP `STAFF_ASSIGNMENT_MANAGE`) · M `Permission`, `UserAccount`, `CreateAccountRequest` (+`assignment`), `AccountProvisioningServiceImpl`, `AccountController`, `IdentityProviderClient`(+`resetTemporaryPassword`) · C `StaffAssignmentController`/`OrganizationController` (`/campuses`, `/classrooms`, `/staff-assignments`), `StaffAssignmentService` (+ tạo/kết thúc/liệt kê), `AccountQueryService` (danh sách/chi tiết), DTO + repository query.
- Steps: 1. migration + enum + entity; 2. quy tắc người tạo → role được tạo → phạm vi (helper dùng chung cho tạo/khóa/mật khẩu/phân công); 3. endpoint; 4. test.
- Tests: AC-1..7 → `AccountProvisioningServiceImplTest`, `StaffAssignmentServiceImplTest`, `AccountQueryServiceImplTest`, WebMvc từng controller, Testcontainers V8 backfill + truy vấn phạm vi; cập nhật `ControllerSecurityCoverageTest` (đổi có chủ đích: PRINCIPAL được `POST /accounts`).
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0. **Dừng chờ review.**
### Phase 2 — Docs
- `BUSINESS_RULES` (AUTH-15..17), ADR-0017 (bổ sung `primary_role`), `identity-access`, `school-structure`, `AUTH_CONTRACT`, `USER_ROLES`, `DATA_DICTIONARY`/`ERD`, `CROSS_REPO_MAP`, `CURRENT_STATE`, snapshot OpenAPI. Exit: check-ai-layer 0 lỗi + verify PASS.
### Phase 3 — FE (nhánh FE hiện tại, không commit)
- Màn `/school/staff` (danh sách, tạo kèm phân công, chi tiết: phân công, khóa/mở, mật khẩu tạm); `permissionService`… dùng `GET /accounts` cho màn ngoại lệ; mock chép luật BE; test; docs ROUTE_MAP. Exit: FE `verify.mjs` PASS.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2, AC-7 | AUTH-06, AUTH-15 | `AccountProvisioningServiceImplTest`, `AccountControllerTest` | 1 | |
| AC-3 | AUTH-16 | `StaffAssignmentServiceImplTest`, controller WebMvc | 1 | |
| AC-4 | AUTH-17, AUTH-12 | `AccountProvisioningServiceImplTest`, `KeycloakIdentityProviderClientTest` | 1 | |
| AC-5, AC-6 | AUTH-08, AUTH-02 | `AccountQueryServiceImplTest`, Testcontainers | 1 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| `primary_role` lệch Keycloak (sửa tay trên console) | Lọc/phạm vi sai | Chỉ đổi role qua quy trình (d); ghi pitfall | Migration mới sửa dữ liệu |
| PRINCIPAL được tạo tài khoản (đổi hành vi) | Mở API cho HT | Đúng AUTH-06; test tham số endpoint × role cập nhật có chủ đích | Bỏ PRINCIPAL khỏi `defaultRoles` |
| Bù trừ Keycloak thất bại | User Keycloak mồ côi | Giữ log `manualCleanupRequired` như hiện tại | Dọn tay |

## Validation log
- [x] Khẳng định code có `file:line` (Context)
- [x] Consumer: FE `PermissionOverridePage` (chọn nhân sự), màn Nhân sự mới; APP không
- [x] Mỗi AC có test; phase có Exit
- [x] Tách khỏi phần A/C; phase 1 lớn nhưng nguyên khối
- [x] P-06, P-14 không dùng như đã chốt
- [x] User duyệt plan + mặc định a–e (2026-10-17)

## Progress log
### 2026-10-17 — Plan duyệt
- User duyệt plan và mặc định a–e ("ok"). Bắt đầu phase 1.
### 2026-10-17 — Phase 1 BE (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 283 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`
- Code: V8 (`primary_role` + backfill + seed VP `STAFF_ASSIGNMENT_MANAGE`), seed dev gán `primary_role`; `Permission` (HT cố định `ACCOUNT_MANAGE`/`ACCOUNT_STATUS_MANAGE`/`STAFF_ASSIGNMENT_MANAGE`, PHT cấu hình được); `StaffAccessPolicy`; `Campus` entity; `StaffAssignmentService` (+tạo/kết thúc/liệt kê/resolve phạm vi); `SchoolStructureService`; `AccountQueryService`, `StaffingService`, `ManagedAccountLoader`; `AccountProvisioningServiceImpl` (tạo kèm phân công trong 1 transaction, HT tự có phân công PRINCIPAL, kiểm phạm vi khóa/mở, cấp lại mật khẩu tạm + hủy phiên); endpoint `GET /accounts`, `GET /accounts/{id}`, `POST /accounts/{id}/temporary-password`, `POST /staff-assignments`, `PATCH /staff-assignments/{id}/end`, `GET /campuses`, `GET /classrooms`.
- Snapshot OpenAPI cập nhật có chủ đích: thêm path mới, `CreateAccountRequest.assignment`, `CreateAccountResponse.assignment`; không path nào bị xóa.
- Khác plan: không tách `StaffAssignmentController` khỏi feature account ở tầng service (controller ở `controller/organization` gọi `StaffingService` của account) để tránh vòng account↔organization; PHT xem danh sách gồm GV/bếp campus mình (không gồm PHT khác).
- Tiếp: Phase 2 docs sau khi user review.
### 2026-10-17 — Phase 2 docs (chờ review)
- User duyệt phase 1 ("ok"). Commit: chưa commit.
- Docs: `BUSINESS_RULES` AUTH-15..17 (CONFIRMED, user chốt 2026-10-17); ADR-0017 mục bổ sung (quyền mới, `StaffAccessPolicy`, `primary_role`); `USER_ROLES` (bảng quyền); `identity-access` (API + pitfalls), `school-structure` (API đọc + ghi phân công); `AUTH_CONTRACT` (tạo kèm `assignment`, mật khẩu tạm, danh sách, phân công, campus/lớp, bảng lỗi); `SECURITY` (`StaffAccessPolicy`); `DATA_DICTIONARY` (`primary_role`); `CURRENT_STATE`.
- Tiếp: Phase 3 FE (màn Nhân sự) sau khi user review.
### 2026-10-17 — Phase 3 FE (chờ review)
- Nhánh FE hiện tại `feature/FE-FEAT-48-nutrition-management`, không commit (theo lựa chọn của user ở phần A).
- Code FE: `services/account/{staffService.js, api/staffApi.js, mock/staffMockRepository.js}` (mock chép luật `StaffAccessPolicy`), `hooks/account/useStaff.js` (+`useStaffStructure`), `utils/account/staffValidation.js`, `components/account/AccountStatusBadge.jsx`; trang `/school/staff`, `/school/staff/new`, `/school/staff/:userId`; menu "Nhân sự" (mục nhận nhiều mã quyền); màn Ngoại lệ quyền chọn nhân sự từ `GET /accounts`; mock danh mục quyền cập nhật theo V8; sửa class `page__header` → `page__head` (class cũ không tồn tại).
- Verify FE: `node scripts/verify.mjs` → `VERIFY PASS full`; `tests/auth/staff.test.mjs` 4/4 + `permissions.test.mjs` 5/5 pass; check-ai-layer FE 0 lỗi (4 cảnh báo có sẵn).
- Giới hạn: chưa chạy UI trên trình duyệt, chưa nối BE thật; màn mock "Phân công PHT/GV" theo năm học giữ nguyên (mặc định e).
