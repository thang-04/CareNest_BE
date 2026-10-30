---
title: Căn chỉnh auth + phân quyền theo khảo sát team 27/10
status: in-progress
owner: thangnd
jira: G94-60 / BE-FEAT-01
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, school-structure]
rules: [AUTH-02, AUTH-03, AUTH-13, AUTH-16, AUTH-17, AUTH-19, AUTH-20, AUTH-21, AUTH-22, AUTH-23, SCH-01, SCH-02, SCH-03, SCH-04, SCH-05, SCH-06]
risk: high
created: 2026-10-27
updated: 2026-10-30
---
# Căn chỉnh auth + phân quyền theo khảo sát team 27/10

Liên quan: `2026-10-04-keycloak-auth.md`, `2026-10-16-permission-management.md`, nhánh đồng đội `feature/G94-226-BE-FEAT-09-school-year-goals` (`2026-10-22-muc-tieu-nam-hoc.md`).

## Context
Khảo sát team 27/10 (SCO/ORG/ROL) lệch BE ở phạm vi PHT, mức quyền, bếp dùng Web, tổ trưởng; nhiều nghiệp vụ tổ chức trường chưa ghi.
- PHT scope theo campus phân công: `CampusAccessPolicy.java:34-37`, `StaffAccessPolicy.java:54-59,66-70`.
- `CurrentUser.hasPermission` có sẵn (`security/CurrentUser.java:41`); V7 không CHECK `permission_code` (`V7__create_permissions.sql:8-13`).
- Gateway login không kiểm role (`AuthServiceImpl.java:94-151`).
- Khớp sẵn: ROL-07, ROL-08, ROL-10, ROL-12 (dạy thay qua `valid_to`), ROL-06 (Admin không xem trẻ, `ChildAccessPolicy.java:25-27`).

## Quyết định đã chốt
1. PHT thuộc cơ sở nào chỉ xem các mảng của cơ sở mình; HT cấp quyền thì xem thêm cơ sở khác — chỉ xem, chỉ dữ liệu trường (cơ sở, lớp, trẻ, module sau); tài khoản/phân công/phụ huynh vẫn chỉ cơ sở mình — user, 27/10.
2. Mảng PHT (dinh dưỡng/chuyên môn) và ROL-09: quyền bật/tắt + mã quyền theo mảng (ADR-0017), không F/R/N; mã cụ thể thêm cùng module — user, 27/10.
3. Bếp và phụ huynh chỉ dùng App: chặn ở BE gateway + FE — user, 27/10.
4. Tổ trưởng = GV được HT (chỉ HT) phân công theo nhóm tuổi, có thêm quyền cột "Tổ trưởng" của ma trận; việc: soạn mục tiêu năm/kế hoạch chủ đề, duyệt kế hoạch/giáo án GV nhóm tuổi trước PHT — user, 27/10.
5. Nhánh auth chỉ chuẩn bị cột Tổ trưởng; G94-226 tự nối `goals.*`, làm API gán tổ trưởng — user, 27/10.
6. Docs chỉ sửa AUTH/SCH; không đụng ADR-0009, NUT-*, `MEAL_NUTRITION_AI_SCHEMA.md`, `modules/nutrition.md`, `modules/attendance.md` (đang sửa dở) — user, 27/10.
7. Điều chỉnh quyết định 22/10 #1: vẫn không có `areas`; module nghiệp vụ gác bằng permission (mảng = mã quyền) — user, 27/10.
8. Web FE ghép đăng nhập qua gateway BE (ADR-0013/0015); client `carenest-dev` chỉ cho dev — user, 29–30/10.
9. Chia hai đợt: đợt 1 = phase 6–8 trên nhánh này; đợt 2 = phase 9 (AUTH-03) sau khi G94-236/226 vào `dev` — user, 29–30/10.
10. AUTH-21 phía App (BGH không dùng App): để tới khi ghép App; đợt này chỉ chặn Web — user, 29–30/10.
11. AUTH-22: không chặn tạo tài khoản bếp thứ hai trong cơ sở, chỉ quy ước — user, 29–30/10.
12. Quên mật khẩu trên Web không áp dụng cho bếp/phụ huynh, chặn im lặng: xử lý như email không tồn tại (không gửi OTP, cùng response); muốn mật khẩu mới thì HT/PHT cấp lại mật khẩu tạm (AUTH-17, AUTH-19) — user, 29–30/10.
13. Lỗi Web login của bếp/phụ huynh giữ 403 `FORBIDDEN` + `desc`, không thêm `ApiCode` — user, 29–30/10 (khớp Phase 3).
14. Đường đọc chặn mặc định (điều chỉnh Phase 2): `canView*` = phạm vi được thao tác (cơ sở/lớp đang phân công); `canRead*` = phạm vi chỉ đọc rộng hơn (`ALL_CAMPUS_VIEW`, AUTH-03). Endpoint đọc tự chọn `canRead*` — user, 29–30/10.
15. AUTH-03: GV xem lại, chỉ đọc, dữ liệu lớp có phân công đã kết thúc (năm học cũ và dạy thay) trong khoảng phân công, khi lớp đó cùng cơ sở với một lớp GV đang được phân công; GV không còn phân công nào ⇒ không xem — user, 29–30/10.

## Làm rõ nghiệp vụ
- **Actor:** HT, PHT, GV/Tổ trưởng, bếp, phụ huynh, Admin. **Outcome:** quyền + phạm vi khớp khảo sát.
- **Ảnh hưởng:** identity-access, school-structure; FE: `authProfile.js` (WEB_ROLES), màn phân quyền (cột mới); APP: không đổi.
- **Thuộc G94-226 (không sửa ở đây để tránh xung đột):** OBS-08, FE-04/Exclusions về giáo án trong `PROJECT_CONTEXT.md`, dòng Tổ trưởng trong `USER_ROLES.md`, `age_groups`.

### Câu hỏi mở
Không còn câu chặn plan này. Ghi nhận cho phần dinh dưỡng (chủ nhánh dinh dưỡng xử lý, không sửa ở đây):
- ORG-01 kho khô quản lý chung, NCC dùng chung — lệch NUT-11/12, ADR-0009.
- SCO-04 không thu tiền, giữ giá suất theo nhóm tuổi; báo cáo tháng suất/ngày ăn từng trẻ.
- ROL-02 / P-05: PHT cơ sở xác nhận số suất, HT thay khi vắng.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| PHT xem phạm vi nào (ROL-01 vs ROL-03) | Cơ sở mình; cơ sở khác khi HT cấp, chỉ xem | user | 27/10 | AUTH-02, AUTH-20 |
| Mức quyền (ROL-09) | Bật/tắt + mã theo mảng | user | 27/10 | AUTH-20 |
| Bếp vào Web (ORG-10) | Chặn BE gateway + FE | user | 27/10 | AUTH-21 |
| Quyền tổ trưởng | Thêm quyền theo phân công, chỉ HT gán | user | 27/10 | AUTH-23 |
| Quên mật khẩu Web cho bếp/phụ huynh | Chặn im lặng như email không tồn tại | user | 29–30/10 | AUTH-21 (Phase 6) |
| GV xem lớp cũ: điều kiện | Phân công đã kết thúc, cùng cơ sở lớp GV đang dạy, trong khoảng phân công | user | 29–30/10 | AUTH-03 (Phase 6) |
| Tài khoản bếp thứ hai/cơ sở | Không chặn, chỉ quy ước | user | 29–30/10 | AUTH-22 (Phase 6) |

## Acceptance criteria
- **AC-1** (`AUTH-20`): Given PHT campus A không có `ALL_CAMPUS_VIEW`, When xem lớp/trẻ campus B, Then 403.
- **AC-2** (`AUTH-20`): Given PHT campus A được GRANT `ALL_CAMPUS_VIEW`, When xem lớp/trẻ/campus B, Then 200.
- **AC-3** (`AUTH-20`, `AUTH-13`): Given như AC-2, When phân công/tạo TK/quản lý phụ huynh ở B, Then 403.
- **AC-4** (`AUTH-20`): Given PHT có GRANT nhưng hết phân công, When xem campus bất kỳ, Then 403.
- **AC-5** (`AUTH-21`): Given TK chỉ có KITCHEN_STAFF hoặc PARENT, When login/refresh qua gateway Web, Then 403 và không có cookie `CARENEST_SESSION`.
- **AC-6** (`AUTH-21`): Given TK ADMIN/PRINCIPAL/VICE_PRINCIPAL/TEACHER, When login gateway, Then 200.
- **AC-7** (`AUTH-23`): Given GV có phân công `AGE_GROUP_TEAM_LEADER` còn hiệu lực, When tính quyền, Then có quyền cột Tổ trưởng; hết hạn/không phân công ⇒ không có; REVOKE ⇒ mất.
- **AC-8** (`AUTH-23`, `AUTH-14`): Given HT, When bật/tắt quyền cột Tổ trưởng, Then lưu ma trận + `permission_change_log`.
- **AC-9** (`AUTH-21`): Given email của TK chỉ có KITCHEN_STAFF hoặc PARENT, When `POST /auth/password/forgot`, Then cùng status/shape như email không tồn tại, không gửi OTP, `verify` không thành công.
- **AC-10** (`AUTH-20`): Given PHT campus A có `ALL_CAMPUS_VIEW`, When kiểm campus B, Then `canViewCampus` = false và `canReadCampus` = true.
- **AC-11** (`AUTH-20`, `AUTH-13`): Given như AC-10, When thao tác ghi ở campus B qua `canView*` (vd. mở lại điểm danh sau khi G94-236 vào), Then 403.
- **AC-12** (`AUTH-03`): Given GV có phân công đã kết thúc ở lớp X và đang được phân công lớp Y cùng cơ sở, When đọc dữ liệu X, Then 200 với dữ liệu trong khoảng phân công; ngoài khoảng không trả; ghi ⇒ 403.
- **AC-13** (`AUTH-03`): Given như AC-12 nhưng X ở cơ sở khác Y, When đọc X, Then 403.
- **AC-14** (`AUTH-03`): Given GV không còn phân công nào hiệu lực, When đọc lớp cũ, Then 403.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Xem liên cơ sở | Mã `ALL_CAMPUS_VIEW` (PHT cấu hình được, mặc định tắt) | Cột/khái niệm campus phụ | Dùng lại GRANT/REVOKE + log sẵn có |
| Chặn Web | Kiểm role sau Direct Grant, 403 `ApiCode.FORBIDDEN` desc mới | Chính sách client Keycloak | Không đổi realm; FE nhận lỗi rõ |
| Quyền tổ trưởng | Cột ma trận theo phân công | GRANT từng người; role Keycloak | Hết phân công tự mất; ROL-05 cấm role đăng nhập |
| Đường đọc liên cơ sở | `canRead*` riêng, `canViewCampus` giữ cơ sở mình | Nới `canViewCampus` + thêm `canManageCampus` | G94-236 `AttendanceDayRules.canUnlock` (ghi) gọi `canViewCampus`; module quên đổi ⇒ thiếu xem, không leo quyền |
| Quên mật khẩu bếp/phụ huynh | Chặn im lặng | 403 rõ ràng | Giữ chống dò tài khoản của `forgot` (`AuthServiceImpl.java:280-283`) |
| AUTH-03 dùng chung | Policy trả khoảng ngày được đọc | `taughtClosedYear` riêng mỗi module | Một chỗ kiểm; module chỉ giới hạn truy vấn theo khoảng |

## Phases
### Phase 1 — Docs/tri thức
- Files: C `docs/plans/active/2026-10-27-survey-auth-alignment.md` · M `docs/business/BUSINESS_RULES.md` · M `docs/business/USER_ROLES.md` · M `docs/system/CROSS_REPO_MAP.md` · M `docs/context/PROJECT_CONTEXT.md` · M `docs/database/DATA_DICTIONARY.md`
- Exit: `node scripts/check-ai-layer.mjs` pass. **Dừng chờ user review.**

### Phase 2 — Xem liên cơ sở
- Files: M `Permission.java` · M `StaffAccessPolicy.java` · M `CampusAccessPolicy.java` · M `SchoolStructureServiceImpl.java` · test policy + service · M `AUTH_CONTRACT.md`, `modules/identity-access.md`
- Ghi giữ `managedCampusIds` (`checkCanCreate/Assign/ManageAccount/ManageChildGuardians/ManageParent`, `AccountQueryServiceImpl:72`, `ChildServiceImpl:112`).
- Tests: AC-1..4 (`@DisplayName("AUTH-20: …")`). Exit: `node scripts/verify.mjs` → `VERIFY PASS full`. **Dừng chờ review.**

### Phase 3 — Chặn Web cho bếp/phụ huynh
- Files: M `AuthServiceImpl.java` (login + refresh: không có role Web ⇒ logout phiên Keycloak, không set cookie, 403 `This account cannot sign in on the Web app`) · test · M `AUTH_CONTRACT.md` (dòng 7 lỗi thời), `architecture/SECURITY.md:11-12`.
- Tests: AC-5, AC-6. Exit: verify PASS. **Dừng chờ review.**

### Phase 4 — FE (chỉ khi user cho phép trong tin nhắn lúc đó)
- `CareNest_FE`: bỏ KITCHEN_STAFF khỏi `WEB_ROLES` (`authProfile.js`), hiển thị lỗi 403 mới; chạy test FE.

### Phase 5 — Cột "Tổ trưởng" trong ma trận
- Files: C migration V10 (nới `ck_role_permissions_role` thêm `AGE_GROUP_TEAM_LEADER`) · M `StaffRole.java` · M `Permission.java` (holder cấu hình được) · M `EffectivePermissionResolver.java` · M `PermissionServiceImpl.java` · test · M `USER_ROLES.md`, `AUTH_CONTRACT.md`.
- Chưa bật mã nào cho cột (G94-226 đưa `goals.*`). Trên dev chưa tạo được phân công này (CHECK V2) ⇒ không đổi hành vi hiện tại.
- Tests: AC-7, AC-8. Exit: verify PASS. **Dừng chờ review.**

### Điều kiện trước đợt 1 (user cho phép riêng trong tin nhắn lúc làm)
- Commit phần phase 2, 3, 5 đang chưa commit (mã Jira theo task user nêu), rồi merge `origin/dev` vào nhánh này: `BUSINESS_RULES.md` ở nhánh chưa có AUTH-20..23 (vào `dev` qua G94-254). Số migration V10 kiểm lại theo thứ tự merge (INT-01).

### Phase 6 — Tri thức (đợt 1)
- Files: M `docs/business/BUSINESS_RULES.md` (AUTH-21 thêm quên mật khẩu; AUTH-03 thêm điều kiện cùng cơ sở + dạy thay; AUTH-22 ghi không chặn tài khoản thứ hai) · M `docs/contracts/AUTH_CONTRACT.md` · M `docs/architecture/SECURITY.md` (`canView*` vs `canRead*`) · M `docs/modules/identity-access.md` (AUTH-01..23).
- Exit: `node scripts/check-ai-layer.mjs` pass. **Dừng chờ review.**

### Phase 7 — Chặn quên mật khẩu cho bếp/phụ huynh (đợt 1)
- Files: M `AuthServiceImpl.java` (`forgot` lọc thêm `primary_role` thuộc `WEB_ROLES`, null cho qua như nhánh 428; TK bị lọc xử lý như không tồn tại tại `:280-283`) · test `AuthServiceImplTest`.
- Tests: AC-9. Exit: `node scripts/verify.mjs` PASS. **Dừng chờ review.**

### Phase 8 — Đường đọc chặn mặc định (đợt 1, điều chỉnh Phase 2)
- Files: M `CampusAccessPolicy.java` (`canViewCampus` về cơ sở mình; thêm `canReadCampus`/`checkCanReadCampus` có `ALL_CAMPUS_VIEW`) · M `ClassroomAccessPolicy.java` (`canReadClassroom`; `canViewClassroom` dùng `canViewCampus` tại `:35`) · M `ChildAccessPolicy.java` (`canReadChild`; `:43`) · M `ChildServiceImpl.java:136` (xem hồ sơ trẻ ⇒ `checkCanReadChild`) · test 3 policy + service.
- AC-1..4 chuyển sang `canRead*`, kết quả không đổi; thêm AC-10. AC-11 kiểm khi module ghi vào `dev`.
- Exit: verify PASS. **Dừng chờ review.**

### Phase 9 — AUTH-03 GV xem lớp cũ (đợt 2, sau khi G94-236/226 vào `dev`)
- `ClassroomAccessPolicy`: khoảng ngày GV được đọc lớp X = phân công TEACHER đã kết thúc ở X, khi X cùng cơ sở với một lớp GV đang được phân công; `canReadClassroom` đúng khi có khoảng. `ChildAccessPolicy.canReadChild`: trẻ có enrollment ở X giao với khoảng đó.
- Module đọc giới hạn truy vấn theo khoảng; thay `AttendanceDayRules.taughtClosedYear` (G94-236 `:104-109`, chỉ năm đã đóng) bằng policy chung. Đường ghi giữ phân công còn hiệu lực (`canRecord`).
- Files cụ thể chốt khi bắt đầu đợt 2 (code module chưa ở `dev`); >8 file ⇒ tách plan riêng.
- Tests: AC-12..14. Exit: verify PASS. **Dừng chờ review.**

### Bàn giao G94-226 (không sửa nhánh họ)
- Rebase lên dev: migration của họ đánh số sau dev (V11+), đổi số ADR-0017 của họ.
- Đưa `goals.*` vào `Permission` (cấu hình cho cột Tổ trưởng/PHT), bỏ map tạm `EduPlanAccessPolicy`, controller dùng `@permissionGuard`.
- API chỉ HT gán/kết thúc tổ trưởng (`ageGroupId`), chặn 1 tổ trưởng còn hiệu lực/nhóm tuổi (AUTH-23); FE thay role mock `TEAM_LEADER`.
- Sau Phase 8: đọc dùng `canRead*`, ghi dùng `canView*`. `AttendanceDayRules.canUnlock` giữ `canViewCampus` (giờ chỉ cơ sở mình — đúng ATT-08); màn xem điểm danh nếu cần xem liên cơ sở thì đổi sang `canReadCampus`.

### Tác động FE còn lại (chỉ liệt kê; sửa `CareNest_FE` khi user cho phép)
- Commit phần dở G94-209 (`.commit-worktrees/permission-auth`); `dev` FE vẫn mock + token localStorage.
- Nhánh G94-178/179/180 bỏ `devKeycloakAuth.js`/`carenest-dev`, chuyển sang gateway.
- Tổ trưởng suy từ `assignments`, không phải role; bỏ `areas` NONE/FULL/LIMITED (`RoleGuard.jsx` `AreaGate`) ⇒ `permissions`; `/users` ⇒ `/accounts`; bỏ route `/facility` (FAC đã loại).
- Quên mật khẩu: không đổi màn (BE trả cùng response).

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1..4 | AUTH-20 | policy unit + service | 2 | — |
| AC-5..6 | AUTH-21 | `AuthServiceImpl` unit | 3 | — |
| AC-7..8 | AUTH-23 | resolver unit + permission service | 5 | — |
| AC-9 | AUTH-21 | `AuthServiceImpl` unit | 7 | — |
| AC-10 | AUTH-20 | policy unit | 8 | — |
| AC-11 | AUTH-20, AUTH-13 | service module ghi (sau merge) | 8/9 | — |
| AC-12..14 | AUTH-03 | policy unit + service đọc của module | 9 | — |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Xung đột merge với G94-226 (migration, ADR, docs giáo án) | Merge chậm | Không sửa phần họ sở hữu; ghi bàn giao | — |
| Bếp dùng chung TK | Mất truy vết cá nhân | Team chấp nhận, ghi AUTH-22 | — |
| Đọc nhầm sang ghi khi tách viewable/managed | Lộ quyền sửa campus khác | Test AC-3; Phase 8 chặn mặc định | Revert phase 2 |
| Tên `canView*` (= được thao tác) dễ hiểu nhầm cạnh `canRead*` | Module mới gọi sai | Ghi `SECURITY.md` + bàn giao; đổi tên để sau khi các nhánh module đã merge | — |
| Phase 9 phụ thuộc nhánh module | Đợt 2 trễ | Tách đợt; file chốt khi bắt đầu | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (FE `authProfile.js`, màn phân quyền)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >8 file ⇒ chia 4 phase BE, mỗi phase ≤8 file
- [x] Không dùng rule PENDING như đã chốt (P-05, P-16, ORG-08 giữ PENDING)
- [x] Tự review: không placeholder · không mâu thuẫn
- [x] Plan >150 dòng và >3 phase sau bổ sung 30/10: user chọn nối vào plan này và duyệt 30/10; Phase 9 tách plan riêng nếu >8 file

## Progress log
### 2026-10-27 — Plan approved
- User duyệt plan trong phiên (ExitPlanMode 27/10).

### 2026-10-27 — Phase 1 (chờ review)
- Commit: chưa commit
- Sửa: `BUSINESS_RULES.md` (AUTH-02, AUTH-16, thêm AUTH-20..23, nhóm SCH-01..06, P-05/P-16 ghi chú khảo sát, thêm P-18, đóng P-14), `USER_ROLES.md`, `CROSS_REPO_MAP.md` (dòng bếp), `PROJECT_CONTEXT.md` (Admin, V1, MVP, nguồn), `DATA_DICTIONARY.md` (ghi chú `classrooms`), `ADR-0003`, `modules/identity-access.md`, `modules/school-structure.md` (P-14 đóng).
- Verify: `node scripts/check-ai-layer.mjs` → `check-ai-layer (BE): 0 lỗi, 0 cảnh báo`
- Khác plan: không sửa OBS-08, FE-04/Exclusions giáo án, dòng Tổ trưởng trong `USER_ROLES.md` vì G94-226 đã sửa (tránh xung đột); không sửa dòng Principal/VP "App: sau" trong `CROSS_REPO_MAP.md` vì lệch quyết định APP 13/10 (BGH có trên app) — chờ user.
- Tiếp: Phase 2 sau khi user review.
- Bổ sung sau review: user chốt Ban giám hiệu không dùng App ⇒ AUTH-21 + `CROSS_REPO_MAP.md` dòng Principal/VP. Repo APP còn màn BGH (`CareNest_APP/docs/architecture/NAVIGATION.md:58`, `docs/features/vice-principal/`) — chưa sửa (cần user cho phép).

### 2026-10-27 — Phase 2 (chờ review)
- Commit: chưa commit
- Sửa: `Permission.java` (thêm `ALL_CAMPUS_VIEW`, cấu hình được cho PHT, mặc định tắt), `CampusAccessPolicy.java` (PHT có quyền + còn phân công PHT ⇒ xem mọi campus), test `CampusAccessPolicyTest` (AC-1, AC-2, AC-4), `StaffAccessPolicyTest` (AC-3); `docs/api/openapi.yaml` (enum thêm `ALL_CAMPUS_VIEW`, sinh bằng `-Dopenapi.snapshot.update=true`), `SECURITY.md`, `AUTH_CONTRACT.md`, `USER_ROLES.md`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 311 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 73s`
- Khác plan: không đổi `StaffAccessPolicy`/`SchoolStructureServiceImpl`. `GET /campuses`, `GET /classrooms` là danh sách chọn cho tạo tài khoản/phân công (gác bằng `ACCOUNT_MANAGE`/`STAFF_ASSIGNMENT_MANAGE`) ⇒ giữ campus phụ trách, tránh hiện campus mà PHT không được ghi. Xem liên cơ sở đi qua `CampusAccessPolicy` (hồ sơ trẻ, lớp, module sau).
- Consumer: FE màn phân quyền nhận thêm mã `ALL_CAMPUS_VIEW` từ `GET /permissions`, `GET /role-permissions` (không đổi hình dạng).
- Tiếp: Phase 3 (chặn Web cho bếp/phụ huynh) sau khi user review.

### 2026-10-27 — Phase 3 (chờ review)
- Commit: chưa commit
- Sửa: `AuthServiceImpl.java` (`WEB_ROLES` + `ensureWebRole`: login sau khi kiểm provision/inactive; đổi mật khẩu lần đầu theo `primary_role` khi có; refresh) — 403 `This account cannot sign in on the Web app`, token Keycloak vừa cấp bị thu hồi qua `finally` sẵn có, không set cookie. Test `AuthServiceImplTest` (AC-5, AC-6 + first login + refresh). Docs: `AUTH_CONTRACT.md` (dòng 7 cũ, lỗi gateway, hướng dẫn client), `SECURITY.md` (ngoại lệ gateway Web).
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 315 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 74s` (lần đầu FAIL spotless, đã `mvnw spotless:apply`).
- Khác plan: thêm chặn ở bước đổi mật khẩu lần đầu (không có token, dùng `primary_role`) để bếp không đặt mật khẩu qua Web rồi mới bị chặn. Không thêm `ApiCode` mới: dùng `FORBIDDEN` + `desc` riêng như các lỗi 403 khác của gateway; snapshot OpenAPI không đổi.
- Consumer FE: `src/services/authProfile.js` (bỏ KITCHEN_STAFF khỏi `WEB_ROLES`), màn login hiển thị lỗi 403 mới. APP: không đổi.
- Tiếp: Phase 4 (FE) cần user cho phép sửa `CareNest_FE`; hoặc Phase 5.

### 2026-10-27 — Phase 5 (chờ review)
- Commit: chưa commit
- Sửa: `CareNestRole` thêm `TEAM_LEADER` (`isLoginRole()` = false, `fromKeycloakRole` bỏ qua ⇒ token không mang được); `Permission.CONFIGURABLE_ROLES` thêm `TEAM_LEADER`; C `V10__team_leader_permission_column.sql` (nới `ck_role_permissions_role`); `StaffAssignmentRepository.existsActiveTeamLeader` (native, `staff_role = 'AGE_GROUP_TEAM_LEADER'`) + `StaffAssignmentService.hasActiveTeamLeaderAssignment`; C `security/authorization/TeamLeaderAssignmentChecker`; `EffectivePermissionResolver.permissionRoles`; `CurrentUserService` (quyền tính trên role token + `TEAM_LEADER` khi đang là tổ trưởng, `CurrentUser.roles` giữ role token); `PermissionServiceImpl.rolesOf` (ngoại lệ từng người áp được cho cột này). Test: `CurrentUserServiceTest`, `PermissionServiceImplTest`, `EffectivePermissionResolverTest`, C `TeamLeaderAssignmentCheckerTest`, `StaffDirectoryIntegrationTest` (query native + CHECK V10 trên PostgreSQL thật). `docs/api/openapi.yaml` (enum role thêm `TEAM_LEADER`), `DATA_DICTIONARY.md`, `AUTH_CONTRACT.md`, `USER_ROLES.md`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 324 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 67s`
- Khác plan: (1) khóa cột ma trận là `TEAM_LEADER` (vừa `VARCHAR(20)`, khớp cột mock FE) thay vì `AGE_GROUP_TEAM_LEADER`; tên phân công vẫn `AGE_GROUP_TEAM_LEADER` của G94-226. (2) Không thêm `StaffRole.AGE_GROUP_TEAM_LEADER` (switch `resolveScope` phải đổi theo, để G94-226 thêm) — dùng native query theo chuỗi. (3) AC-7 phần "có quyền / REVOKE mất quyền" chưa test được bằng mã thật vì chưa có mã cấu hình cho cột; đã test role tính quyền có `TEAM_LEADER` và ô ma trận cột này không áp cho GV thường.
- Bàn giao G94-226 (bổ sung): khi đổi `existsActiveTeamLeader` sang JPQL với `StaffRole.AGE_GROUP_TEAM_LEADER` giữ hành vi `hasActiveTeamLeaderAssignment`; mã `goals.*` khai `configurableRoles` gồm `TEAM_LEADER` (+ PHT nếu cần), test AC-7 đầy đủ.
- Consumer FE: `GET /role-permissions` thêm phần tử `TEAM_LEADER` (editable, chưa có quyền); `GET /accounts/{id}/permission-overrides` `roles` có `TEAM_LEADER` khi GV đang là tổ trưởng; enum `CareNestRole` trong OpenAPI thêm `TEAM_LEADER`.
- Tiếp: Phase 4 (FE) khi user cho phép; bàn giao G94-226.

### 2026-10-27 — Kiểm thử bổ sung (chờ review)
- Review độc lập (subagent code-reviewer) trên diff nguồn: không lỗi critical/leo quyền. Đã sửa: refresh 403 không xóa cookie (`AuthController.refresh` xóa cookie khi 401 hoặc 403; test `AuthControllerTest#refreshClearsCookieWhenForbidden`); thêm test nhánh `primary_role` null (`AuthServiceImplTest`) và `KeycloakRealmRoleConverterTest` bỏ realm role `TEAM_LEADER`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 327 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 66s`
- E2E cô lập (Keycloak thật + bootstrap gateway + API từ jar hiện tại, profile dev, dữ liệu giả, script ngoài repo): login Web 4 role được / bếp, phụ huynh 403 không cookie, phiên Keycloak bị thu hồi; refresh 403 khi role đổi sang bếp; PHT xem trẻ campus B chỉ khi HT cấp `ALL_CAMPUS_VIEW`, picker và phân công vẫn chặn, thu hồi có hiệu lực ngay; ma trận có cột `TEAM_LEADER`; realm role `TEAM_LEADER` giả trong token bị bỏ qua.
- Chưa kiểm chứng: nhánh "đang là tổ trưởng" trên DB thật (dev chưa tạo được phân công này) — kiểm sau khi G94-226 merge.
- Bàn giao G94-226 (bổ sung từ review): migration phải nới `staff_assignments.staff_role` lên ≥ `VARCHAR(21)` (nhánh đó đã nới `VARCHAR(30)`); `AccountServiceImpl.getMyAccount` và `StaffAccessPolicy.checkCanAssign` dùng `CareNestRole.valueOf(staffRole.name())` sẽ lỗi với `AGE_GROUP_TEAM_LEADER` ⇒ map StaffRole→CareNestRole tường minh; test nhánh tổ trưởng trên PostgreSQL.
- Tác động FE: `docs/quality/reports/2026-10-27-fe-impact-survey-auth.md`.

### 2026-10-27 — Phase 4 FE (chờ review)
- Repo `CareNest_FE`, nhánh `feature/G94-209-FE-FEAT-53-permission-management` (worktree `.commit-worktrees/permission-auth`), merge dev commit `4fb1ded` (user cho phép); phần sửa chưa commit. Plan FE: `CareNest_FE/docs/plans/active/2026-10-27-survey-auth-web-access.md`.
- Phạm vi user chọn: P0 + màn phân quyền nối API thật. Còn lại (tổ trưởng, bếp rời menu, `areas`, `/users`→`/accounts`) phase sau.
- Verify FE: các suite test liên quan pass, `npm run check` pass; `test:menu` 1 fail có sẵn trên dev (không liên quan). Chưa test trình duyệt.

### 2026-10-30 — Bổ sung thiết kế phase 6–9 (draft, chờ duyệt)
- Brainstorm với user 29–30/10 (đối chiếu luồng nghiệp vụ sau khảo sát với auth BE/FE): quyết định 8–15, AC-9..14, phase 6–9. Status về `draft` tới khi user duyệt lại.
- Phát hiện khi đối chiếu: G94-236/226 `AttendanceDayRules.canUnlock` (ghi) gọi `canViewCampus` ⇒ với Phase 2 hiện tại, PHT có `ALL_CAMPUS_VIEW` mở lại điểm danh được cơ sở khác sau merge. Xử lý ở Phase 8.
- `BUSINESS_RULES.md` chưa sửa: nhánh này thiếu AUTH-20..23 của `dev`; sửa ở Phase 6 sau khi merge `dev`.
- Code: chưa sửa. Commit: chưa.

### 2026-10-30 — Phase 6–9 approved
- User duyệt plan bổ sung trong phiên ("Duyệt plan", 30/10).
- Tiếp: điều kiện trước đợt 1 (commit phần dở + merge `dev`) khi user cho phép trong tin nhắn lúc làm, rồi Phase 6.

### 2026-10-30 — Điều kiện trước đợt 1 (user cho phép: commit bằng G94-60 + merge dev)
- Commit `388b77b` (phase 2, 3, 5 + plan + báo cáo FE); không đưa `MEAL_NUTRITION_AI_*`, `ADR-0018` (mảng dinh dưỡng, để nguyên untracked). Merge `origin/dev` ⇒ `dd4af6d`, không xung đột; `dev` chỉ đổi docs + `.githooks/pre-push`, không thêm migration (V10 chưa trùng số).
- Verify trước commit và sau merge: `VERIFY PASS full | tests 327 fail 0 err 0 skip 38 | archunit ok | snapshot SKIP | spotless ok`. Docker Desktop không khởi động được (mở 2 lần, WSL `docker-desktop` Stopped) ⇒ 38 integration test + snapshot **chưa kiểm chứng lại hôm nay** (lần PASS đủ gần nhất: 27/10, cùng code).
- `check-ai-layer`: 0 lỗi, 4 cảnh báo E2 có sẵn từ `dev` (plan `2026-10-29-survey-full-alignment.md` trỏ file facility đã xóa).

### 2026-10-30 — Phase 6 (chờ review)
- Commit: commit ghi tri thức + Progress log này (sau `4a829ee`)
- Sửa: `BUSINESS_RULES.md` (AUTH-03 cùng cơ sở + dạy thay; AUTH-21 quên mật khẩu chặn im lặng, chặn App để sau; AUTH-22 không chặn tài khoản thứ hai), `USER_ROLES.md` (dòng GV), `modules/identity-access.md` (AUTH-01..23), `context/CURRENT_STATE.md` (dòng identity-access).
- Khác plan: chưa sửa `AUTH_CONTRACT.md`, `SECURITY.md` — hai file mô tả hành vi code hiện có; sửa cùng code ở Phase 7 (quên mật khẩu) và Phase 8 (`canRead*`) để docs không đi trước code.
- Tiếp: Phase 7 sau khi user review.

### 2026-10-30 — Phase 7 (chờ review)
- Commit: `606c8a7`
- Sửa: `AuthServiceImpl.forgot` lọc thêm `mayUseWeb` (`primary_role` null hoặc thuộc `WEB_ROLES`) ⇒ email bếp/phụ huynh đi nhánh như không tồn tại: token OTP không gắn user, không gửi OTP, cùng response; `resend`/`verify`/`reset` theo token nên không dùng được. Test `AuthServiceImplTest#appOnlyRecoveryLooksLikeUnknownEmailAndSendsNoOtp` (AC-9, fail trước khi sửa). Docs: `AUTH_CONTRACT.md` (dòng forgot), `BUSINESS_RULES.md` AUTH-21 + `CURRENT_STATE.md` (trạng thái code).
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 328 fail 0 err 0 skip 38 | archunit ok | snapshot SKIP | spotless ok | 53s` (lần đầu FAIL spotless, đã `mvnw spotless:apply`). Docker tắt ⇒ 38 integration test + snapshot chưa kiểm chứng; thay đổi không đụng DTO/endpoint nên snapshot không đổi.
- Khác plan: email dùng chung cho một TK Web và một TK bếp/phụ huynh trước đây không gửi OTP (2 kết quả); nay TK Web nhận OTP vì TK App bị lọc trước khi đếm.
- Tiếp: Phase 8 (`canRead*`) sau khi user review.

### 2026-10-30 — Phase 8 (chờ review)
- Commit: `4a829ee`
- Sửa: `CampusAccessPolicy` (`canViewCampus` về cơ sở mình; thêm `canReadCampus`/`checkCanReadCampus` có `ALL_CAMPUS_VIEW`), `ClassroomAccessPolicy` (`canReadClassroom`/`checkCanReadClassroom`, helper `isAssignedTeacher`, `inCampus`), `ChildAccessPolicy` (`canReadChild`/`checkCanReadChild`, helper `canAccessChild`), `ChildServiceImpl.getChildProfile` ⇒ `checkCanReadChild`. Test: `CampusAccessPolicyTest` (AC-1, AC-2, AC-4 chuyển sang `canReadCampus`; AC-10 `allCampusViewNeverWidensActionScope`), `ClassroomAccessPolicyTest`, `ChildAccessPolicyTest` (đọc liên cơ sở được, thao tác không), `ChildServiceImplTest`. Docs: `SECURITY.md` (bảng policy + quy ước `canView*`/`canRead*`), `modules/identity-access.md` (Known pitfalls), `backend-coding-guide.md` (mục scope), `CURRENT_STATE.md`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 333 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 52s` (Docker đã chạy ⇒ lần này phủ cả commit `388b77b`, merge `dd4af6d`, Phase 7). `check-ai-layer`: 0 lỗi, 4 cảnh báo có sẵn từ `dev`.
- AC-11 kiểm khi module ghi (G94-236 `canUnlock`) vào `dev`: hàm đó gọi `canViewCampus` nên giờ chỉ cơ sở mình.
- Consumer FE: không đổi contract; `GET /children/{id}` cho PHT có `ALL_CAMPUS_VIEW` như trước.
- Tiếp: đợt 2 (Phase 9, AUTH-03) sau khi G94-236/226 vào `dev`.

### 2026-10-30 — Merge `dev` sau G94-226/236 (chờ review, chưa commit merge)
- User cho phép: merge `origin/dev` (PR #2 G94-226, đã gộp G94-236) + đổi migration V10 → V38; không commit merge, chờ review.
- Xung đột 11 file đã giải: code `AuthController` (refresh xóa cookie khi HTTP 401 hoặc 403), `AuthServiceImpl` (mã lỗi mới `ACCOUNT_NOT_PROVISIONED`/`ACCOUNT_INACTIVE` + giữ `ensureWebRole` theo `primary_role`), `ChildServiceImpl` (`checkCanReadChild` + `CHILD_NOT_FOUND`); 2 test policy; docs `BUSINESS_RULES` (AUTH-03 gộp quyết định 30/10 + trạng thái code G94-236), `USER_ROLES`, `AUTH_CONTRACT`, `CURRENT_STATE`, coding guide; `openapi.yaml` sinh lại = bản `dev` + đúng 23 dòng enum `ALL_CAMPUS_VIEW`/`TEAM_LEADER` của nhánh.
- Theo `dev` (constructor `GlobalException` private): `checkCanRead*` dùng cùng mã với `checkCanView*` (`CAMPUS_/CLASS_/CHILD_ACCESS_DENIED`); chặn Web cho bếp/phụ huynh dùng `FORBIDDEN` 1101 (chưa có mã riêng). `PermissionServiceImplTest` cột Tổ trưởng chờ `PERMISSION_NOT_CONFIGURABLE_FOR_ROLE`.
- Migration: `V10__team_leader_permission_column.sql` → `V38__...` (dev đã có V10–V37); docs trỏ V38. Bản V10 cũ trong `target/classes` gây `Found more than one migration with version 10` (ENV-261016).
- AC-11: `AttendanceIntegrationTest#lockedAfterCutoffUntilUnlockedAndAfterRevoke` thêm PHT cơ sở B có `ALL_CAMPUS_VIEW` mở lại điểm danh cơ sở A ⇒ 403.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 570 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 342s`. `check-ai-layer`: 0 lỗi, 5 cảnh báo E2 có sẵn từ `dev`.
- Tiếp: user review rồi commit merge; Phase 9 (AUTH-03 policy chung, thay `AttendanceDayRules.taughtClosedYear`) — chốt file, >8 file thì tách plan.
- User chốt (30/10): giữ `FORBIDDEN` 1101 cho chặn role Web, không thêm `ApiCode` riêng. FE phân biệt với lỗi CSRF bằng cách lấy lại CSRF và gửi lại 1 lần; ghi ở `AUTH_CONTRACT.md` mục Tác động FE/APP.
