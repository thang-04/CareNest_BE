---
title: Tài khoản phụ huynh gắn trẻ (phần C của phân quyền)
status: in-progress
owner: thangnd
jira: G94-60 / BE-FEAT-01
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, child]
rules: [AUTH-04, AUTH-06, AUTH-08, AUTH-12, AUTH-13, AUTH-17, AUTH-18, AUTH-19]
risk: high
created: 2026-10-17
updated: 2026-10-17
---
# Tài khoản phụ huynh gắn trẻ (phần C)

Phần A: `2026-10-16-permission-management.md`; phần B: `2026-10-17-staff-account-provisioning.md`.

## Context
- Tài khoản PARENT hiện chỉ Admin tạo qua `POST /accounts` và không gắn trẻ (`AccountProvisioningServiceImpl` `PROVISIONABLE_ROLES`, `StaffAccessPolicy.ADMIN_CREATABLE`); liên kết `guardian_child_links` (V3) chỉ có ở seed dev; `GuardianChildLinkService` chỉ đọc.
- Không có API danh sách trẻ (`ChildController` chỉ `GET /children/{id}`); phạm vi trẻ = lớp đang học hôm nay (`EnrollmentService.findActiveClassroomIds`, `ChildAccessPolicy`).
- Cách phụ huynh đăng nhập OPEN ở ADR-0011.

## Quyết định đã chốt
1. Username phụ huynh = SĐT chuẩn hóa; mật khẩu tạm bắt đổi lần đầu; tạo cùng liên kết trẻ; không SMS — user 2026-10-16.
2. Quyền mới `PARENT_ACCOUNT_MANAGE`: HT cố định (toàn trường); PHT cấu hình được, **mặc định bật**, chỉ trẻ đang học trong campus mình — user 2026-10-17.
3. Mật khẩu tạm **hệ thống tự sinh**, trả đúng 1 lần (in phiếu), không lưu/log — user 2026-10-17.
4. SĐT đã có tài khoản phụ huynh ⇒ chỉ thêm liên kết, giữ mật khẩu — user 2026-10-17.
5. Làm thêm: danh sách trẻ để chọn; xem/gỡ phụ huynh của trẻ; khóa/mở + cấp lại mật khẩu tạm cho phụ huynh; phiếu in ở FE — user 2026-10-17.

**Mặc định agent chọn (đổi được khi duyệt):**
- a. SĐT VN 10 số bắt đầu `0`; chuẩn hóa `+84`/`84` → `0`, bỏ khoảng trắng/dấu chấm/gạch.
- b. Quan hệ: `MOTHER`, `FATHER`, `GUARDIAN`, `OTHER`.
- c. SĐT trùng username của nhân sự ⇒ 409; phụ huynh đã liên kết trẻ đó ⇒ 409.
- d. Gỡ liên kết cuối cùng không xóa/khóa tài khoản (phụ huynh vẫn đăng nhập nhưng không thấy trẻ nào — AUTH-04).
- e. Khóa/mở, cấp lại mật khẩu phụ huynh qua endpoint riêng `/parent-accounts/...` (quyền `PARENT_ACCOUNT_MANAGE`), không mở `ACCOUNT_*` của nhân sự; mật khẩu mới cũng do hệ thống sinh.
- f. Bỏ PARENT khỏi `POST /accounts` (Admin không còn tạo phụ huynh rời) — thay bằng luồng này như ADR-0017 "Còn chờ".
- g. Danh sách trẻ chỉ mở theo `PARENT_ACCOUNT_MANAGE` (màn chọn trẻ); danh sách trẻ cho GV/màn khác để sau.

## Làm rõ nghiệp vụ
- **Actor:** HT, PHT (theo quyền). **Outcome:** phụ huynh có tài khoản đăng nhập bằng SĐT, thấy đúng con mình (AUTH-04).
- **Đối chiếu:** ADR-0011 OPEN "phụ huynh đăng nhập bằng gì" ⇒ CONFIRMED SĐT. FE mock `ParentActivationPage` (gửi lại SMS) ⇒ thay bằng phiếu in.
- **Ảnh hưởng:** identity-access, child; FE: màn Tài khoản phụ huynh; APP: đăng nhập bằng SĐT (chỉ là username — không đổi luồng PKCE).

### Câu hỏi mở
| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Ai cấp TK phụ huynh | HT + PHT theo quyền (mặc định bật) | user | 2026-10-17 | AUTH-18 |
| Mật khẩu tạm | Hệ thống sinh, hiện 1 lần | user | 2026-10-17 | AUTH-18 |
| SĐT trùng | Chỉ thêm liên kết | user | 2026-10-17 | AUTH-18 |
| Phạm vi | + danh sách trẻ, gỡ liên kết, khóa/mở, mật khẩu, phiếu in | user | 2026-10-17 | AUTH-19 |

## Acceptance criteria
- **AC-1** (`AUTH-18`): HT/PHT (trẻ campus mình) gửi `{phone, fullName, relationship}` cho trẻ ⇒ 201, tài khoản PARENT username = SĐT chuẩn hóa, liên kết trẻ, trả `temporaryPassword` 1 lần; đăng nhập bằng SĐT thấy đúng trẻ.
- **AC-2** (`AUTH-18`): SĐT đã là phụ huynh ⇒ chỉ thêm liên kết, `newAccount=false`, không trả mật khẩu; đã liên kết trẻ đó ⇒ 409; SĐT là nhân sự ⇒ 409.
- **AC-3** (`AUTH-08`): PHT với trẻ campus khác / trẻ không có lớp hiện tại ⇒ 403; `GET /children` của PHT chỉ trẻ campus mình.
- **AC-4** (`AUTH-19`): gỡ liên kết ⇒ phụ huynh mất quyền xem trẻ ngay; tài khoản còn nguyên.
- **AC-5** (`AUTH-19`, `AUTH-12`): khóa/mở, cấp lại mật khẩu phụ huynh khi người làm quản lý campus của một trẻ được liên kết; mật khẩu mới trả 1 lần, hủy phiên cũ.
- **AC-6**: Keycloak lỗi sau khi tạo user ⇒ xóa user Keycloak, không còn dòng tài khoản/liên kết.
- **AC-7**: mật khẩu tạm không xuất hiện trong log (`toString` che, không log body).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Endpoint tạo | `POST /children/{childId}/guardians` | `POST /accounts` role PARENT | Gắn trẻ là bắt buộc, phạm vi theo trẻ |
| Phạm vi phụ huynh | Theo campus lớp hiện tại của trẻ liên kết (`StaffAccessPolicy`) | Theo campus lưu ở tài khoản | Phụ huynh không có phân công; trẻ chuyển lớp/campus thì phạm vi tự đúng |
| Mật khẩu | `SecureRandom`, 10 ký tự bỏ ký tự dễ nhầm, có chữ hoa/thường/số | Nhân viên nhập | Tránh mật khẩu yếu/trùng SĐT (policy Keycloak cấm trùng username) |

## Phases
### Phase 1 — BE
- Files: C `V9__parent_account_permission.sql` (seed VP `PARENT_ACCOUNT_MANAGE`) · M `Permission` · M `StaffAccessPolicy` (+ phạm vi trẻ/phụ huynh) · M `GuardianChildLinkService` (+ tạo/gỡ/liệt kê theo trẻ), `EnrollmentService`/`ChildService` (+ danh sách trẻ theo lớp) · C `GuardianAccountService` (feature account: tạo/ghép tài khoản, khóa/mở, mật khẩu, liệt kê phụ huynh của trẻ) · C `TemporaryPasswordGenerator` · C `GuardianController` (`/children/{id}/guardians`, `/parent-accounts/...`) · M `ChildController` (`GET /children`) · M `AccountProvisioningServiceImpl`/`StaffAccessPolicy` (bỏ PARENT ở `POST /accounts`) · DTO.
- Tests: AC-1..7 (service Mockito, WebMvc, Testcontainers liên kết + danh sách trẻ theo campus); cập nhật test cũ dùng PARENT ở `POST /accounts`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0. **Dừng chờ review.**
### Phase 2 — Docs
- `BUSINESS_RULES` AUTH-18/19; ADR-0011 (phụ huynh đăng nhập bằng SĐT → CONFIRMED), ADR-0017 (quyền mới); `identity-access`, `child` card; `AUTH_CONTRACT`; `USER_ROLES`; `CURRENT_STATE`; snapshot OpenAPI; `CROSS_REPO_MAP` (APP: đăng nhập bằng SĐT). Exit: check-ai-layer 0 lỗi + verify PASS.
### Phase 3 — FE (nhánh FE hiện tại, không commit)
- Làm lại `/children/activation` → "Tài khoản phụ huynh": chọn trẻ (`GET /children`), xem/thêm/gỡ phụ huynh, khóa/mở, cấp lại mật khẩu, phiếu in (bỏ SMS); mock chép luật; test; docs ROUTE_MAP. Exit: FE `verify.mjs` PASS.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2, AC-6, AC-7 | AUTH-18 | `GuardianAccountServiceImplTest`, `GuardianControllerTest` | 1 | |
| AC-3 | AUTH-08 | `StaffAccessPolicyTest`, `ChildServiceImplTest`, Testcontainers | 1 | |
| AC-4, AC-5 | AUTH-19, AUTH-12 | `GuardianAccountServiceImplTest` | 1 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Lộ mật khẩu tạm | Chiếm tài khoản phụ huynh | Trả 1 lần, không lưu/log, bắt đổi lần đầu; cấp lại hủy phiên | — |
| SĐT nhập sai | Phụ huynh không đăng nhập được | Chuẩn hóa + kiểm định dạng; sửa = gỡ liên kết, tạo lại | Gỡ liên kết |
| Bỏ PARENT ở `POST /accounts` | Đổi contract (Admin) | Báo FE/APP; snapshot OpenAPI | Thêm lại vào danh sách |

## Validation log
- [x] Khẳng định code có `file:line`/tên class (Context)
- [x] Consumer: FE `ParentActivationPage`; APP màn đăng nhập (username = SĐT)
- [x] Mỗi AC có test; phase có Exit
- [x] Tách khỏi phần A/B
- [x] Không dùng rule PENDING như đã chốt (P-16 giữ nguyên)
- [x] User duyệt plan + mặc định a–g (2026-10-17)

## Progress log
### 2026-10-17 — Plan duyệt
- User duyệt plan và mặc định a–g ("Ok"). Bắt đầu phase 1.
### 2026-10-17 — Phase 1 BE (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 306 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`
- Code: V9 (seed VP `PARENT_ACCOUNT_MANAGE`, CHECK quan hệ); `Permission.PARENT_ACCOUNT_MANAGE`; `GuardianRelationship`; `PhoneNumbers`; `TemporaryPasswordGenerator`; `AccountLifecycleOperations` (tách tạo user Keycloak + bù trừ, khóa/mở, mật khẩu tạm dùng chung cho nhân sự và phụ huynh — provisioning dùng lại); `StaffAccessPolicy` (+ phạm vi trẻ/phụ huynh); `GuardianChildLinkService` (+ liệt kê/đếm/tạo/gỡ), `EnrollmentService`/`ClassroomService` (+ truy vấn lô), `ChildService.searchChildren`; `GuardianAccountService`; `GuardianController`, `GET /children`; bỏ PARENT khỏi `POST /accounts`.
- Snapshot OpenAPI cập nhật có chủ đích (thêm `/children`, `/children/{id}/guardians…`, `/parent-accounts/…`); không path nào bị xóa.
- Khác plan: tách `AccountLifecycleOperations` để không chép logic Keycloak/bù trừ (DRY); gặp ENV-261017-maven-incremental-test-compile-stale.
- Tiếp: Phase 2 docs sau khi user review.
### 2026-10-17 — Phase 2 docs (chờ review)
- User duyệt phase 1 ("ok"). Commit: chưa commit.
- Docs: `BUSINESS_RULES` AUTH-18/19 (CONFIRMED); ADR-0011 "Còn chờ" phụ huynh đăng nhập ⇒ chốt SĐT; ADR-0017 mục bổ sung phụ huynh, "Còn chờ" hết; `USER_ROLES` (+`PARENT_ACCOUNT_MANAGE`); `AUTH_CONTRACT` (bỏ PARENT ở `POST /accounts`, mục "Tài khoản phụ huynh gắn trẻ" + bảng lỗi); `child`, `identity-access` card; `DATA_DICTIONARY` (CHECK quan hệ V9); `CURRENT_STATE`; `CROSS_REPO_MAP` (APP đăng nhập bằng SĐT).
- Tiếp: Phase 3 FE sau khi user review.
### 2026-10-17 — Phase 3 FE (chờ review)
- User duyệt phase 2 ("ok"). Nhánh FE hiện tại, không commit.
- Code FE: `models/account/guardianConstants.js`, `utils/account/guardianValidation.js` (chuẩn hóa SĐT như BE), `services/account/{guardianService.js, api/guardianApi.js, mock/guardianMockRepository.js}` (mock chép luật BE; `staffScopeOf` dùng chung phạm vi với mock nhân sự), `hooks/account/useGuardians.js`; viết lại `/children/activation` "Tài khoản phụ huynh": chọn trẻ, xem/thêm/gỡ phụ huynh, khóa/mở, cấp lại mật khẩu, phiếu in (`print-sheet`, chỉ nút In — không xuất PDF để mật khẩu không thành file); menu/route theo `PARENT_ACCOUNT_MANAGE` (HT cũng thấy); `PERMISSIONS`, danh mục mock thêm quyền.
- Verify FE: `node scripts/verify.mjs` → `VERIFY PASS full`; `tests/auth/guardians.test.mjs` 4/4 pass; check-ai-layer FE 0 lỗi.
- Giới hạn: mock cũ về tài khoản phụ huynh trong module trẻ (`activateParentAccount`, `resendParentSms`, badge ở hồ sơ trẻ, widget dashboard PHT) chưa gỡ — dùng ở `ChildProfilePage`, `ChildQuickPanel`, `VicePrincipalDashboard`; chưa chạy UI trên trình duyệt, chưa nối BE thật.
- User chốt gỡ mock cũ ("gỡ luôn"): FE bỏ `activateParentAccount`/`resendParentSms`/`listParentAccounts`, collection `parentAccounts`, `accountStatus`/SMS trên `child.guardians`, `ParentAccountBadge`, `canViewParentAccounts`/`canActivateParent`, hằng số SMS; hồ sơ trẻ chỉ giữ thông tin liên lạc phụ huynh (SĐT sửa được), nút dẫn sang màn Tài khoản phụ huynh theo `PARENT_ACCOUNT_MANAGE`; dashboard PHT bỏ thẻ "chưa kích hoạt" (BE không có số đếm). Verify FE PASS full; guardians test 4/4.

### 2026-10-18 — Thiết kế lại UI Nhân sự & quyền (FE, nhánh `feature/G94-209-FE-FEAT-53-permission-management`, chưa commit)
- User thấy UI cũ khó dùng (không rõ bắt đầu từ đâu, khái niệm khó hiểu); chọn hướng "lấy người làm trung tâm", phương án a (chia đôi), giữ lý do bắt buộc (AUTH-14). Wireframe ở scratchpad (`wireframe-h1.html`).
- FE: `/school/staff[/:userId]` = Nhân sự & quyền (danh sách + tờ người: Làm ở đâu? · Được làm những gì? · Tài khoản); `/school/roles` = Mặc định của vai trò; route cũ `/school/roles/users[/:id]`, `/school/roles/:role` chuyển hướng; menu gộp một mục. Xóa trang cũ (ma trận, form vai trò, ngoại lệ, danh sách/chi tiết nhân sự). Logic công tắc ↔ GRANT/REVOKE ở `utils/account/personPermissions.js` + test. Không đổi API.
- Verify FE: `VERIFY PASS full`; chụp màn chế độ mock 1280/375: không tràn ngang, không lỗi console.
- Còn hở: nhãn "Riêng" trong danh sách người cần API mới (danh sách không trả ngoại lệ) — chưa làm; chưa thử với BE thật.
- Quyết định mới cho màn phụ huynh (user, 2026-10-18): tự tạo tài khoản phụ huynh khi tiếp nhận trẻ + tạo hàng loạt theo lớp; mật khẩu cấp khi in phiếu; bắt buộc SĐT, ca lỗi vào "Cần xử lý"; đếm theo lớp (có tài khoản / đã in / đã đăng nhập). Cần plan làn L + mã Jira riêng; BE chưa có chức năng tiếp nhận trẻ. Wireframe P1 ở scratchpad (`wireframe-ph.html`). Chưa ghi vào BUSINESS_RULES (chờ plan duyệt).

### 2026-10-18 — Dựng màn phụ huynh P1 trên API hiện có (FE, chưa commit)
- User: "dựng màn phụ huynh P1 tiếp đi". `/children/activation` dựng lại theo P1: chọn điểm trường, cột lớp (tiến độ "có tài khoản" mỗi lớp, chấm cam khi còn thiếu, ô lọc khi ≥8 lớp), danh sách bé (chưa có tài khoản lên đầu, chip lọc), dải "Cần xử lý" gom bé chưa có tài khoản, tìm bé theo tên trong điểm trường; panel trượt của bé (`components/account/GuardianPanel.jsx`) giữ nguyên hành vi cũ: thêm/gỡ, khóa/mở, cấp mật khẩu mới kèm phiếu in.
- Số đếm theo lớp tính ở FE: tải đủ trẻ của điểm trường qua `GET /children` (trang 100) rồi gom theo lớp (`utils/account/guardianCoverage.js` + test). Không đổi API.
- Chưa làm (chờ plan làn L + API BE): số "đã in phiếu", "đã đăng nhập lần đầu", in phiếu cả lớp, ca tạo tự động lỗi. Không hiển thị số giả.
- Verify FE `VERIFY PASS full`; chụp màn chế độ mock 1280/375: không tràn ngang, không lỗi console.
