---
title: Một tài khoản nhiều vai trò, chặn theo nền tảng, đăng nhập bằng SĐT, đổi SĐT
status: draft
owner: thangnd
jira: chưa có — hỏi user (Câu hỏi mở 5)
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, child]
rules: [AUTH-04, AUTH-17, AUTH-18, AUTH-19, AUTH-21, AUTH-22]
risk: high
created: 2026-10-30
updated: 2026-10-30
---
# Một tài khoản nhiều vai trò, chặn theo nền tảng, đăng nhập bằng SĐT, đổi SĐT

## Context
Yêu cầu user 30/10/2026: một người một tài khoản (GV kiêm phụ huynh), Web chỉ quyền nhân sự, App chuyển chế độ GV/phụ huynh, đăng nhập bằng SĐT, đổi SĐT không đổi định danh. Hiện trạng:
- **Định danh:** `user_accounts.id` + `keycloak_user_id` (claim `sub`) bất biến; `username` UNIQUE (`V1__create_user_accounts.sql:3-15`). Mật khẩu, role ở Keycloak (ADR-0011). Trạng thái chỉ `ACTIVE`/`INACTIVE`.
- **Role:** token mang tập role (`CurrentUserService.java:99`), policy dùng `hasRole` ⇒ nhiều role đã chạy ở tầng quyền (`ChildAccessPolicy.java:60`). DB chỉ có bản sao 1 role `primary_role` (`V8__...sql:3`), dùng lọc danh sách, phạm vi quản lý, chặn Web ở đổi mật khẩu lần đầu/quên mật khẩu (`AuthServiceImpl.java:128`, `:282`, `:514-516`).
- **Chặn liên kết:** SĐT trùng tài khoản nhân sự ⇒ 409 `PHONE_BELONGS_TO_STAFF` (`GuardianAccountServiceImpl.java:96-101`) ⇒ GV kiêm phụ huynh hiện phải có 2 tài khoản (và chỉ khi username GV khác SĐT).
- **Đăng nhập:** nhân sự = username trường đặt hoặc email (`loginWithEmailAllowed`), phụ huynh = SĐT 10 số đầu `0` (`PhoneNumbers`). Không tự đăng ký (`registrationAllowed=false`).
- **Nền tảng:** login gateway chặn khi không có role Web (`AuthServiceImpl.java:112`, `:509-511`) ⇒ GV+PH vào Web được. Nhưng API chỉ kiểm `aud` (`SecurityConfig.java:125-128`), không kiểm `azp`; client public `carenest-web` (PKCE) vẫn bật ⇒ phụ huynh lấy token qua client này gọi API được như từ App. App không chặn Ban giám hiệu (AUTH-21).
- **Đổi SĐT:** chưa có. Realm `editUsernameAllowed=false` (`docker/keycloak/import/carenest-realm.json`); Keycloak 26.8 cần kiểm Admin API có đổi username được không khi cờ tắt.
- **OTP:** hạ tầng challenge + rate limit có sẵn (`V4__create_web_auth_state.sql`), chỉ gửi qua email; **chưa có nhà cung cấp SMS** ⇒ OTP qua SĐT chưa làm được.
- **Trẻ ra trường:** liên kết giữ nguyên, policy phụ huynh không xét enrollment ⇒ phụ huynh vẫn xem trẻ đã ra trường (`ChildAccessPolicy.java:60`).
- Không có import Excel ở BE.

## Quyết định đã chốt
1. HT/PHT đổi SĐT cho người dùng, giữ tài khoản/liên kết/lịch sử — user, 30/10/2026. **Hoãn tới khi có nhà cung cấp SMS** (SĐT mới phải xác minh qua OTP SMS, không xác minh bằng gọi điện) — user, 30/10/2026.
2. Một người một tài khoản, nhiều role; Web chỉ quyền nhân sự, phụ huynh-only không vào Web; App chuyển chế độ GV/phụ huynh, BE không tin `activeRole` client gửi — user (spec 30/10/2026).
3. Ưu tiên SĐT + mật khẩu làm thông tin đăng nhập; email bổ sung; SĐT không phải định danh — user (spec 30/10/2026).
4. Không gửi mật khẩu plaintext qua SMS — user (spec 30/10/2026), thay phần "gửi mật khẩu tạm qua SMS" của AUTH-18.
5. Giữ 403 `FORBIDDEN` 1101 cho chặn role Web — user, 30/10/2026.

## Câu hỏi mở
- [x] 1. Bếp dùng chung một tài khoản mỗi cơ sở (AUTH-22): tên đăng nhập là gì khi chuẩn là SĐT? Đề xuất: ngoại lệ, giữ tên đăng nhập trường đặt (vd. `bep.cs1`).
- [x] 2. SĐT nhập khi thêm phụ huynh đã thuộc tài khoản có sẵn (phụ huynh hoặc nhân sự): bắt người nhập xác nhận đúng người (hiện tên che, gửi lại kèm `confirmUserId`) cho cả hai, hay chỉ khi là nhân sự? Đề xuất: cả hai (đổi AUTH-18 "tự thêm liên kết").
- [x] 3. Trẻ ra trường: phụ huynh còn xem dữ liệu lịch sử của trẻ không, bao lâu? Hiện vẫn xem mãi.
- [x] 4. Chưa có SMS: (a) kích hoạt giữ mật khẩu tạm in phiếu + bắt đổi lần đầu; (b) đổi SĐT tự phục vụ bằng OTP (case còn số cũ) hoãn tới khi team chốt nhà cung cấp SMS; (c) SĐT mới xác minh bằng cách nhà trường gọi số mới, ghi lý do. Đồng ý?
- [ ] 5. Mã Jira cho việc này (và merge `dev` đang staged commit trước bằng G94-60?).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| 3. Phụ huynh xem trẻ đã ra trường | Không xem trẻ đã ra trường; hết con đang học ⇒ tài khoản INACTIVE; còn con khác học thì xem được con đó | user | 2026-10-30 | CHD-04, `AUTH_CONTRACT.md` |
| 1. Tên đăng nhập tài khoản bếp dùng chung | SĐT bếp của cơ sở | user | 2026-10-30 | AUTH-15, `AUTH_CONTRACT.md` |
| 4. Chưa có SMS | Chờ có SMS: Phase 4 (đổi SĐT) hoãn; kích hoạt giữ mật khẩu tạm in phiếu như hiện tại | user | 2026-10-30 | plan này |
| 2. Xác nhận đúng người khi SĐT đã có tài khoản | Cả phụ huynh có sẵn lẫn nhân sự | user | 2026-10-30 | AUTH-18, `AUTH_CONTRACT.md` |

## Giả định (ghi để user soát)
- Role PARENT giữ khi gỡ hết liên kết; App hiện chế độ phụ huynh khi có PARENT **và** `childIds` không rỗng. Policy vẫn bắt liên kết nên không lộ dữ liệu.
- `primary_role` = vai trò nhân sự cao nhất, chỉ `PARENT` khi tài khoản chỉ là phụ huynh; tập role đầy đủ đọc từ token. Không thêm bảng role thứ hai (tránh 2 nguồn quyền).
- Thu hồi vai trò nhân sự (GV nghỉ): thao tác mới cho người có `ACCOUNT_MANAGE` theo phạm vi AUTH-17; còn PARENT ⇒ `primary_role=PARENT`, hủy mọi phiên.
- Quyền đổi SĐT: permission mới `ACCOUNT_PHONE_CHANGE`, mặc định HT; PHT mặc định bật, chỉ người trong phạm vi AUTH-17/AUTH-19, HT tắt được. Không ai đổi SĐT của chính mình qua luồng này.
- SĐT giữ định dạng hiện có (10 số đầu `0`), không đổi sang E.164 (tránh migrate username phụ huynh).
- Nhân sự cũ giữ username cũ tới khi được đổi sang SĐT bằng luồng đổi SĐT; không backfill tự động.

## Acceptance criteria
- **AC-1** (AUTH-21): Given token `azp` = client Web và không có role Web When gọi bất kỳ API Then 403 1101.
- **AC-2** (AUTH-21): Given GV+PH đăng nhập Web When gọi API phụ huynh (trẻ của mình qua đường phụ huynh) Then không được dùng quyền PARENT (role PARENT bỏ khỏi `CurrentUser` khi nền tảng Web).
- **AC-3** (AUTH-21): Given token `azp` = `carenest-mobile` và chỉ có role Ban giám hiệu When gọi API Then 403.
- **AC-4** (AUTH-18): Given SĐT đã là tài khoản GV When thêm làm phụ huynh của trẻ (có xác nhận) Then không tạo tài khoản mới, thêm role PARENT, giữ role/phân công GV.
- **AC-5**: Given tài khoản PH được thêm vai trò GV When tạo phân công Then cùng `userId`, đăng nhập SĐT/mật khẩu cũ.
- **AC-6**: Given GV+PH bị thu hồi vai trò nhân sự When đăng nhập Web Then 403; App vẫn chế độ phụ huynh; phiên cũ bị hủy.
- **AC-7**: Given người có `ACCOUNT_PHONE_CHANGE` When đổi SĐT hợp lệ Then `userId`, role, phân công, liên kết, lịch sử giữ nguyên; SĐT cũ không đăng nhập được; mọi phiên bị hủy; có bản ghi lịch sử.
- **AC-8**: Given SĐT mới đã thuộc tài khoản khác When đổi Then 409, không đổi gì ở Keycloak/DB.
- **AC-9**: Given Keycloak đổi username lỗi When đổi SĐT Then DB không đổi, yêu cầu `FAILED`; Given DB lỗi sau khi Keycloak đã đổi Then trả Keycloak về username cũ (compensation), không được thì `RECONCILE_REQUIRED` + log.
- **AC-10**: Given hai yêu cầu đổi SĐT đồng thời cho một tài khoản When xử lý Then chỉ một thành công.
- **AC-11**: Given người không có quyền / ngoài phạm vi / chính mình When đổi SĐT Then 403/400.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nhận diện nền tảng | Claim `azp` của token đã ký (gateway confidential ⇒ WEB, `carenest-mobile` ⇒ MOBILE) | Header `X-Platform` | Header do client tự khai; `azp` do Keycloak ký. Giới hạn: client public ai cũng xin token được ⇒ chỉ chặn đúng phạm vi nền tảng, không chống người cố ý; quyền thật vẫn là permission + scope |
| Client `carenest-web` (PKCE cũ) | Tính là WEB; đề xuất tắt khi FE chuyển hẳn sang gateway | Giữ như hiện tại | Hiện là đường vòng qua chặn Web |
| Nhiều role | Role ở Keycloak + `primary_role` | Bảng `user_account_roles` | Không tạo nguồn quyền thứ hai |
| Đăng nhập SĐT | username Keycloak = SĐT chuẩn hóa, `sub` là định danh | SPI phone-login, attribute alias | Có sẵn với phụ huynh, không thêm extension |
| Đổi SĐT | HT/PHT đổi, bảng lịch sử có trạng thái + compensation | Tạo tài khoản mới | Giữ lịch sử, đúng quyết định 1 |
| Kiến trúc nền tảng + đổi SĐT | ADR-0022 | — | Quyết định bảo mật dài hạn |

## Phases
Đề xuất tách: Phase 4 thành plan riêng khi bắt đầu (migration + realm + Keycloak).
### Phase 1 — Chặn theo nền tảng (AC-1..3)
- Files: C `utils/enums/ClientPlatform.java` · M `security/CurrentUserService.java`, `security/CurrentUser.java`, `application.yml` (map client → nền tảng) · test `CurrentUserServiceTest`, `PlatformAccessIntegrationTest` · docs `SECURITY.md`, `AUTH_CONTRACT.md`, ADR-0022.
- `azp` lạ ⇒ 403; `carenest-dev` chỉ khi bật cờ dev.
- **Dừng chờ review.**
### Phase 2 — Một tài khoản nhiều role (AC-4..6)
- Files: M `GuardianAccountServiceImpl`, `AddGuardianRequest` (`confirmUserId`), `IdentityProviderClient`/`KeycloakIdentityProviderClient` (gỡ realm role), `AccountServiceImpl` (thu hồi vai trò nhân sự), `ApiCode` · test service + integration · docs `AUTH_CONTRACT.md`, `BUSINESS_RULES.md` AUTH-18.
- **Dừng chờ review.**
### Phase 3 — Nhân sự đăng nhập bằng SĐT (theo câu hỏi 1)
- Files: M `CreateAccountRequest` (`phone` thay `username`), `AccountServiceImpl.create`, test, `AUTH_CONTRACT.md`, `openapi.yaml`.
- **Dừng chờ review.**
### Phase 4 — Đổi SĐT (AC-7..11) — hoãn: chờ nhà cung cấp SMS
- Spike: Keycloak 26.8 Admin API đổi username khi `editUsernameAllowed=false`; nếu cần bật thì user profile chỉ cho admin sửa username.
- Files: C migration `V39__create_user_phone_changes.sql` (lịch sử + trạng thái + permission seed), service/controller đổi SĐT · M `IdentityProviderClient` (đổi username), `Permission`, realm json · test.
- **Dừng chờ review.**

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Chặn `azp` sai map | Khóa người dùng thật | Map theo cấu hình, test từng client | Tắt cờ kiểm nền tảng |
| Bật sửa username ở realm | User tự đổi username | User profile: chỉ admin sửa | Tắt cờ |
| Keycloak/DB lệch khi đổi SĐT | Đăng nhập sai số | Trạng thái + compensation + log | Sửa tay theo bản ghi |
| FE/APP chưa theo kịp contract | Lỗi màn thêm phụ huynh, tạo nhân sự | Ghi `AUTH_CONTRACT.md` mục tác động | Giữ field cũ một thời gian |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer: FE màn thêm phụ huynh, tạo tài khoản nhân sự, đăng nhập; APP đăng nhập, chuyển chế độ, gọi API theo `azp`
- [x] Mỗi AC có test; mỗi phase có Exit `node scripts/verify.mjs` → `VERIFY PASS full`
- [x] >3 phase ⇒ đã đề xuất tách Phase 4
- [x] Không dùng rule PENDING/OPEN như đã chốt (P-28 không đụng)
- [x] Tự review

## Progress log
### 2026-10-30 — Draft
- Commit: chưa commit.
- Viết plan từ spec user; chưa sửa code vì còn câu hỏi mở 1–5. (Đính chính: merge `dev` đã được commit + push từ trước là `adb049a`, không phải đang staged.)

### 2026-10-30 — Phase 1 (chờ review)
- Commit: chưa commit. User trả "ok" ⇒ làm Phase 1 theo chế độ tự chạy; Phase 1 không phụ thuộc câu hỏi mở 1–4.
- Sửa: C `utils/enums/ClientPlatform.java`, `security/ClientPlatformProperties.java`, ADR-0022 · M `KeycloakRealmRoleConverter` (lọc role theo `azp`, hết role ⇒ `PLATFORM_DENIED`), `SecurityConfig` (`anyRequest` từ chối `PLATFORM_DENIED`, bean converter + thêm `carenest.auth.client-id` vào WEB), `CurrentUserService` (dùng bean converter), `AuthServiceImpl` (`WEB_ROLES` = `ClientPlatform.WEB`), `application.yml`, `application-dev.yml`, `.env.example`. Test: `KeycloakRealmRoleConverterTest`, `SecurityConfigTest` (AC-1..3, client lạ), `CurrentUserServiceTest#webGatewayTokenDropsParentRole` (AC-2). Docs: `AUTH_CONTRACT.md`, `SECURITY.md`, `KEYCLOAK.md`, `BUSINESS_RULES.md` AUTH-21, `CURRENT_STATE.md`.
- Khác plan: không tạo `PlatformAccessIntegrationTest` — filter chain thật đã phủ ở `SecurityConfigTest`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 580 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 227s` (lần đầu FAIL ArchUnit `sharedEnumsInUtils` ⇒ chuyển `ClientPlatform` sang `utils/enums`). `check-ai-layer`: 0 lỗi, 5 cảnh báo E2 có sẵn.
- Giả định: thiếu `azp` hoặc client lạ ⇒ 403; ngoài profile `dev` token `carenest-dev` ⇒ 403 (FE `origin/dev` dùng password grant `carenest-dev` vẫn chạy vì local/VPS đang chạy profile `dev`).
- Tác động: APP — tài khoản Ban giám hiệu đăng nhập App bị 403 mọi API; GV kiêm phụ huynh lấy chế độ từ `roles` + `childIds`. FE — không đổi (token Web không còn PARENT).
- Tiếp: Phase 2 cần trả lời câu hỏi mở 2.
- Đính chính: trước đó trong chat nói Admin API vẫn đổi username khi `editUsernameAllowed=false` — chưa kiểm, chuyển thành spike Phase 4.

### 2026-10-30 — Phase 2 (chờ review)
- Commit: chưa commit. Câu hỏi mở 2: user chọn "Cả hai".
- Sửa: C `GuardianCandidateRequest`, `GuardianCandidateResponse` · M `AddGuardianRequest` (`confirmUserId`), `GuardianAccountService(Impl)` (`findGuardianCandidate`, `linkExistingAccount`, che họ tên), `GuardianController` (`POST /children/{id}/guardians/lookup`), `AccountLifecycleOperations` (`grantRealmRole`, `revokeRealmRole`), `IdentityProviderClient` + `KeycloakIdentityProviderClient` (`removeRealmRole`), `AccountProvisioningService(Impl)` (`revokeStaffRole`), `AccountController` (`POST /accounts/{id}/staff-role/revoke`), `UserAccount` (`primary_role` sửa được, `becomeParentOnly`), `ApiCode` (thêm 3007, 1127, 1128; bỏ 1125). Test: `GuardianAccountServiceImplTest` (AC-4, xác nhận, phạm vi, tự liên kết, tra SĐT), `AccountProvisioningServiceImplTest` (AC-6, Keycloak lỗi), `GuardianControllerTest`, `AccountControllerTest`. Docs: `AUTH_CONTRACT.md`, `BUSINESS_RULES.md` AUTH-18, `openapi.yaml` (sinh lại: thêm 2 endpoint, 2 schema, `confirmUserId`).
- Giả định: thêm role PARENT cho nhân sự theo phạm vi quản lý tài khoản AUTH-17 (PHT chỉ GV/bếp campus mình); thu hồi vai trò nhân sự chỉ khi còn liên kết trẻ, theo phạm vi AUTH-17; Keycloak gỡ role trước, DB sau (gọi lại được), rồi hủy phiên; phân công chưa bắt đầu kết thúc ở ngày bắt đầu (`endAssignment` không cho ngày trước đó).
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 593 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 279s`. `check-ai-layer`: 0 lỗi, 5 cảnh báo E2 có sẵn.
- AC-5 (phụ huynh thành GV): chưa có luồng — `POST /accounts` tạo tài khoản mới theo username; nối vào tài khoản phụ huynh có sẵn cần username = SĐT ⇒ làm cùng Phase 3.
- Tác động: FE — màn thêm phụ huynh gọi `lookup` trước, gửi `confirmUserId`; bắt 409 3007 thay 1125; màn nhân sự thêm nút "Thu hồi vai trò nhân sự". APP — không đổi.
- Tiếp: Phase 3 cần câu hỏi mở 1.

### 2026-10-30 — Phase 3 (chờ review)
- Commit: chưa commit. Câu hỏi mở 1: user chọn "SĐT bếp cơ sở".
- Sửa: C `utils/PersonNames.java` (che họ tên, dùng chung), `dto/account/PhoneLookupRequest` (thay `GuardianCandidateRequest`, đã xóa), `dto/account/AccountCandidateResponse` · M `CreateAccountRequest` (`phone` thay `username`, thêm `confirmUserId`), `AccountProvisioningService(Impl)` (`findAccountCandidate`, username = SĐT chuẩn hóa, `addStaffRoleToParent`), `AccountController` (`POST /accounts/lookup`), `AccountLifecycleOperations` (`addRealmRole`), `UserAccount` (`becomeStaff`), `AuthServiceImpl` (chuẩn hóa SĐT ở `login`), `GuardianAccountServiceImpl`/`GuardianController` (dùng `PersonNames`, `PhoneLookupRequest`), `ApiCode` (1129). Test: `AccountProvisioningServiceImplTest` (dữ liệu chuyển sang SĐT; AC-5 `parentAccountBecomesTeacher`, xác nhận, tra SĐT, SĐT sai), `AccountControllerTest`, `AuthServiceImplTest#loginNormalizesPhoneIdentifier`. Docs: `AUTH_CONTRACT.md`, `BUSINESS_RULES.md` AUTH-15, `KEYCLOAK.md`.
- Giả định: chỉ nhận SĐT di động 10 số (giữ `PhoneNumbers`; bếp cần SĐT di động, không nhận số cố định); thêm vai trò nhân sự cho phụ huynh theo `checkCanCreate` (như tạo mới), bỏ qua `temporaryPassword`/`email` của request; tài khoản nhân sự cũ giữ tên đăng nhập cũ tới khi đổi SĐT (Phase 4), không backfill.
- Tác động: FE — form tạo nhân sự đổi `username` ⇒ `phone`, gọi `POST /accounts/lookup` trước, gửi `confirmUserId` khi SĐT là phụ huynh, bắt 409 1129; ô đăng nhập ghi "SĐT hoặc email". APP — màn đăng nhập Keycloak nhắc nhập SĐT 10 số.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 598 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 193s` (lần trước Docker tắt ⇒ 174 skip, đã bật lại Docker rồi chạy lại; `openapi.yaml` sinh lại: `POST /accounts/lookup`, `CreateAccountRequest.phone`/`confirmUserId`, `PhoneLookupRequest`, `AccountCandidateResponse`). `check-ai-layer`: 0 lỗi, 5 cảnh báo E2 có sẵn.
- Tiếp: Phase 4 (đổi SĐT) cần câu hỏi mở 3–4; commit cần mã Jira (câu hỏi 5).

### 2026-10-30 — Phase 4 hoãn
- User chọn "Chờ có SMS": không làm đổi SĐT bằng xác minh gọi điện. Phase 4 làm lại khi team chốt nhà cung cấp SMS (cần hỏi trước khi thêm dependency/hạ tầng). Tới lúc đó SĐT sai/đổi xử lý bằng cách cũ: HT/PHT tạo tài khoản mới với SĐT mới (mất liên tục lịch sử), hoặc giữ SĐT cũ làm tên đăng nhập.
- Kích hoạt tài khoản giữ như hiện tại: mật khẩu tạm in phiếu, bắt đổi lần đầu; không gửi mật khẩu qua SMS.
- Còn mở: câu hỏi 3 (phụ huynh xem lịch sử trẻ đã ra trường), câu hỏi 5 (mã Jira để commit Phase 1–3).

### 2026-10-30 — Trẻ ra trường (câu hỏi mở 3, chờ review)
- Commit: chưa commit (`af7b65a` đã chứa Phase 1–3).
- Sửa: `EnrollmentRepository#findChildIdsLeftSchool` + `EnrollmentService(Impl)` ("đã rời trường" = có lịch sử xếp lớp, không còn lớp chưa kết thúc); `ChildAccessPolicy` (đường phụ huynh loại trẻ đã rời trường); `AccountServiceImpl` (`childIds` chỉ trẻ còn học); `YearTransitionService(Impl)` (`ChildrenLeftSchoolEvent` khi kích hoạt năm học, ngày hiệu lực = ngày bắt đầu chuyển lớp); C `ParentAccountLeftSchoolListener` (AFTER_COMMIT, ADR-0004: khóa tài khoản chỉ là phụ huynh hết con đang học; Keycloak lỗi thì DB vẫn INACTIVE). Test: `ChildAccessPolicyTest#parentCannotViewChildWhoLeftSchool`, `AccountServiceImplTest#parentChildIdsExcludeChildrenWhoLeftSchool`, `ParentAccountLeftSchoolListenerTest` (4), `YearTransitionIntegrationTest#activationMovesChildrenAndClosesOldYear` (query thật + event). Docs: CHD-04, `AUTH_CONTRACT.md`.
- Giả định: kích hoạt sớm (tối đa 7 ngày trước ngày bắt đầu) thì khóa ngay lúc kích hoạt dù trẻ còn lớp tới hôm trước ngày bắt đầu; con mới nhập học không tự mở lại tài khoản (không phân biệt được khóa tay với khóa tự động) — HT/PHT mở; trẻ chưa từng xếp lớp coi như đang học; chưa có luồng thôi học giữa năm nên chỉ áp khi chuyển năm học; event mất khi crash sau commit ⇒ tài khoản không bị khóa nhưng vẫn không xem được trẻ.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 604 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 196s`. `check-ai-layer`: 0 lỗi, 5 cảnh báo E2 có sẵn.

### 2026-10-31 — Review trước khi lên `dev` (chờ review)
- Commit: chưa commit. Review độc lập (subagent) diff `adb049a..HEAD`: 2 HIGH, 5 MEDIUM; đã đối chiếu code, sửa các mục dưới.
- HIGH-1 (PHT chiếm tài khoản phụ huynh campus khác: gộp thành GV campus mình rồi cấp lại mật khẩu): gộp phụ huynh thành nhân sự gọi `checkCanManageParent`; cấp lại mật khẩu nhân sự có liên kết trẻ cũng gọi (`checkCanManageLinkedParent`, Admin bỏ qua). Không chặn khóa/mở và phân công (không lộ quyền đăng nhập).
- HIGH-2 (role Keycloak mồ côi khi ghi DB lỗi): `AccountLifecycleOperations.addRealmRole` gỡ role vừa thêm khi `persist` lỗi; dùng cho cả gộp nhân sự và liên kết phụ huynh vào tài khoản nhân sự.
- MEDIUM-3 (merge entity cũ ghi đè `primary_role`): cột trở lại `updatable = false`, đổi role bằng `UserAccountRepository.updatePrimaryRole` (native SQL); test DB `updatePrimaryRoleChangesOnlyRoleAndStaleMergeDoesNotRevertIt`.
- MEDIUM-4 (listener gọi Keycloak trong transaction, một lỗi hỏng cả lô): listener `NOT_SUPPORTED`, mỗi tài khoản `deactivateQuietly` (DB + hủy phiên web trong 1 transaction, Keycloak ngoài transaction, lỗi chỉ log). Còn lại: event mất khi lỗi sau commit không chạy lại (ADR-0004 đã chấp nhận); chưa có integration test đầu-cuối không `@Transactional` cho listener.
- MEDIUM-6: logout Keycloak lỗi sau khi thu hồi vai trò đã ghi ⇒ chỉ log, trả 200.
- MEDIUM-7 (liên kết/gộp vào tài khoản INACTIVE): thêm `status` vào kết quả tra SĐT; tự mở lại hay không — hỏi user.
- L-9: thu hồi vai trò nhân sự cần còn con đang học (không chỉ còn liên kết). L-12: `toString` của `PhoneLookupRequest`/`AddGuardianRequest` không in SĐT/họ tên.
- Không sửa: MEDIUM-5 (`carenest-dev` giữ mọi role ở profile `dev` trên VPS) — VPS đặt `CARENEST_DEV_CLIENT_ENABLED: "false"` (`compose.vps.yaml`) nên client bị tắt ở Keycloak, không xin được token; L-8 tra SĐT không giới hạn phạm vi (cần để báo SĐT đã dùng, họ tên đã che); L-11 token cũ còn role tới hết 5 phút (cùng loại reset mật khẩu); L-13 tài khoản cũ `primary_role` null chỉ Admin liên kết được.

