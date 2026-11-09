---
title: Màn giao việc cho Phó hiệu trưởng theo mảng (Web)
status: in-progress
owner: thangnd
jira: G94-209 / FE-FEAT-53 (luồng phân quyền FE)
branch: feature/G94-209-FE-FEAT-53-permission-management (FE, worktree `.commit-worktrees/permission-auth`); BE feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access]
rules: [AUTH-02, AUTH-07, AUTH-10, AUTH-11, AUTH-12, AUTH-13, AUTH-14, AUTH-20]
risk: medium
created: 2026-11-08
updated: 2026-11-08
---
# Màn giao việc cho Phó hiệu trưởng theo mảng (Web)

## Context
HT là giáo viên, không rành công nghệ. Màn `/school/vice-principals` liệt kê từng mã quyền với "Được làm / Không được làm" nên khó hiểu:
- Không có quyền xem: bảng chỉ có mã sửa (`VicePrincipalCard.jsx:24-30`), trong khi PHT xem được cả trường (AUTH-20). "Không được làm" ở dòng thực đơn bị đọc thành "không đụng được bán trú".
- Không hiện nơi áp dụng. `getVicePermissions` bỏ `scope` (`schoolConfigApi.js`), wizard không chọn được (`permissionApi.js:62`), dù BE đã nhận `CAMPUS`/`SCHOOL` (`PermissionServiceImpl.java:325`, ADR-0023).
- Thuật ngữ kỹ thuật: "So với vai trò", "Cấp thêm", "campus". Nhãn lấy từ `description` của enum BE.
- Cùng một việc nằm ở 3 màn: `/school/roles`, `/permissions/overrides`, `/school/vice-principals` (`PermissionTabs.jsx`).
- FE gom nhóm theo `feature`, `requires` (`permissionMatrix.js:34`, `:97`) nhưng BE không trả hai trường này (`PermissionResponse.java`) ⇒ ma trận thành danh sách phẳng.
- PHT thật mang mảng demo, không theo mã BE: `authService.js:110` → `withPermissions` (`authMockRepository.js:43`) gán `children: RESTRICTED`. HT tắt `CHILD_*` thì PHT vẫn thấy nút, bấm mới 403.

## Quyết định đã chốt
1. Hướng A: giao việc theo mảng, tách Xem và Sửa, hiện nơi áp dụng, có giao nhanh, có bảng "ai phụ trách gì" — user, 2026-11-08.
2. Gom mảng làm ở FE, BE vẫn lưu từng mã, không đổi API — đề xuất đã trình, user chốt cùng hướng A, 2026-11-08.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT giao việc cho từng PHT · PHT biết mình xem gì, sửa gì, ở đâu. Rule AUTH-02, AUTH-10..14, AUTH-20 (CONFIRMED), AUTH-07 (ACCEPTED). Flow: `[B]` HT quyết ai phụ trách mảng → `[S]` HT giao trên Web, nhập lý do → `[S]` có hiệu lực ở request kế tiếp.
- **Đối chiếu thiết kế ban đầu:** không đổi rule. `USER_ROLES.md` đã mô tả "mảng không được giao chỉ xem, dữ liệu cả trường"; màn hiện tại chưa thể hiện.
- **Ảnh hưởng:** identity-access (chỉ đọc, BE không đổi code). FE: `/school/vice-principals`, `/school/roles`, `/permissions/overrides`, màn hồ sơ trẻ. APP: không. Dữ liệu cũ: ngoại lệ đã lưu giữ nguyên, đọc lại theo mảng.

### Câu hỏi mở
- [x] 1. Nhánh FE: `feature/G94-209-FE-FEAT-53-permission-management` chậm `dev` 331 commit, có 19 file dở từ 27/10, merge `dev` xung đột từ 30 file trở lên. Tạo nhánh mới cùng luồng từ `dev`, hay merge `dev` vào nhánh cũ?

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Hướng thiết kế màn quyền | Hướng A | user | 2026-11-08 | plan này |
| Nhánh FE | Merge `dev` vào nhánh G94-209 | user | 2026-11-08 | plan này |

### Giả định (tự chọn theo chế độ tự chạy, user soát)
- **Mảng và nơi áp dụng** (bảng dưới) suy từ `Permission.java` và `meal-management-permissions.md` §4.
- **Không đi vòng Spec Kit:** đây là thiết kế lại UI cho chức năng có sẵn, không có rule mới. User muốn coi là feature mới thì bổ sung `domain-spec.md`.
- **Giáo viên giữ màn cũ:** GV chỉ có `CHILD_PROFILE_VIEW` cấu hình được. `/permissions/overrides` còn phục vụ GV, đổi tên thành "Ngoại lệ giáo viên"; PHT chuyển hết sang màn giao việc. Nên thực tế vẫn 3 tab, khác "còn 2 màn" đã nói với user.
- **`CHILD_PROFILE_VIEW` không khóa ở BE:** bỏ PHT khỏi `configurableRoles` sẽ làm PHT mất luôn quyền, vì PHT không phải role cố định (`EffectivePermissionResolver.java`, `Permission.java`). FE ẩn khi đang bật, hiện cảnh báo khi đang tắt.
- **Sửa mockup:** chữ "Chưa ai sửa được" sai, vì HT luôn làm được. Đổi thành "Chưa giao".
- **Mảng chuyên môn** (khen thưởng, xếp loại) chưa có mã quyền ⇒ ngoài plan này, chờ user chốt.

| Mảng | Việc (mã) | Nơi áp dụng |
| --- | --- | --- |
| Bán trú | Thực đơn tuần (`MENU_MANAGE`), Danh mục (`NUTRITION_MANAGE`) | Cố định "Cả trường" |
| Bán trú | Số suất (`MEAL_COUNT_CONFIRM`), Kho và hàng tươi (`STOCK_MANAGE`), Lưu mẫu (`FOOD_SAFETY_APPROVE`) | Chọn: điểm trường phụ trách hoặc Cả trường |
| Hồ sơ trẻ và phụ huynh | `CHILD_ENROLL`, `CHILD_PROFILE_EDIT`, `CHILD_PLACEMENT`, `PARENT_ACCOUNT_MANAGE` | Cố định điểm trường phụ trách |
| Nhân sự | `STAFF_ASSIGNMENT_MANAGE`, `ACCOUNT_MANAGE`, `ACCOUNT_STATUS_MANAGE` | Cố định điểm trường phụ trách |

## Acceptance criteria
- **AC-1** (`AUTH-20`): Given PHT còn phân công, không có mã sửa nào When HT mở thẻ Then thẻ ghi "Xem được dữ liệu của cả trường" và mọi mảng ghi "Chỉ xem", không có chữ "Không được làm".
- **AC-2** (`AUTH-10`): Given mã mặc định tắt When bật Then lưu GRANT. Given mã mặc định bật When tắt Then lưu REVOKE. Given giá trị trùng mặc định Then không lưu ngoại lệ.
- **AC-3** (`AUTH-02`, `AUTH-20`): Given `MEAL_COUNT_CONFIRM` mặc định bật When chọn "Cả trường" Then lưu GRANT `SCHOOL` dù trùng mặc định. When chọn lại điểm trường Then bỏ ngoại lệ.
- **AC-4** (`AUTH-20`): Given mã không theo mảng hoặc REVOKE When lưu Then không gửi `scope`. Mã không `campusScoped` không có ô chọn nơi áp dụng.
- **AC-5** (`AUTH-10`): Given bấm "Phụ trách dinh dưỡng" Then bật đủ 5 việc Bán trú, không đổi mảng khác, chưa lưu tới khi HT xác nhận.
- **AC-6** (`AUTH-14`): Given có thay đổi When lưu Then bắt buộc lý do, gọi một lần `PUT /accounts/{id}/permission-overrides`, lịch sử có dòng trước/sau.
- **AC-7** (`AUTH-10`): Given BE thêm mã mới chưa có trong bảng mảng Then mã hiện ở nhóm "Việc khác", không bị ẩn.
- **AC-8** (`AUTH-20`): Given không PHT nào giữ mã tại điểm trường Then bảng tổng quan ghi "Chưa giao"; GRANT `SCHOOL` được tính cho mọi điểm trường.
- **AC-9** (`AUTH-10`): Given đang xem năm không ACTIVE When mở thẻ Then chỉ đọc, vì quyền theo người chứ không theo năm.
- **AC-10** (`AUTH-20`): Given `CHILD_PROFILE_VIEW` của PHT đang tắt Then thẻ cảnh báo và cho bật lại, không ghi "Xem được cả trường" cho hồ sơ trẻ.
- **AC-11** (`AUTH-07`, `AUTH-20`): Given HT tắt `CHILD_ENROLL` của PHT Then mục "Tiếp nhận trẻ" và nút tương ứng ẩn. Given PHT không có mã sửa của trang Then trang ghi "Bạn đang chỉ xem".
- **AC-12** (`AUTH-20`): Given PHT mở danh sách trẻ Then thấy cột, bộ lọc điểm trường như dữ liệu BE trả.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Bố cục | A: thẻ theo mảng | B trợ lý hỏi đáp; C bảng so sánh | A dễ dùng mà còn chi tiết; lấy phần tổng quan của C |
| Nơi giữ bảng mảng | Hằng số FE, có nhóm "Việc khác" | BE thêm `area` vào catalog | Không đổi contract; là cách hiển thị, không phải rule |
| Hồ sơ trẻ của PHT | `areaAccess` đọc `bePermissions` | BE trả cờ `canX` từng trẻ | Một file, không đổi contract |

## Phases
### Phase 1 — Logic mảng, chưa đổi giao diện
- Files: C `src/models/account/vpTaskAreas.js` · C `src/utils/account/vpTasks.js` · M `src/services/school-config/api/schoolConfigApi.js` (`getVicePermissions` trả `scope`, `campusScoped`) · M `src/models/account/permissionConstants.js` (nhãn 3 mã `CHILD_*`) · C `tests/auth/vp-tasks.test.mjs`
- Steps: 1. Bảng mảng và nơi áp dụng. 2. Hàm đọc trạng thái mảng từ `items`. 3. Hàm dựng `overrides` kèm `scope` (AC-2..4), xử lý riêng mã mặc định bật nhưng cần `SCHOOL`. 4. Hàm tính bảng tổng quan.
- Tests: AC-2, AC-3, AC-4, AC-5, AC-7, AC-8 → `tests/auth/vp-tasks.test.mjs`
- Exit: FE `node scripts/verify.mjs` → `VERIFY PASS`, skip 0
- **Dừng chờ user review.**

### Phase 2 — Giao diện giao việc
- Files: C `src/components/permissions/VpTaskPanel.jsx` · C `src/components/permissions/VpCoverageCard.jsx` · M `src/contexts/PermissionGrantContext.jsx` · M `src/components/school-config/VicePrincipalCard.jsx` · M `src/pages/school-config/VicePrincipalAssignmentPage.jsx` · M `src/components/permissions/PermissionTabs.jsx` · M `src/components/permissions/OverrideWizardModal.jsx` (chỉ còn GV) · M `src/styles/modules/school-config.css`
- Steps: 1. Thẻ có 2 dòng tóm tắt, 3 mảng, "Chọn từng việc". 2. Giao nhanh, thanh lưu, lý do dùng `ReasonModal`. 3. Bảng tổng quan. 4. Đổi chữ theo bảng đã trình user. 5. Nhánh demo (`SCHOOL_CONFIG_API` tắt) giữ nguyên.
- Tests: AC-1, AC-6, AC-9, AC-10 → `tests/auth/vp-task-panel.test.mjs`; soát tay bằng trình duyệt với tài khoản HT dev
- Exit: FE `node scripts/verify.mjs` → `VERIFY PASS`, skip 0
- **Dừng chờ user review.**

### Phase 3 — PHT thấy đúng quyền của mình
- Files: M `src/utils/school-config/schoolConfigPermissions.js` · M `src/utils/children/childrenPermissions.js` · M `src/layouts/menuConfig.js` · C `src/components/ui/ReadOnlyNotice.jsx` · M `tests/menu/vp-be-areas.test.mjs` · BE M `docs/system/CROSS_REPO_MAP.md`
- Steps: 1. `areaAccess('children')` đọc `CHILD_*` với người dùng thật. 2. Tách `canEditChild`, `canPlaceChild` theo đúng mã. 3. Cột, bộ lọc điểm trường theo AUTH-20. 4. Dòng "Bạn đang chỉ xem" ở trang hồ sơ trẻ, bán trú.
- Tests: AC-11, AC-12 → `tests/menu/vp-be-areas.test.mjs`
- Exit: FE `node scripts/verify.mjs` → `VERIFY PASS`, skip 0; BE `node scripts/check-ai-layer.mjs` pass
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-2..5, 7, 8 | AUTH-02, 10, 20 | `vp-tasks.test.mjs` (unit) | 1 | chưa chạy |
| AC-1, 6, 9, 10 | AUTH-10, 14, 20 | `vp-task-panel.test.mjs` (component) | 2 | chưa chạy |
| AC-11, 12 | AUTH-07, 20 | `vp-be-areas.test.mjs` (unit) | 3 | chưa chạy |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| PUT thay toàn bộ ngoại lệ, gửi thiếu là mất | PHT mất quyền đã giao, hoặc `SCHOOL` hạ về `CAMPUS` | Dựng từ danh sách đầy đủ, giữ mã ngoài bảng mảng; test AC-3, AC-7 | Lịch sử có trước/sau, HT giao lại; revert FE |
| Bật cả mảng mở quyền ngoài ý | PHT sửa được nhiều hơn dự định | Chỉ lưu sau xác nhận, hộp xác nhận liệt kê từng việc | HT tắt lại, hiệu lực request kế tiếp |
| Phase 3 ẩn nhầm nút của PHT | PHT không làm được việc | Test theo từng mã; BE vẫn là chốt chặn | Revert riêng Phase 3 |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ không đổi contract BE
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file ⇒ đã chia 3 phase dừng review; không tách plan vì cùng một màn
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan · không câu mơ hồ

## Progress log
### 2026-11-08 — Plan (chờ user duyệt)
- Commit: chưa commit
- Verify: chưa chạy, chưa sửa code
- Khác plan: user yêu cầu làm trên nhánh phân quyền. BE đã sang G94-60; 2 file docs dở cất ở stash "docs dở trước khi sang G94-60" vì không áp sạch lên nhánh này.
- Tiếp: làm Phase 1 ở worktree FE. Không dùng folder `CareNest_FE` chính: đang có việc khác dở (plan boarding 08/11).

### 2026-11-08 — Phase 1 (chờ review)
- Commit: FE merge `2afa20a` (cây file trùng `dev`); code Phase 1 chưa commit
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify | log verify.log`
- Khác plan: test ở `tests/auth/` vì `npm run verify` không chạy `tests/account/`. Nhãn lời thường đặt ở `vpTasks.js`, không ở `schoolConfigApi.js`: ESLint cấm module school-config import code account. Vì vậy Phase 2 đặt panel ở `components/permissions/`, mở qua `PermissionGrantContext`.
- Việc dở 27/10 của nhánh cất ở stash FE "G94-209 việc dở 27/10 trước khi merge dev", chưa đối chiếu với `dev`.
- BE G94-60 chậm `dev` 149 commit nên `check-ai-layer` của FE báo 18 link BE gãy và chặn commit FE; commit merge phải đưa BE về tạm G94-192 rồi trả lại. Merge `dev` vào G94-60 có 4 file xung đột.
- Tiếp: user cho làm Phase 2.

### 2026-11-08 — Phase 2 (chờ review)
- Commit: chưa commit
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify | log verify.log`
- Soát trình duyệt: `VpTaskPanel`, `VpCoverageCard` chạy trên trang thử ngoài repo (component, CSS thật; đăng nhập, lưu là stub): giao nhanh, chọn từng việc, "Cả trường" gửi GRANT SCHOOL, lý do bắt buộc, 375px không tràn; sửa lỗi nút bị cắt chữ. Trang thật ở chế độ mock mở được, tab đổi tên đúng.
- Chưa kiểm: màn thật nối BE. Keycloak local từ chối tài khoản dev theo README (`invalid_grant`), nên chưa chạy `useVicesPermissions`, `VpCoverageCard` trong trang, lưu thật, chặn rời trang (`useLeaveGuard`).
- Khác plan: không mở qua `PermissionGrantContext` mà thêm 4 dòng vào bảng ngoại lệ module ở `.eslintrc.cjs`, như `RoleDefaultsPage.jsx`; xóa `PermissionGrantContext.jsx` vì hết người dùng. Thêm `UserOverridesPage.jsx`: sửa PHT chuyển sang màn Phân công, vì wizard làm mất phạm vi cả trường của mã mặc định bật. Test ở `tests/auth/vp-task-panel.test.mjs`.
- Tiếp: soát màn thật với tài khoản HT, user review, rồi Phase 3.

### 2026-11-08 — Phase 3 (chờ review)
- Commit: chưa commit
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify | log verify.log`; test mới `tests/auth/vp-children-permissions.test.mjs` 4/4
- Đã làm: người dùng BE có tiếp nhận, sửa hồ sơ, xếp lớp/cho nghỉ, tài khoản phụ huynh, mỗi việc theo đúng mã `CHILD_*`/`PARENT_ACCOUNT_MANAGE`, sửa chỉ ở điểm trường phụ trách. PHT xem trẻ mọi điểm trường, có cột và bộ lọc điểm trường. Mục menu "Tiếp nhận", "Xếp lớp" ẩn theo mã. Dòng "Bạn đang chỉ xem" ở danh sách trẻ, danh mục bán trú, thực đơn tuần, kho và hàng tươi, lưu mẫu khi PHT không có mã sửa nào của trang. Bản demo giữ cách cũ.
- Khác plan: không tạo `ReadOnlyNotice.jsx`; dòng báo đặt ở `AreaGate` (một chỗ cho mọi trang), bảng đường dẫn ở `src/utils/account/vpReadOnlyPages.js`. Test ở `tests/auth/` thay vì sửa `vp-be-areas.test.mjs`. Chưa soát trình duyệt với tài khoản BE (cùng lý do Phase 2).
- Tiếp: user soát màn thật, review, cho phép commit.

### 2026-11-08 — Merge `dev` vào BE G94-60 (user yêu cầu)
- Commit: `8ad24b6` (chưa push)
- Verify: BE `node scripts/verify.mjs` → `VERIFY PASS full | tests 1185 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 541s | log target/verify.log`
- Xung đột: `ApiCode.java` (mã đổi vai trò AUTH-25 trùng số với `dev` ⇒ đổi 1130→1133, 1131→1134, 1132→1135, 2322→2326; cập nhật `AUTH_CONTRACT.md`, `CROSS_REPO_MAP.md`, plan `2026-11-02-staff-role-change.md`), `StaffingServiceImplTest` (giữ test của cả hai bên), `identity-access.md` (ghép), `openapi.yaml` (sinh lại bằng `OpenApiSnapshotTest`, +81 dòng so với `dev` là 2 endpoint AUTH-25).
- Đã áp lại 2 stash docs (giữ nguyên trong stash list, chưa xóa).

### 2026-11-08 — Soát màn thật (Phase 2, 3)
- Môi trường: FE worktree G94-209 nối bộ docker `main_seed` (API 8090, Keycloak 8190), tài khoản dev `principal`, `viceprincipal` do user cung cấp.
- HT: bảng "Ai phụ trách việc gì", thẻ giao việc, chọn "Cả trường", lý do bắt buộc, chặn rời trang khi chưa lưu đều đúng. Lưu thật: `PUT /accounts/3/permission-overrides` 200, BE lưu `GRANT STOCK_MANAGE scope SCHOOL`; đã trả lại (overrides rỗng).
- PHT: `/menu/foods` hiện "Bạn đang chỉ xem", ẩn "Thêm thực phẩm"; `/kitchen/food-samples` hiện dòng báo; `/kitchen/dry-stock` (có mã) không báo, có "Phiếu nhập"; danh sách trẻ có cột "Điểm trường".
- Lỗi tìm được và đã sửa: (1) một PHT lỗi tải quyền (BE 503, tài khoản `th.pht.b` không có trên Keycloak 8190) làm hỏng mọi thẻ ⇒ nạp từng người (`Promise.allSettled`), bảng tổng quan ghi thiếu ai; (2) `VpTaskPanel` lặp render khi có thay đổi chưa lưu ⇒ giữ `onDirty` trong ref; (3) đăng nhập dev dựng user thiếu `bePermissions` ⇒ dùng `toWebUser`. Ghi tri thức: FE `FE-ENV-261108-dev-login-real-permissions`.
- Chưa kiểm được: dữ liệu hồ sơ trẻ thật (module trẻ ở bản build này còn demo); PHT `th.pht.b` (lỗi dữ liệu seed, không phải code).
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify | log verify.log` (sau lần sửa cuối).

### 2026-11-08 — Làm lại bố cục: bảng Việc × Người
- Lý do (user soát màn): thẻ 2 cột cao thấp lệch nhau, nút mảng và nút việc không cùng cột, cột nơi áp dụng lúc chữ lúc ô chọn, so 2 PHT phải đảo mắt; bảng "Ai phụ trách việc gì" lặp thông tin.
- Quyết định (tự chọn phương án khuyến nghị A, chế độ tự chạy): một bảng dùng chung khuôn `perm-table` của màn "Quyền theo vai trò" — dòng = việc theo mảng, cột = từng PHT đã có điểm trường. Mỗi ô 1 ô chọn: "Chỉ xem" / "Được sửa · <điểm trường>" / "Được sửa · Cả trường" (chỉ mã `campusScoped` có cả hai nơi). Dòng mảng có ô chọn cả mảng ("Sửa x/y việc" khi giao một phần).
- Bỏ thẻ "Ai phụ trách việc gì": việc Bán trú / Hồ sơ trẻ chưa ai được sửa ở điểm trường nào thì cảnh báo ngay dưới tên việc (mảng Nhân sự không cảnh báo — mặc định Hiệu trưởng làm).
- Giao nhanh (user yêu cầu): bấm xong người đó có nhãn (vd. "Phụ trách dinh dưỡng") cạnh tên, nút giao nhanh đó ẩn; nhãn suy ra từ việc đang giao (đủ mọi việc của mảng) nên bỏ bớt việc thì nhãn tự mất; nhãn có nút bỏ (về chỉ xem cả mảng). Không lưu nhãn ở BE.
- Lưu (user yêu cầu): một thanh lưu chung; bấm "Xem lại và lưu" mở modal liệt kê theo từng người: việc, trước → sau, nhãn thêm/bỏ; nhập lý do rồi "Xác nhận lưu". Gọi PUT từng người có thay đổi; người lỗi giữ nguyên nháp, báo tên.
- PHT chưa có điểm trường vẫn hiện thẻ cũ (nút Phân công) dưới bảng. Bản mock (SCHOOL_CONFIG_API=false) giữ thẻ cũ.
- Đã làm (FE, chưa commit): `VpTaskMatrix` thay `VpTaskPanel` + `VpCoverageCard` (đã xóa); `ReasonModal` nhận `children`/`confirmLabel`/`size` cho bản xem trước; `vpTasks` thêm ô chọn, nhãn giao nhanh, bản xem trước, cảnh báo việc chưa ai sửa; bỏ `VP_COVERAGE_ROWS`, thêm cờ `checkGaps`. Bản mock giữ thẻ cũ.
- Soát màn thật (HT, stack 8090/8190): bảng thẳng cột; bấm "Phụ trách dinh dưỡng" ⇒ nhãn hiện, nút ẩn, thanh lưu "4 thay đổi · 1 người"; modal liệt kê trước → sau; lưu thật 200, nhãn giữ sau tải lại; bỏ nhãn + bật lại số suất ⇒ lưu trả về đúng trạng thái ban đầu. PHT `th.pht.b` vẫn lỗi tải (dữ liệu seed) ⇒ cột báo lỗi, ghi chú trên bảng.
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full` (sau lần sửa cuối).

### 2026-11-09 — Tách 2 tab: Phân công điểm trường | Giao việc
- Lý do (user soát): một màn vừa đổi điểm trường vừa giao việc vẫn khó dùng. User chọn kiểu "Phân công | Giao việc" trong 3 cách hiểu (mỗi PHT một tab; trả về 2 tab Phân quyền cũ; 2 tab trên màn PHT).
- Tab "Phân công điểm trường" (mặc định): bảng mỗi PHT một dòng — tên, điểm trường (kèm thời điểm/người phân công khi BE có), nút "Đổi điểm trường"/"Phân công" và "Giao việc" (sang tab 2). Cảnh báo điểm trường chưa có PHT chỉ ở tab này.
- Tab "Giao việc": bảng Việc × Người như trước, bỏ nút "Đổi điểm trường" ở đầu cột. PHT chưa có điểm trường ⇒ thông báo kèm nút về tab 1. Tab hiện "Chưa lưu" khi có nháp.
- Tab nằm trên URL (`?tab=tasks`), đổi bằng `guard.step` nên không hỏi rời trang; cả 2 tab luôn gắn trên trang (ẩn bằng `hidden`) để đổi tab không mất nháp.
- Đã làm (FE, chưa commit): thêm `ViceCampusTable`; `VicePrincipalCard` chỉ còn cho bản mock; `VpTaskMatrix` bỏ prop `onChangeCampus`; test `vice-campus-table.test.mjs`.
- Soát màn thật (HT, 1366px): tab 1 thẳng cột; "Giao việc" ở dòng ⇒ sang tab 2; giao nhanh rồi đổi tab qua lại ⇒ nháp và nhãn còn, không bật hộp rời trang; "Bỏ thay đổi" ⇒ về như cũ (không lưu gì).
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full` (sau lần sửa cuối).

### 2026-11-09 — Bỏ tab: một bảng + modal giao việc từng người
- Lý do (user soát): tab 1 thừa khoảng trống; muốn nhãn hiện ngay ở bảng ngoài, "Giao việc" mở modal to thay cho tab.
- Bảng Phân công PHT (không còn tab): cột Phó hiệu trưởng | Điểm trường phụ trách | Nhãn | Được sửa (từng mảng: Sửa cả mảng / Sửa x/y việc / Chỉ xem) | Thao tác (Đổi điểm trường, Giao việc). Người lỗi tải việc ⇒ báo + Thử lại, nút Giao việc khóa.
- Trên bảng: dòng thu gọn "N việc chưa giao cho Phó hiệu trưởng nào — Hiệu trưởng tự làm" (bấm mở danh sách), thay cảnh báo dưới tên việc của bảng cũ.
- Modal "Giao việc cho <tên>" (cỡ xl, 1100px): nhãn + giao nhanh ở đầu; bảng Việc | <người này> (ô chọn) | Phó hiệu trưởng khác đang sửa (tên · nơi, hoặc cảnh báo chưa ai sửa ở điểm trường nào — tính cả lựa chọn đang chỉnh). Chân modal: số thay đổi, Bỏ thay đổi, Đóng, Xem lại và lưu ⇒ modal xem trước chồng lên (trước → sau, nhãn thêm/bỏ, lý do bắt buộc) ⇒ PUT một người. Đóng khi còn nháp ⇒ hỏi "Bỏ thay đổi chưa lưu?".
- Đã làm (FE, chưa commit): `VpTaskModal.jsx` (xuất `VpTaskModal`, `VpLabelChips`, `VpAreaSummary`, `VpGapsNote` — trang school-config không được import `utils/account` nên phần đọc vpTasks nằm ở module phân quyền); bỏ `VpTaskMatrix.jsx` + test; mục nợ lint đổi tên sang `VpTaskModal.jsx` (không thêm mục mới); `ViceCampusTable` thêm cột Nhãn/Được sửa; test `vp-task-modal.test.mjs`, `vice-campus-table.test.mjs`.
- (Đã thay bằng bố cục 2 bên ở mục 2026-11-09 kế tiếp.) Soát màn thật (HT, 1600px): bảng gọn, nhãn hiện ngoài; modal 1100px; giao nhanh ⇒ nhãn hiện, nút ẩn, "Có 4 thay đổi"; xem trước chồng modal đúng 4 dòng; lưu thật 200 ⇒ bảng ngoài cập nhật nhãn; Đóng khi có nháp ⇒ hỏi; hoàn tác (bỏ nhãn + bật lại số suất Cơ sở 1) lưu ⇒ về đúng trạng thái ban đầu.
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full` (sau lần sửa cuối).

### 2026-11-09 — Bố cục 2 bên + lưới công tắc
- Lý do (user soát): cột "Nhãn" khó hiểu; user chọn bố cục A "chia 2 bên" và yêu cầu thiết kế lại lưới chọn quyền cho dễ dùng.
- Quyết định (tự chọn kiểu khuyến nghị, chế độ tự chạy): lưới kiểu 1 "công tắc + nơi ngay cạnh" (bỏ ô chọn thả xuống). Hai kiểu còn lại đã đưa user xem: nút 3 mức mỗi việc (dòng dài, khó lướt); ô đánh dấu theo mảng + nơi chung (không cho từng việc khác nơi, BE cho phép).
- Bố cục: trái danh sách thẻ PHT (tên, "Điểm trường: …", "Phụ trách: dinh dưỡng/chuyên môn/chưa giao", "sửa x/y việc", nút Đổi điểm trường/Phân công điểm trường); phải khung "Việc của <tên>": dòng Giao nhanh (nút "+ Phụ trách …" / chip đã giao có ×), từng mảng một khung có "được sửa x/y việc" + ô "Sửa cả mảng" (3 trạng thái), mỗi việc một công tắc "được sửa"; việc đang bật thì bên phải hiện nơi (chọn Cơ sở/Cả trường cho 3 mã campusScoped, mã khác hiện nơi cố định); dưới tên việc: "PHT khác đang sửa: …" hoặc cảnh báo chưa ai sửa. Thanh lưu dính đáy khung (đổi màu khi có nháp) ⇒ "Xem lại và lưu" mở modal xem trước + lý do.
- Người đang chọn nằm trên URL `?vp=<id>`; mặc định người đầu tiên tải được việc. Đổi người khi còn nháp ⇒ hỏi "Bỏ thay đổi chưa lưu?". Nháp gắn khóa dữ liệu đã lưu nên lưu xong tự bỏ. Chữ "nhãn" đổi thành "Phụ trách"/"Giao nhanh" ở mọi chỗ người dùng thấy.
- Đã làm (FE, chưa commit): `VpTaskEditor.jsx` (xuất `VpPeopleList`, `VpTaskEditor`, `VpGapsNote`); bỏ `VpTaskModal.jsx`, `ViceCampusTable.jsx` và test của chúng; mục nợ lint đổi tên sang `VpTaskEditor.jsx`; test `vp-task-editor.test.mjs`.
- Lỗi gặp: đổi chữ trong `.eslintrc.cjs` bằng `Get-Content -Raw … | Set-Content -Encoding utf8` (PowerShell 5.1) đọc file theo ANSI ⇒ vỡ dấu tiếng Việt + thêm BOM. Sửa: đọc lại UTF-8, mã hóa ngược Windows-1252 về byte gốc, bỏ BOM; `git diff` chỉ còn dòng định đổi. Bài học: sửa file UTF-8 bằng `[IO.File]::ReadAllText/WriteAllText` với `UTF8Encoding($false)` hoặc công cụ Edit, không dùng `Get-Content`/`Set-Content`.
- Soát màn thật (HT, 1600px và 900px): 2 bên thẳng, màn hẹp xếp chồng; bật "Nhập kho khô" + chọn "Cả trường" ⇒ "Bán trú · được sửa 2/5", thanh lưu "Có 1 thay đổi"; bấm người khác ⇒ hỏi, "Ở lại" giữ nháp; lưu thật ⇒ nháp tự bỏ, thẻ trái "sửa 7/12"; hoàn tác lưu ⇒ về "1/5", "6/12" như ban đầu.
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full` (sau lần sửa cuối).
### 2026-11-09 — Badge cấp quyền ở màn Mặc định theo vai trò
- Yêu cầu user: màn `/school/roles` thêm badge màu phân biệt cấp quyền.
- Quyết định (chế độ tự chạy, chỉ hiển thị, không đổi cách kiểm quyền): 4 cấp `PERMISSION_LEVELS` trong `permissionConstants.js` — Xem (xanh dương), Thao tác (xanh lá), Duyệt (cam), Quản trị (đỏ); mỗi `PERMISSION_TASKS` có `level`. Gán: *_VIEW = Xem; tiếp nhận/sửa/xếp lớp trẻ, danh mục bán trú, kho khô, bếp ghi mẫu = Thao tác; xác nhận suất ăn, công bố thực đơn, duyệt mẫu = Duyệt; tài khoản nhân sự/phụ huynh, phân công, phân quyền = Quản trị. User muốn gán khác thì sửa `level`.
- Badge `PermissionLevelBadge` đứng trước tên việc trong `PermissionTable` (nên wizard ngoại lệ giáo viên cũng có), cùng bề rộng cho thẳng cột; thêm dòng chú thích "Cấp quyền:" dưới chú thích ô tick.
- Test `tests/auth/permission-levels.test.mjs` (mọi việc có cấp, 4 màu khác nhau); soát màn thật: 15/15 dòng có badge đúng cấp.
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full` (sau lần sửa cuối).
### 2026-11-09 — Nhãn thẻ PHT, validate đổi điểm trường, modal xem lại sửa được
- Yêu cầu user: thẻ PHT gắn nhãn nổi bật; luồng chọn phân công chưa validate; nút lưu chỉ ghi "Lưu"; modal xem lại cho hoàn tác/sửa ngay.
- Thẻ PHT: nhãn màu thay dòng chữ — điểm trường (xanh dương, icon ghim), mảng phụ trách (xanh ngọc, "Dinh dưỡng"/"Chuyên môn"; chưa có thì xám "Chưa giao mảng"), "Sửa x/y việc" (xanh lá; 0 thì xám); chưa có điểm trường (cam), tải lỗi (đỏ).
- Validate đổi điểm trường (`validateVpAssignment(form, { campuses, currentCampusId, requireChange })`): bắt buộc chọn; chặn điểm trường đã ngừng (BE trả CAMPUS_INACTIVE) — danh sách chỉ hiện điểm trường còn hoạt động; bản API chặn chọn lại đúng điểm trường đang phụ trách (BE coi là không đổi). Lỗi hiện dưới ô, báo lại ngay khi chọn sau lần bấm lưu đầu. Điểm trường đã có PHT khác ⇒ chỉ báo thông tin "cùng phụ trách", không chặn (BE cho phép, BUSINESS_RULES không cấm). So id theo String vì select trả chuỗi.
- Nút "Xem lại và lưu" ⇒ "Lưu"; nút trong modal cũng "Lưu". Modal xem lại: cột "Sau" là ô chọn (Chỉ xem / Được sửa · nơi), mỗi dòng "Hoàn tác", đầu bảng "Hoàn tác tất cả"; hoàn tác hết ⇒ báo không còn thay đổi và khóa nút Lưu (`ReasonModal` thêm `canSubmit`). Helper `revertVpTask`; dòng preview thêm `value`/`options`.
- Test: `vp-assignment-validation.test.mjs` (mới), `vp-tasks.test.mjs` (hoàn tác trong preview), `vp-task-editor.test.mjs` (nhãn màu, nút Lưu) — 24/24 pass.
- Soát màn thật (HT): nhãn hiện đúng 2 thẻ; bật 2 việc ⇒ modal 2 dòng, đổi "Sau" kho khô sang Cả trường, hoàn tác dòng thực đơn ⇒ còn 1 dòng, thanh lưu "Có 1 thay đổi"; Hoàn tác tất cả ⇒ báo hết thay đổi, Lưu khóa. Đổi điểm trường: giữ Cơ sở 1 + bấm lưu ⇒ lỗi, không gửi; chọn Cơ sở 2 ⇒ hết lỗi, báo đang có PHT khác. Không lưu thật lần này.
- Verify: lần đầu FAIL do prettier `vpTasks.js` ⇒ `prettier --write` ⇒ FE `node scripts/verify.mjs` → `VERIFY PASS full` (sau lần sửa cuối).
### 2026-11-09 — Giải thích phạm vi sửa, hộp Phân công và hộp xem lại mới
- Yêu cầu user: (1) đổi bố cục hộp "Phân công <tên>" cho đẹp (ảnh user gửi là hộp đổi điểm trường); (2) brainstorm: việc chỗ "Cả trường" cố định, chỗ chọn Cơ sở/Cả trường, chỗ chỉ Cơ sở — người dùng thắc mắc vì sao.
- Brainstorm (chế độ tự chạy, chọn khuyến nghị A): (A) trong mỗi mảng gom việc theo kiểu phạm vi, mỗi nhóm một dòng tiêu đề có icon + câu "vì sao" — chọn; (B) nhãn phạm vi + lý do lặp ở từng dòng — dài, lặp chữ; (C) chú giải đầu khung + nhãn mỗi dòng — phải dò chú giải. 3 nhóm: "Dùng chung cả trường" (thực đơn, danh mục — cả trường một bộ dữ liệu); "Theo điểm trường — chọn được phạm vi" (3 mã campusScoped, mặc định điểm trường, chọn Cả trường khi người này lo thay mọi điểm trường); "Chỉ <điểm trường>" (hồ sơ trẻ, nhân sự — AUTH-02 không giao PHT toàn trường). Nút Cơ sở/Cả trường chỉ còn ở nhóm chọn được; bỏ chữ "Chỉ xem" ở dòng tắt (công tắc đã nói). Helper `vpScopeGroups`, `VP_SCOPE`.
- Hộp Phân công: khối người (avatar, năm học, đang phụ trách đâu) → chọn điểm trường bằng thẻ radio (nhãn "Đang phụ trách", "Đã có: <tên>", "Chưa có Phó hiệu trưởng", "Đã ngừng hoạt động") → khối "hệ quả" khi đổi (chuyển từ/sang, điểm trường cũ trống PHT, cùng phụ trách với ai, việc theo điểm trường áp dụng nơi mới) → Ghi chú "Không bắt buộc". Giữ validate cũ.
- Hộp xem lại: tóm tắt (tổng + nhãn đếm Được sửa thêm / Về chỉ xem / Đổi nơi sửa, Hoàn tác tất cả) → thẻ từng việc nhóm theo mảng, viền + icon màu theo loại, "trước (gạch) → ô chọn sau", nút Hoàn tác → khối "Lý do thay đổi" tách riêng có gợi ý và đếm ký tự. Dòng preview thêm `kind`, `area` (`VP_CHANGE`).
- Lỗi gặp khi soát: thẻ điểm trường đang phụ trách hiện cả "Đang phụ trách" lẫn "Chưa có PHT" (đếm người khác loại trừ chính người này) ⇒ không gắn "Chưa có" cho điểm trường hiện tại. Ô chọn trong hộp xem lại bị `.select` kéo 100% ⇒ `.select.vp-review__select { width: auto }`.
- Test 49/49 pass (vp-tasks, vp-task-editor, vp-assignment-validation, permission-center). Soát màn thật (HT): 3 nhóm phạm vi đúng mảng; hộp Phân công đổi sang Cơ sở 2 hiện 4 dòng hệ quả; hộp xem lại 1 thẻ "Chỉ xem → Được sửa · Cả trường". Không lưu thật.
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full` (sau lần sửa cuối).
### 2026-11-09 — Trạng thái "đang chọn" rõ cho người khó nhìn
- Yêu cầu user: người dùng lớn tuổi khó thấy đang chọn nếu chỉ có màu xanh; sau đó góp ý badge quá đậm và dòng việc đang bật chưa có chữ nào báo.
- Làm: thẻ PHT đang chọn có nhãn chữ "✓ Đang chọn" + viền 2px + vạch trái; thẻ điểm trường trong hộp Phân công có "✓ Đã chọn" + viền + nền nhạt; nút Cơ sở/Cả trường đang chọn có dấu ✓ + viền + chữ đậm; mỗi dòng việc có chữ trạng thái cạnh công tắc ("✓ Được sửa" xanh đậm / "Chỉ xem" xám) + vạch trái khi bật. Nhãn `.pick-badge` sau góp ý dùng nền trắng, viền và chữ cùng tông (không đặc). Viền thẻ giữ 2px cả lúc chưa chọn để không giật bố cục.
- Test vp-task-editor 5/5 (thêm kiểm "Đang chọn" chỉ 1 người, chữ trạng thái dòng việc). Soát màn thật: đúng như trên. Verify FE → `VERIFY PASS full` (sau lần sửa cuối).
### 2026-11-09 — AUTH-26: mỗi điểm trường tối đa 1 Phó hiệu trưởng (BE + FE)
- User chốt 2026-11-09: mỗi điểm trường tối đa một PHT (trước đó BE cho xếp 2 PHT cùng điểm trường, BUSINESS_RULES không quy định). Ghi `BUSINESS_RULES.md` AUTH-26.
- Giả định (chế độ tự chạy, user soát): dữ liệu cũ đã trùng (nếu có) không tự sửa, chỉ chặn lần lưu mới; muốn đổi người phải chuyển PHT đang giữ đi trước (không có thao tác "đổi chỗ" một bước).
- BE: `ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN` (2327, 409) — **đổi API contract** (thêm mã lỗi). Repository `existsOtherVicePrincipalAtCampus` (+ bản `OpenEnded` đổi ngày kết thúc null thành 9999-12-31). Chặn ở `VicePrincipalAssignmentServiceImpl.assign` (khoảng từ ngày hiệu lực tới trước phân công năm sau) và `StaffAssignmentServiceImpl.createAssignment` (PHT có campus — đường `/staff-assignments`, tạo tài khoản, đổi vai trò). `AUTH_CONTRACT.md` ghi 409 2327.
- Test BE: `VicePrincipalAssignmentIntegrationTest.campusHoldsAtMostOneVicePrincipal` (Postgres thật), `StaffAssignmentServiceImplTest.campusWithAnotherVicePrincipalIsConflict`.
- FE: thẻ điểm trường đã có PHT khác bị khóa + câu "Muốn đổi, chuyển <tên> sang điểm trường khác trước"; `validateVpAssignment` thêm `takenBy`; mock repo chặn như BE; bỏ các câu "hai người cùng phụ trách". FE hiện lỗi BE qua toast (không có bảng mã lỗi riêng) nên không cần map 2327. APP không gọi API phân công PHT.
- Verify: BE lần 1 FAIL do spotless (`StaffAssignmentRepository.java`; tests 1187 pass) ⇒ `spotless:apply` ⇒ BE `VERIFY PASS full | tests 1187 fail 0`. FE `node scripts/verify.mjs` → `VERIFY PASS full`.