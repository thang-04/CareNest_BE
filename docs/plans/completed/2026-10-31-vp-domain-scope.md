---
title: Phạm vi quyền hiệu phó theo mảng (xem cả trường, sửa theo phạm vi)
status: done
owner: thangnd
jira: G94-60 / BE-FEAT-01   # task con cho commit: hỏi user khi commit
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, school-structure, attendance, nutrition, child]
rules: [AUTH-02, AUTH-13, AUTH-15, AUTH-16, AUTH-18, AUTH-20, NUT-02, ATT-08]
risk: high
created: 2026-10-31
updated: 2026-10-31
---
# Phạm vi quyền hiệu phó theo mảng

Thay một phần `2026-10-27-survey-auth-alignment.md` (quyết định 14, AC-1/AC-2/AC-10): plan cũ 267 dòng nên tách plan mới.

## Context
User chốt 2026-10-31 (AUTH-02, AUTH-20): PHT xem dữ liệu cả trường mặc định; mảng HT giao (vd. dinh dưỡng) được sửa trong phạm vi HT chọn — một điểm trường hoặc cả trường. Code hiện:
- Xem liên cơ sở cần `ALL_CAMPUS_VIEW` (mặc định tắt): `Permission.java:46-51`, `CampusAccessPolicy.java:50-60`. Mã này đã có trên `dev`.
- Lọc "campus của PHT" chép ở nhiều chỗ, chưa qua `canRead*`: `CampusServiceImpl.java:73,110,345`, `SchoolConfigAccess.java:70-86` (dùng ở `RoomServiceImpl.java:52`, `TeacherAssignmentServiceImpl.java:255`), `ClassManagementServiceImpl.java:373-377`, `YearTransitionServiceImpl.java:77,560`, `MealCountServiceImpl.java:68-91,284-295`, `AttendanceDayRules.java:98-100` (đọc điểm danh và sức khỏe trong ngày).
- Chưa có mã quyền sửa nào theo mảng. Việc ghi của PHT trong mảng dinh dưỡng hiện chỉ có xác nhận và điều chỉnh số suất, kiểm bằng role + campus phân công (`MealCountController.java:47-59`, `MealCountServiceImpl.java:78-86`).
- Ngoại lệ từng người chỉ có GRANT/REVOKE, chưa có phạm vi (`V7__create_permissions.sql` bảng `user_permission_overrides`; `EffectivePermissionResolver.java:41-51`).

## Quyết định đã chốt
1. PHT xem dữ liệu trường (cơ sở, lớp, trẻ, dữ liệu module) ở mọi cơ sở, chỉ xem, miễn còn phân công PHT — user, 2026-10-31.
2. Mảng được giao: HT chọn phạm vi một điểm trường hoặc cả trường — user, 2026-10-31.
3. Tạo tài khoản, phân công, phụ huynh vẫn chỉ trong cơ sở của PHT (AUTH-15..19, không đổi).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** Actor HT (giao mảng + phạm vi), PHT (xem, sửa mảng). Rule AUTH-02, AUTH-20 (CONFIRMED 31/10), NUT-02, ATT-08 (CONFIRMED), P-36 (PENDING). Flow: [B] HT bật mã sửa mảng cho PHT kèm phạm vi → [S] lưu ngoại lệ có phạm vi, ghi lịch sử → [B] PHT sửa → [S] kiểm mã quyền + phạm vi campus.
- **Đối chiếu:** `SECURITY.md:76-77`, `USER_ROLES.md` dòng `ALL_CAMPUS_VIEW` ghi theo rule cũ; rule đã cập nhật 31/10.
- **Ảnh hưởng:** identity-access (quyền, policy), school-structure, attendance, nutrition (số suất), child (xếp lớp chỉ đọc). FE: `models/account/permissionConstants.js`, `models/school-config/permissionCatalog.js`, `pages/permissions/UserOverridesPage.jsx`, `components/permissions/OverrideWizardModal.jsx` (bỏ `ALL_CAMPUS_VIEW`, thêm chọn phạm vi), bộ lọc điểm trường của PHT (luôn hiện). APP: không (PHT không dùng App — AUTH-21). Dữ liệu cũ: dòng `ALL_CAMPUS_VIEW` trong `role_permissions`/`user_permission_overrides`.

### Câu hỏi mở
- [x] P-36a: phạm vi "một điểm trường" bắt buộc là điểm trường PHT được phân công? Đề xuất: có — HT không giao một PHT làm mảng ở cơ sở khác cơ sở của mình.
- [x] P-36b: xác nhận/điều chỉnh số suất (NUT-02) có theo mảng dinh dưỡng không? Đề xuất: có, mã `MEAL_COUNT_CONFIRM` **mặc định bật** cho PHT (phạm vi cơ sở mình) để giữ NUT-02 và hành vi hiện tại; HT thu hồi với PHT không làm dinh dưỡng, hoặc nâng lên cả trường. Mở lại điểm danh (ATT-08) giữ theo cơ sở phân công.
- [x] P-36c: mỗi mã quyền một phạm vi riêng (đề xuất), hay một phạm vi chung cho cả mảng?
- [x] Kế hoạch giáo dục: PHT nào cũng sửa được theo role (EDU-08, giữ theo code — user 29/10). AUTH-02 mới có áp vào đây không? Đề xuất: ngoài plan này, áp khi đưa quyền kế hoạch giáo dục vào ma trận.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| PHT xem phạm vi nào | Cả trường, chỉ xem | user | 31/10 | AUTH-02, AUTH-20 |
| Phạm vi mảng được giao | Một điểm trường hoặc cả trường, HT chọn | user | 31/10 | AUTH-02, AUTH-20 |
| P-36a/b/c, kế hoạch giáo dục | Theo đề xuất: điểm trường phân công; `MEAL_COUNT_CONFIRM` mặc định bật cho PHT, ATT-08 theo cơ sở; phạm vi theo từng mã; kế hoạch giáo dục ngoài plan | user | 31/10 | AUTH-02, AUTH-20, NUT-02, đóng P-36 |

## Acceptance criteria
- **AC-1** (`AUTH-20`): Given PHT campus A còn phân công, không có quyền gì thêm, When xem campus/lớp/trẻ/điểm danh/sức khỏe/phòng/lớp/số suất của B, Then 200.
- **AC-2** (`AUTH-20`): Given PHT hết phân công, When xem bất kỳ campus nào, Then 403.
- **AC-3** (`AUTH-20`, `AUTH-16`): Given PHT campus A, When phân công/tạo tài khoản/quản lý phụ huynh ở B hoặc mở lại điểm danh ở B, Then 403.
- **AC-4** (`AUTH-20`): Given DB còn dòng `ALL_CAMPUS_VIEW`, When chạy migration, Then dòng ma trận/ngoại lệ bị xóa, lịch sử giữ nguyên; `GET /permissions` không còn mã này.
- **AC-5** (`AUTH-20`): Given HT GRANT `MEAL_COUNT_CONFIRM` phạm vi `SCHOOL` cho PHT A, When PHT A xác nhận số suất B, Then 200; phạm vi `CAMPUS` ⇒ B bị 403, A được.
- **AC-6** (`AUTH-20`): Given REVOKE `MEAL_COUNT_CONFIRM` với PHT A, When xác nhận số suất A, Then 403; vẫn xem được (AC-1).
- **AC-7** (`AUTH-20`): Given request GRANT phạm vi `SCHOOL` cho mã không theo mảng, hoặc phạm vi đi kèm REVOKE, When lưu ngoại lệ, Then 400.
- **AC-8** (`AUTH-14`): Given đổi phạm vi CAMPUS → SCHOOL, When lưu, Then lịch sử ghi một dòng có phạm vi trước/sau.
- **AC-9** (`NUT-02`): Given seed mặc định, When PHT có phân công xác nhận số suất cơ sở mình, Then 200 (không hồi quy).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Xem cả trường | Bỏ `ALL_CAMPUS_VIEW`, `canReadCampus` mở mọi campus cho PHT còn phân công | Giữ mã, seed bật cho PHT | Rule không còn cho HT tắt xem; giữ mã thừa ⇒ HT tắt được, trái AUTH-20 |
| Lọc danh sách campus | Một hàm `CampusAccessPolicy.readableCampusIds` thay các bản chép | Sửa từng chỗ | Hết lệch giữa các module (CMR-03) |
| Lưu phạm vi mảng | Cột `scope` (`CAMPUS`/`SCHOOL`) trên `user_permission_overrides`, chỉ cho GRANT mã có cờ `campusScoped`; ô ma trận bật ⇒ `CAMPUS` | Bảng mới phân công mảng (user, mảng, phạm vi) | Dùng lại GRANT/REVOKE, API, lịch sử sẵn có; bảng mới ⇒ hai nơi cấu hình cùng một quyền |
| Kiểm sửa | `CampusAccessPolicy.canEditCampus(user, permission, campusId)` | Kiểm trong từng service | Một chỗ, module sau (thực đơn, kho) dùng lại |
Lưu phạm vi trên ngoại lệ là quyết định kiến trúc ⇒ ADR-0023 (Phase 2).

## Phases
### Phase 1 — Xem cả trường
- Files: M `utils/enums/Permission.java` · M `security/authorization/CampusAccessPolicy.java` · M `service/attendance/impl/AttendanceDayRules.java` · M `service/meals/impl/MealCountServiceImpl.java` (tách campus xem / campus xác nhận) · M `service/organization/impl/CampusServiceImpl.java` · M `service/organization/impl/SchoolConfigAccess.java` · M `service/organization/impl/ClassManagementServiceImpl.java` · M `service/child/impl/YearTransitionServiceImpl.java` · C `db/migration/V39__drop_all_campus_view_permission.sql` · test tương ứng · docs `SECURITY.md`, `USER_ROLES.md`, `AUTH_CONTRACT.md`, `openapi.yaml` (enum).
- Steps: 1. `readableCampusIds` (null = mọi campus cho HT và PHT còn phân công; rỗng = không). 2. Đổi các chỗ đọc ở Context sang hàm này hoặc `canRead*`; đường ghi giữ `canView*`. 3. Xóa `ALL_CAMPUS_VIEW` + migration xóa dòng.
- Tests: AC-1..4 → `CampusAccessPolicyTest`, `CampusServiceImplTest`, `MealCountIntegrationTest`, `AttendanceIntegrationTest` (`@DisplayName("AUTH-20: …")`).
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0 (Docker bật). **Dừng chờ user review.**

### Phase 2 — Phạm vi mảng trên ngoại lệ quyền
- Files: C `db/migration/V40__permission_override_scope.sql` (cột `scope` + CHECK; cột phạm vi ở `permission_change_log`; seed VP `MEAL_COUNT_CONFIRM` theo P-36b) · C `utils/enums/PermissionScope.java` · M `Permission.java` (cờ `campusScoped`, mã `MEAL_COUNT_CONFIRM`) · M `entity/account/UserPermissionOverride.java` · M `EffectivePermissionResolver.java` (trả thêm tập quyền phạm vi cả trường) · M `security/CurrentUser.java`, `CurrentUserService.java` · M `dto/account/UpdatePermissionOverridesRequest.java`, `PermissionOverrideResponse.java`, `PermissionChangeLogResponse.java`, `MyAccountResponse.java` (thêm `schoolWidePermissions`) · M `PermissionServiceImpl.java` · M `CampusAccessPolicy.java` (`canEditCampus`) · C `docs/decisions/ADR-0023-permission-scope-on-override.md` · docs `AUTH_CONTRACT.md`, `openapi.yaml`, `DATA_DICTIONARY.md`, `ERD.md`.
- Tests: AC-7, AC-8 → `PermissionServiceImplTest`; resolver/policy → `EffectivePermissionResolverTest`, `CampusAccessPolicyTest`.
- Exit: như Phase 1, `snapshot ok` sau khi sinh lại `openapi.yaml`. **Dừng chờ user review.**

### Phase 3 — Áp phạm vi cho số suất
- Files: M `controller/meals/MealCountController.java` (`@permissionGuard.has('MEAL_COUNT_CONFIRM')`, giữ điều kiện role hiện có) · M `MealCountServiceImpl.java` (`confirms`/`adjusts` qua `canEditCampus`) · test · docs `BUSINESS_RULES.md` (bỏ dòng "Code chưa theo" ở AUTH-02/20), `CURRENT_STATE.md`, `modules/identity-access.md`, `modules/nutrition.md`.
- Tests: AC-5, AC-6, AC-9 → `MealCountIntegrationTest` (chưa có unit test service số suất; thêm C `MealCountServiceImplTest` nếu cần mock phạm vi).
- Exit: như Phase 1. **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1..3 | AUTH-20, AUTH-16 | policy unit + service + integration điểm danh | 1 | pass |
| AC-4 | AUTH-20 | migration (integration Testcontainers) | 1 | pass (V39 chạy trong mọi integration test; chưa có test chèn dòng cũ) |
| AC-7, AC-8 | AUTH-20, AUTH-14 | service unit | 2 | pass |
| AC-5, AC-6, AC-9 | AUTH-20, NUT-02 | policy unit + integration | 3 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Sót chỗ lọc campus khi đổi sang đọc | PHT vẫn bị 403 ở một màn | Grep `StaffRole.VICE_PRINCIPAL` trong `service/` ở Phase 1 | Sửa chỗ sót |
| Đổi nhầm đường ghi sang `canRead*` | PHT sửa dữ liệu campus khác | AC-3; đường ghi chỉ `canView*`/`canEditCampus` | Revert commit phase |
| FE còn gửi/hiển thị `ALL_CAMPUS_VIEW` | 400 khi lưu ngoại lệ chứa mã cũ | Báo FE trước khi merge; danh sách file ở mục Ảnh hưởng | Thêm lại enum (migration V39 chỉ xóa dòng, không sửa schema) |
| Seed `MEAL_COUNT_CONFIRM` sai mặc định | PHT không xác nhận được suất | AC-9 | Migration mới đổi seed |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (FE 4 file + bộ lọc điểm trường; APP không)
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file ⇒ đề xuất tách: Phase 1 độc lập, tách được thành plan riêng nếu user muốn
- [x] Không dùng rule PENDING/OPEN như đã chốt (P-36 nằm ở Câu hỏi mở)
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan · không câu mơ hồ

## Progress log
### 2026-10-31 — Lập plan (draft, chờ duyệt)
- Commit: chưa commit
- Verify: chưa code; `node scripts/check-ai-layer.mjs` sau khi viết plan.
- Tiếp: user trả lời 4 câu hỏi mở và duyệt plan ⇒ Phase 1.

### 2026-10-31 — Duyệt
- User (thangnd) duyệt plan, chốt 4 câu hỏi mở theo đề xuất ⇒ `approved`; P-36 đóng. Chế độ tự chạy (CLAUDE.md): làm liền 3 phase, mỗi phase verify + log.

### 2026-10-31 — Phase 1 (done)
- Commit: chưa commit
- Sửa: `CampusAccessPolicy.canReadAllCampuses` (HT, PHT còn phân công) + `canReadCampus` dùng nó; bỏ `Permission.ALL_CAMPUS_VIEW`; `V39__drop_all_campus_view_permission.sql`. Đường đọc chuyển sang hàm chung: `CampusServiceImpl` (danh sách/chi tiết), `SchoolConfigAccess.canViewSchool/checkCanViewSchool` (phòng, cơ cấu lớp, phân công GV, tổ trưởng, lịch sử phân công), `YearTransitionServiceImpl.getPlacements` (cờ sửa vẫn chỉ HT), `AttendanceDayRules.canView/canReadCampus` (điểm danh, tổng hợp, sức khỏe trong ngày), `MealCountServiceImpl.Viewer.reviews`. Đường ghi giữ nguyên (`canRecord`, `canUnlock`, `confirms`, `adjusts`, `checkPrincipal`).
- Test: `CampusAccessPolicyTest` (AC-1, AC-2), `ClassroomAccessPolicyTest`, `ChildAccessPolicyTest`, `StaffAccessPolicyTest` (AC-3), `CampusServiceImplTest`, `CampusServiceIntegrationTest`, `ClassStructureIntegrationTest`, `AttendanceIntegrationTest` (AC-1, AC-3 mở lại điểm danh), `MealCountIntegrationTest` (xem được, không xác nhận). `openapi.yaml` sinh lại: chỉ bớt 9 dòng enum `ALL_CAMPUS_VIEW`. Docs: `SECURITY.md`, `USER_ROLES.md`, `AUTH_CONTRACT.md` (tác động FE), `modules/identity-access.md`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 610 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 199s` (lần đầu FAIL 4 test khẳng định hành vi cũ — đã sửa theo AUTH-20; lần hai FAIL spotless — đã `spotless:apply`).
- Khác plan: AC-4 kiểm bằng V39 chạy trong mọi integration test (Flyway), chưa có test riêng chèn dòng cũ rồi migrate. Thêm `TeacherAssignmentServiceImpl`, `RoomServiceImpl` (gọi `SchoolConfigAccess`) vào danh sách file.
- Tiếp: Phase 2.

### 2026-10-31 — Phase 2 (done)
- Commit: chưa commit
- Sửa: `PermissionScope` (CAMPUS/SCHOOL); `Permission` thêm cờ `campusScoped` + mã `MEAL_COUNT_CONFIRM` (PRINCIPAL cố định, VP cấu hình được); `V40__permission_override_scope.sql` (cột `scope` + CHECK chỉ GRANT, GRANT cũ = CAMPUS; `before_scope/after_scope` ở lịch sử; seed VP `MEAL_COUNT_CONFIRM`); `UserPermissionOverride`, `PermissionChangeLog.withScopes`; `EffectivePermissionResolver.schoolWide`; `CurrentUser.schoolWidePermissions` + `hasSchoolWidePermission` (constructor 7 tham số giữ nguyên = không có phạm vi cả trường); `CurrentUserService` đọc ma trận/ngoại lệ một lần cho cả hai tập; `PermissionServiceImpl` nhận/kiểm `scope` (400 `INVALID_VALUE` khi REVOKE có scope hoặc SCHOOL cho mã không theo mảng), đổi chỉ phạm vi cũng ghi lịch sử; `CampusAccessPolicy.canEditCampus`. DTO: `OverrideItem.scope`, `PermissionOverrideResponse.scope`, `PermissionChangeLogResponse.beforeScope/afterScope`, `PermissionResponse.campusScoped`, `MyAccountResponse.schoolWidePermissions`.
- Test: `PermissionServiceImplTest` (AC-7 `schoolScopeOnlyForGrantOfCampusScopedPermission`, AC-8 `raisingScopeToSchoolSavesOverrideAndScopeHistory`), `EffectivePermissionResolverTest#schoolWideOnlyFromSchoolGrantOfCampusScopedPermission`, `CampusAccessPolicyTest` (canEditCampus: điểm trường, cả trường + hết phân công, thu hồi, HT); cập nhật `PermissionTest`, `CurrentUserServiceTest` (HT có thêm `MEAL_COUNT_CONFIRM`), `AccountControllerTest`, `AuthServiceImplTest`. `PermissionSchemaIntegrationTest` (seed khớp `defaultRoles`) pass với V40.
- Docs: ADR-0023, `DATA_DICTIONARY.md`, `ERD.md`, `AUTH_CONTRACT.md` (`/accounts/me`, API phân quyền, lỗi scope), `openapi.yaml` sinh lại (thêm field + enum, bớt `ALL_CAMPUS_VIEW`).
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 616 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 215s`.
- Khác plan: lỗi scope dùng lại `INVALID_VALUE` 9007, không thêm `ApiCode` mới. `PermissionResponse.campusScoped` thêm ngoài plan để FE biết mã nào nhận phạm vi.
- Tiếp: Phase 3.

### 2026-10-31 — Phase 3 (done)
- Commit: chưa commit
- Sửa: `MealCountController#confirm` ⇒ `@permissionGuard.has('MEAL_COUNT_CONFIRM')` (vẫn HT + PHT theo mặc định — thêm vào `ControllerSecurityCoverageTest#defaultPermissionsKeepPreviousRoleAccess`); `#adjust` giữ `hasRole('VICE_PRINCIPAL')`. `MealCountServiceImpl.Viewer.confirms/adjusts` qua `CampusAccessPolicy.canEditCampus` (nhớ kết quả theo campus trong request); điều chỉnh vẫn chỉ PHT.
- Test: `MealCountIntegrationTest#mealCountConfirmFollowsNutritionScope` (AC-5 PHT B phạm vi cả trường xác nhận A, AC-6 PHT A bị thu hồi không xác nhận nhưng vẫn xem); AC-9 = luồng xác nhận sẵn có của PHT A với quyền mặc định. User test của `MealCountIntegrationTest`, `MealCountListenerIntegrationTest` dựng kèm `TestPermissions.defaultsOf`.
- Docs: `BUSINESS_RULES.md` (AUTH-02 trạng thái code), `CURRENT_STATE.md`, `modules/identity-access.md` (2 pitfall), `modules/nutrition.md`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 617 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 251s`.
- Khác plan: không ghép `hasRole` với guard ở `#adjust` — `ControllerSecurityCoverageTest` coi mọi chuỗi nháy đơn là mã quyền; mã + phạm vi đã kiểm ở service.
- Còn lại ngoài BE: FE (`CareNest_FE`) theo `AUTH_CONTRACT.md` mục Tác động FE/APP; chưa commit (mã Jira task con hỏi khi commit).

