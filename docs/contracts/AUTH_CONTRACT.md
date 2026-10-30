# Auth Contract

> Status: **ĐANG TRIỂN KHAI** (ADR-0011). Phần "Đã có" dùng được làm nguồn; phần "Sắp có" sẽ chốt khi endpoint được implement. Hướng dẫn vận hành Keycloak: `docs/system/KEYCLOAK.md`.

## Cơ chế (đã chốt)

Web React dùng gateway opt-in theo ADR-0013 (`docs/decisions/ADR-0013-react-auth-gateway.md`). Backend Phase 2 đã có source API/session/OTP; xem trạng thái kiểm chứng trong plan. Tích hợp FE thuộc Phase 3: source gateway FE đã có trên FE `dev` (`8896d90`) nhưng mặc định `VITE_AUTH_MODE=mock`; bật `VITE_AUTH_MODE=gateway` để dùng gateway. Đối chiếu `docs/quality/reports/2026-10-22-auth-fe-integration-gap.md` (một số dòng về FE `dev` đã cũ). Mobile và Web legacy tiếp tục PKCE. Hướng dẫn extension: `docker/keycloak/auth/README.md`.

- **Keycloak** (realm `carenest`) xác minh mật khẩu và phát token. Mobile/Web legacy dùng **OAuth2 Authorization Code + PKCE (S256)**; gateway React nhận mật khẩu tại backend qua confidential client riêng, không đưa client secret ra browser.
- Client gửi `Authorization: Bearer <access_token>` cho mọi API (trừ `/actuator/health`, Swagger).
- Access token: JWT RS256, thời hạn 5 phút, `aud` chứa `carenest-api`, `sub` = id user Keycloak, role ở `realm_access.roles`.
- Refresh token PKCE do client giữ. Riêng Web gateway (ADR-0015): refresh token Keycloak mã hóa AES-GCM nằm trong cookie HttpOnly, DB không lưu phiên; Keycloak giữ phiên. Refresh token xoay vòng, JavaScript không đọc được.
- Logout: client gọi `end_session` của Keycloak (Web) / `logout` + xóa token (Mobile). Access token đã cấp còn hiệu lực tới khi hết hạn (≤ 5 phút).

| Client Keycloak | Dùng cho | Loại | Redirect |
| --- | --- | --- | --- |
| `carenest-web` | CareNest_FE | public + PKCE | `${CARENEST_WEB_ORIGIN}/*` |
| `carenest-mobile` | CareNest_APP | public + PKCE | `${CARENEST_MOBILE_REDIRECT_URI}` (mặc định `carenest://oauth/callback`) |

Endpoint OIDC lấy từ discovery: `{KEYCLOAK_HOSTNAME}/realms/carenest/.well-known/openid-configuration`.

## API React auth gateway (opt-in, Phase 2)

`CARENEST_AUTH_ENABLED=false` là mặc định. Khi bật, backend yêu cầu đủ gateway secret, hai khóa base64 độc lập 32 byte, origin chính xác và cấu hình cookie phù hợp HTTPS. React gửi `credentials: include` cho `/auth/**`, không gửi Bearer tại các route này. API nghiệp vụ vẫn dùng Bearer và không xác thực bằng cookie.

1. Gọi `GET {API_PREFIX}/auth/csrf` lấy `data.token` và `data.headerName=X-XSRF-TOKEN`. Browser giữ cookie `XSRF-TOKEN` path `{API_PREFIX}/auth`; React giữ token CSRF từ response body vì cookie path này không đọc được từ route React `/`.
2. Mọi POST `/auth/**` bên dưới phải gửi header `X-XSRF-TOKEN` khớp cookie, kể cả request có thêm Bearer. Thiếu/sai CSRF trả 403 envelope.
3. Sau login/refresh, access token chỉ lưu trong bộ nhớ React; gửi Bearer cho `/accounts/me`, `/me/password` và API nghiệp vụ. Không lưu access/refresh token vào localStorage/sessionStorage.

| Method/path (sau API prefix) | Body | Response data / kết quả |
| --- | --- | --- |
| GET `/auth/csrf` | — | `{token,headerName}` |
| POST `/auth/login` | `{identifier,password,remember}` | 200 `{accessToken,expiresAt,passwordChangeRequired:false,account}` + cookie phiên; identifier là username hoặc email |
| POST `/auth/login` (mật khẩu tạm đã xác minh) | như trên | 428 `{expiresAt,passwordChangeRequired:true,changeToken}`; không phát cookie phiên/access/refresh token |
| POST `/auth/refresh` | tùy chọn `{force:false}` | `{accessToken,expiresAt}` + `Set-Cookie` mới; đọc cookie `CARENEST_SESSION`, gộp request đồng thời; 401 kèm xóa cookie; `force` không còn tác dụng |
| POST `/auth/logout` | — | 200, xóa cookie, best-effort logout Keycloak; cookie thiếu/hỏng vẫn 200 |
| POST `/auth/password/forgot` | `{email}` | `{requestId,expiresAt,resendAvailableAt}`; cùng status/shape cho email không tồn tại, inactive, bị giới hạn hoặc thuộc tài khoản chỉ dùng App (bếp, phụ huynh — AUTH-21: không gửi OTP; mật khẩu mới do HT/PHT cấp lại mật khẩu tạm) |
| GET `/auth/password/otp/{requestId}` | — | metadata cùng shape, không chứa email/OTP |
| POST `/auth/password/otp/{requestId}/resend` | — | metadata; khoảng cách 60 giây |
| POST `/auth/password/otp/{requestId}/verify` | `{otp}` | `{resetToken,expiresAt}`; OTP một lần |
| POST `/auth/password/reset` | `{token,newPassword,confirmPassword}` | token = `changeToken` hoặc `resetToken`; thành công 200, đăng nhập lại |
| PUT `/me/password` (Bearer bắt buộc) | `{currentPassword,newPassword,confirmPassword}` | 200; xác minh mật khẩu hiện tại + đúng JWT subject; thu hồi session |

Mọi response thành công dùng `{code,desc,data}` và `Cache-Control: no-store`. Cookie phiên `CARENEST_SESSION`: HttpOnly, SameSite=Lax, path `{API_PREFIX}/auth`, Secure theo cấu hình; remember bật thì Max-Age tối đa 10 giờ, tắt thì session cookie. Idle 30 phút và trần 10 giờ do Keycloak áp (`ssoSessionIdleTimeout`, `ssoSessionMaxLifespan`); server chặn thêm absolute 10 giờ từ `loginAt` trong cookie. Refresh trả 503 khi provider tạm lỗi thì cookie được giữ, client thử lại được.

OTP: 6 số, hạn 5 phút, tối đa 5 lần sai trên cùng request kể cả resend; 5 lượt cấp/gửi lại trên email hash mỗi bucket 30 phút cố định. Request metadata sống tối đa 30 phút. Policy mật khẩu do Keycloak quyết định; policy từ chối giữ proof để sửa lại. Lỗi mutation không rõ kết quả tiêu hủy proof local; phải bắt đầu lại, không retry mù.

Recovery/current-password dùng assertion HMAC hạn 60 giây, nonce một lần, ràng buộc realm/subject/issuer/audience/purpose để đổi lấy capability provider 10 phút. Không có API đổi mật khẩu chỉ bằng userId. Native MFA/required actions không được bỏ qua; gateway hiện chưa có UI nhận TOTP, tài khoản yêu cầu TOTP sẽ bị từ chối.

Khi gateway bật, cleanup chạy mỗi60 giây: xóa session/quota hết hạn; challenge được dọn sau1 giờ kể từ expiresAt để vẫn giữ metadata/cooldown trong cửa sổ recovery. Tắt feature dừng cleanup; cần bật lại hoặc vận hành retention riêng nếu ngừng gateway lâu dài.

Lỗi gateway: 401 sai credentials/phiên hết hạn; 403 chưa provision/inactive/CSRF; 403 `errorCode` 1101 `FORBIDDEN` khi tài khoản không có role ADMIN/PRINCIPAL/VICE_PRINCIPAL/TEACHER (bếp, phụ huynh — AUTH-21) ở login, đổi mật khẩu lần đầu (theo `primary_role`) và refresh, token vừa cấp bị thu hồi, không set cookie (refresh 401/403 xóa cookie phiên; 503 giữ cookie để retry); 404 challenge không hợp lệ; 409 cooldown/hết lượt; 428 đổi mật khẩu lần đầu; 503 provider/storage không khả dụng. Không trả OTP, provider proof, refresh token hoặc lỗi nội bộ ra client. Access JWT đã cấp có thể còn hiệu lực tối đa 5 phút sau logout/đổi mật khẩu; ACTIVE check tại server tiếp tục áp dụng.

## Lỗi (đã có)

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Thiếu token, sai chữ ký, hết hạn, sai issuer/audience | 401 | `errorCode` 1002 `UNAUTHENTICATED` (kèm header `WWW-Authenticate: Bearer`) |
| Role không đủ (`@PreAuthorize`) | 403 | `errorCode` 1101 `FORBIDDEN` |
| Token không còn role nào trên nền tảng của client: phụ huynh/bếp dùng token Web, Ban giám hiệu dùng token App, client không khai báo (ADR-0022, AUTH-21) | 403 | `errorCode` 1101 `FORBIDDEN` — mọi API |
| Không kiểm được token vì Keycloak tạm không khả dụng | 503 | `errorCode` 1013 `AUTH_SERVICE_UNAVAILABLE` — báo thử lại, **không** về màn hình đăng nhập |

Client xử lý 401: refresh token 1 lần rồi gọi lại; refresh thất bại ⇒ về màn hình đăng nhập.

## Tài khoản hiện tại (đã có)

`GET {API_PREFIX}/accounts/me` — mọi user đã đăng nhập:

```json
{ "code": 200, "desc": "Get my account success",
  "data": { "userId": 12, "username": "teacher.a", "fullName": "...", "email": null, "roles": ["TEACHER"],
            "permissions": ["CHILD_PROFILE_VIEW"],
            "assignments": [ { "staffRole": "TEACHER", "campusId": null, "classroomId": 3,
                               "validFrom": "2026-09-01", "validTo": null } ],
            "childIds": [] } }
```

- `assignments`: phân công **đang hiệu lực hôm nay** và khớp role trong token (`staffRole` PRINCIPAL: cả hai id null; VICE_PRINCIPAL/KITCHEN_STAFF: `campusId`; TEACHER: `classroomId`).
- `childIds`: trẻ liên kết với tài khoản (chỉ khi có role PARENT).
- `permissions`: quyền hiệu lực (ADR-0017) — dùng ẩn/hiện menu, route; đổi ngay khi Hiệu trưởng sửa ma trận/ngoại lệ (gọi lại `/accounts/me` để cập nhật).

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Có tài khoản Keycloak nhưng chưa có hồ sơ CareNest | 403 | `errorCode` 1014 `ACCOUNT_NOT_PROVISIONED` |
| Tài khoản CareNest bị khóa (token còn hạn vẫn bị chặn) | 403 | `errorCode` 1015 `ACCOUNT_INACTIVE` |

Hai lỗi trên áp dụng cho **mọi** API dưới `{API_PREFIX}` (không riêng `/accounts/me`).

`roles` là role toàn cục (dùng ẩn/hiện menu). FE/APP không tự quyết quyền: BE kiểm scope ở từng API.

## Phân quyền theo phạm vi (đã có)

API nhận id (trẻ, lớp, campus) kiểm hai lớp: quyền (`@PreAuthorize("@permissionGuard.has(...)")`, ADR-0017) rồi phạm vi (phân công/liên kết còn hiệu lực). **Ngoài phạm vi và id không tồn tại cùng trả 403 với cùng `desc`** — client không phân biệt được hai trường hợp (chống dò id). 404 chỉ xảy ra với người có phạm vi.

`GET {API_PREFIX}/children/{childId}` — quyền `CHILD_PROFILE_VIEW` (mặc định TEACHER, VICE_PRINCIPAL, PRINCIPAL, PARENT):

```json
{ "code": 200, "desc": "Get child profile success",
  "data": { "id": 2, "fullName": "...", "dateOfBirth": "2022-03-15", "classroomId": 1 } }
```

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Không có `CHILD_PROFILE_VIEW` (KITCHEN_STAFF, ADMIN, người bị thu hồi) | 403 | `errorCode` 1101 `FORBIDDEN` |
| Trẻ ngoài phạm vi hoặc id không tồn tại (PHT có `ALL_CAMPUS_VIEW` xem được trẻ đang học ở mọi campus — AUTH-20) | 403 | `errorCode` 3001 `CHILD_ACCESS_DENIED` |
| `childId` không phải số | 400 | `Malformed request` |

`classroomId` = lớp trẻ đang học hôm nay (`null` nếu không có enrollment hiện tại — khi đó chỉ phụ huynh liên kết xem được).

## Cấp / khóa tài khoản (đã có; quyền `ACCOUNT_MANAGE` / `ACCOUNT_STATUS_MANAGE`, AUTH-06, AUTH-15, AUTH-17)

Ai tạo / quản lý được ai (`StaffAccessPolicy`): Admin → tạo PRINCIPAL, khóa/mở mọi tài khoản; HT (có phân công PRINCIPAL) → PHT/GV/bếp toàn trường; PHT (khi HT bật quyền) → GV/bếp trong campus mình. Không ai thao tác chính mình.

`POST {API_PREFIX}/accounts/lookup` (`ACCOUNT_MANAGE`) — body `{ "phone": "0912 000 001" }` ⇒ `{ exists, userId, maskedFullName, primaryRole }` (`exists=false` ⇒ còn lại `null`). Gọi trước khi tạo để biết SĐT đã có tài khoản chưa.

`POST {API_PREFIX}/accounts`

```json
{ "phone": "0912 000 001", "fullName": "Giáo viên C", "email": "teacher.c@example.test",
  "role": "TEACHER", "temporaryPassword": "...",
  "assignment": { "classroomId": 11, "validFrom": "2026-10-17" }, "confirmUserId": null }
```

- `phone` (thay `username`, chốt 30/10/2026): SĐT di động, chấp nhận `+84`/`84`, dấu cách, `.`, `-`, `()`; chuẩn hóa thành 10 số đầu `0` = tên đăng nhập. Bếp dùng SĐT bếp của cơ sở (AUTH-22). Sai định dạng ⇒ 400 `errorCode` 1124. Tài khoản tạo trước đó giữ tên đăng nhập cũ (đăng nhập bằng tên cũ hoặc email). `fullName` bắt buộc; `email` tùy chọn; `role` ∈ PRINCIPAL, VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF (PARENT ⇒ 400 `This role cannot be created via API` — phụ huynh tạo kèm trẻ, AUTH-18).
- `temporaryPassword` (8–128 ký tự): bắt buộc trừ khi có `email` và server bật gửi email đặt mật khẩu. Người dùng phải đổi ở lần đăng nhập đầu. CareNest không lưu/log mật khẩu.
- `assignment` (AUTH-15): PHT/bếp ⇒ `campusId`, GV ⇒ `classroomId`; `validFrom` bỏ trống = hôm nay. Bắt buộc khi người tạo là PHT; HT bỏ trống được. PRINCIPAL không gửi (tự có phân công toàn trường); PARENT không có phân công.
- 201: `{ userId, username, fullName, email, role, status: "ACTIVE", setupEmailSent, assignment }` (`assignment` = phân công vừa tạo hoặc `null`).
- SĐT đã là tài khoản **phụ huynh** (`primaryRole=PARENT`): phụ huynh được nhận làm nhân sự ⇒ gửi `confirmUserId` = `userId` của `lookup` sau khi xác nhận đúng người; BE thêm role nhân sự + phân công vào chính tài khoản đó, giữ SĐT/mật khẩu (bỏ qua `temporaryPassword`, `email`), `setupEmailSent=false`. Thiếu/sai `confirmUserId` ⇒ 409 `errorCode` 1129; chính mình ⇒ 400 1114. SĐT đã là nhân sự ⇒ 409 `USERNAME_OR_EMAIL_EXISTS`.
- Đăng nhập Web: `identifier` dạng SĐT (`+84 912 …`, dấu cách) được chuẩn hóa trước khi gửi Keycloak. Màn đăng nhập Keycloak của App không chuẩn hóa ⇒ App nhắc nhập 10 số bắt đầu bằng 0.

`PATCH {API_PREFIX}/accounts/{userId}/status` — body `{ "status": "INACTIVE" | "ACTIVE" }` ⇒ 200 `{ userId, username, fullName, email, status }`. INACTIVE có hiệu lực ngay (kể cả token còn hạn) và hủy session Keycloak.

`POST {API_PREFIX}/accounts/{userId}/staff-role/revoke` (`ACCOUNT_MANAGE`, phạm vi như khóa/mở) — nhân sự nghỉ việc nhưng con vẫn học: gỡ role nhân sự ở Keycloak, kết thúc mọi phân công chưa hết, tài khoản còn lại là phụ huynh (`primary_role = PARENT`), hủy mọi phiên ⇒ 200 `{ userId, username, fullName, email, status }`. Không còn liên kết trẻ ⇒ 400 `errorCode` 1128 (dùng khóa tài khoản); tài khoản không phải nhân sự (phụ huynh, Admin) ⇒ 400 1127; chính mình ⇒ 400 1114; Keycloak lỗi ⇒ 503, DB chưa đổi, gọi lại được.

`POST {API_PREFIX}/accounts/{userId}/temporary-password` (`ACCOUNT_MANAGE`) — body `{ "temporaryPassword": "..." }` (8–128) ⇒ 200 `{ userId, username, fullName, email, status }`. Bắt đổi ở lần đăng nhập kế tiếp; hủy mọi phiên web và session Keycloak đang mở.

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Không có quyền tương ứng | 403 | `errorCode` 1101 `FORBIDDEN` |
| Tạo role ngoài phạm vi (Admin tạo GV, HT tạo HT, PHT thiếu `assignment` / campus khác) | 403 | `You cannot create an account with this role` |
| `assignment` sai hình dạng (GV gửi `campusId`, PHT/bếp gửi `classroomId`, PRINCIPAL/PARENT gửi `assignment`) | 400 | vd. `Teacher must be assigned to a classroom` |
| Campus / lớp không tồn tại | 404 | `Campus not found` / `Classroom not found` |
| Khóa/mật khẩu tạm: tài khoản ngoài phạm vi (PHT nhận 403 cả khi id không tồn tại) | 403 | `You cannot manage this account` |
| Mật khẩu tạm: cho chính mình | 400 | `Use change password for your own account` |
| Mật khẩu tạm: đã đặt nhưng không hủy được phiên | 503 | `Password reset but existing sessions could not be revoked, please retry` |
| Body sai | 400 | `Invalid request data` (+ lỗi theo field) |
| `role` = ADMIN | 400 | `This role cannot be created via API` |
| Thiếu mật khẩu tạm khi không gửi được email | 400 | `Temporary password is required when a setup email cannot be sent` |
| Username đã có trong CareNest | 409 | `Username already exists` |
| Username/email đã có bên Keycloak | 409 | `Username or email already exists` |
| Keycloak từ chối dữ liệu (mật khẩu vi phạm policy, vd. trùng username; họ tên có ký tự như `( ) < > & /`) | 400 | `Account data rejected by identity provider (check password policy and full name)` |
| Keycloak không khả dụng / chưa cấu hình | 503 | `Identity provider unavailable` |
| Khóa: `userId` là chính người gọi (AUTH-09) | 400 | `You cannot change the status of your own account` |
| Khóa: `userId` không tồn tại | 404 | `Account not found` |
| Khóa: đã lưu CareNest nhưng Keycloak lỗi | 503 | `Account status saved but identity provider sync failed, please retry` |

## Danh sách nhân sự, phân công, campus/lớp (đã có — AUTH-08, AUTH-16)

| Method + path | Quyền | Body / query | `data` |
| --- | --- | --- | --- |
| `GET /accounts` | một trong `ACCOUNT_MANAGE`, `ACCOUNT_STATUS_MANAGE`, `STAFF_ASSIGNMENT_MANAGE`, `PERMISSION_MANAGE` | `role`, `status`, `campusId`, `q` (username/họ tên), `page` (0…), `size` (1–100, mặc định 20) | `PageResponse` của `{ userId, username, fullName, email, role, status, activeAssignments[] }` |
| `GET /accounts/{userId}` | như trên | — | `{ userId, username, fullName, email, role, status, assignments[] }` (mọi phân công, mới nhất trước) |
| `POST /staff-assignments` | `STAFF_ASSIGNMENT_MANAGE` | `{ userId, campusId?, classroomId?, validFrom?, validTo? }` — role phân công lấy theo role tài khoản | 201 `{ id, userId, staffRole, campusId, classroomId, validFrom, validTo }` |
| `PATCH /staff-assignments/{id}/end` | `STAFF_ASSIGNMENT_MANAGE` | `{ validTo }` (ngày cuối còn hiệu lực) | như trên |
| `GET /campuses/options` | `ACCOUNT_MANAGE` hoặc `STAFF_ASSIGNMENT_MANAGE` | — | `[{ id, code, name }]` — HT/Admin mọi campus, PHT campus mình (tách khỏi `GET /campuses` của màn điểm trường khi merge G94-226) |
| `GET /classrooms?campusId=` | như trên | `campusId` bắt buộc | `[{ id, campusId, name }]` lớp của năm học đang diễn ra |

- `role` trong danh sách là bản sao role Keycloak (`null` = chưa rõ). HT/Admin thấy mọi tài khoản; PHT chỉ GV/bếp đang được phân công trong campus mình.

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| PHT lọc / xem campus khác | 403 | `You do not have access to this campus` |
| Xem chi tiết tài khoản ngoài phạm vi | 403 | `You cannot manage this account` |
| Phân công role/campus ngoài phạm vi, phân công HT | 403 | `You cannot manage this assignment` |
| Tự phân công / kết thúc phân công của mình | 400 | `You cannot change your own assignments` |
| Tài khoản không phải PHT/GV/bếp | 400 | `This account cannot be assigned` |
| Trùng phân công còn mở cùng phạm vi | 409 | `This staff already has an open assignment for this scope` |
| Kết thúc: ngày cuối trước ngày bắt đầu / đã kết thúc sớm hơn | 400 / 409 | `Assignment end date must not be before start date` / `Assignment already ended` |

## Phân quyền cấu hình được (đã có, chỉ PRINCIPAL — `PERMISSION_MANAGE`, ADR-0017)

| Method + path | Body | `data` |
| --- | --- | --- |
| `GET /permissions` | — | `[{ code, module, description, defaultRoles[], configurableRoles[] }]` |
| `GET /role-permissions` | — | `[{ role, editable, configurablePermissions[], permissions[] }]` cho 6 role đăng nhập + cột `TEAM_LEADER` (AUTH-23: không phải role đăng nhập, áp cho GV đang có phân công tổ trưởng; chưa có quyền nào cấu hình được) |
| `GET /role-permissions/{role}` | — | 1 phần tử như trên |
| `PUT /role-permissions/{role}` | `{ "permissions": ["CHILD_PROFILE_VIEW"], "reason": "..." }` (thay toàn bộ ô đang bật) | như GET |
| `GET /accounts/{userId}/permission-overrides` | — | `{ userId, roles[], configurablePermissions[], overrides: [{ permission, effect, reason, updatedBy, updatedAt }], effectivePermissions[] }` |
| `PUT /accounts/{userId}/permission-overrides` | `{ "overrides": [{ "permission": "CHILD_PROFILE_VIEW", "effect": "GRANT" \| "REVOKE" }], "reason": "..." }` (thay toàn bộ; quyền bỏ khỏi danh sách = theo ma trận) | như GET |
| `GET /permission-change-logs?targetRole=&targetUserId=&page=0&size=20` | — | `PageResponse` của `{ id, batchId, actorUserId, targetRole, targetUserId, permissionCode, beforeState, afterState, reason, changedAt }`, mới nhất trước |

- `reason` bắt buộc (≤500 ký tự). `beforeState`/`afterState`: `ON`/`OFF` (ma trận), `GRANT`/`REVOKE`/`INHERIT` (ngoại lệ).
- Role đích của ngoại lệ đọc từ Keycloak; `size` 1–100.

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Không phải PRINCIPAL | 403 | `errorCode` 1101 `FORBIDDEN` |
| Sửa ma trận PRINCIPAL/PARENT/ADMIN (AUTH-11) | 403 | `Permissions of this role are fixed` |
| Quyền không cấu hình được cho role | 400 | `Permission <CODE> cannot be configured for role <ROLE>` |
| Mã quyền/role không tồn tại, thiếu `reason`, `size` > 100 | 400 | `Malformed request` / `Invalid request data` |
| Ngoại lệ cho chính mình (AUTH-12) | 400 | `You cannot change your own permissions` |
| Ngoại lệ cho người có PRINCIPAL/ADMIN hoặc không có role cấu hình (vd. chỉ PARENT) | 403 | `Permissions of this account cannot be overridden` |
| Ngoại lệ: quyền không áp dụng cho role của người đó / trùng quyền | 400 | `Permission <CODE> cannot be configured for this account` / `Duplicate permission <CODE>` |
| `userId` không tồn tại | 404 | `Account not found` |
| Keycloak không khả dụng khi đọc role | 503 | `Identity provider is unavailable` |
| Lọc lịch sử theo cả `targetRole` và `targetUserId` | 400 | `Filter by either targetRole or targetUserId` |

## Tài khoản phụ huynh gắn trẻ (đã có — `PARENT_ACCOUNT_MANAGE`, AUTH-18, AUTH-19)

Phạm vi (`StaffAccessPolicy`): HT mọi trẻ/phụ huynh; PHT trẻ đang học (lớp hôm nay) trong campus mình, và phụ huynh có ít nhất một trẻ liên kết như vậy. PHT nhận cùng 403 cho trẻ/tài khoản không tồn tại và ngoài phạm vi.

| Method + path | Body / query | `data` |
| --- | --- | --- |
| `GET /children` | `campusId`, `classroomId`, `q` (họ tên), `page`, `size` (1–100) | `PageResponse` của `{ id, fullName, dateOfBirth, classroomId, campusId, guardianCount }` (lớp/campus hiện tại, `null` nếu chưa có lớp) |
| `GET /children/{childId}/guardians` | — | `[{ userId, username, fullName, relationship, status, linkedAt }]` (`username` = SĐT) |
| `POST /children/{childId}/guardians/lookup` | `{ "phone": "0912 345 678" }` | `{ exists, userId, maskedFullName, staffAccount, alreadyLinked }` (`exists=false` ⇒ các field khác `null`/`false`) |
| `POST /children/{childId}/guardians` | `{ "phone": "0912 345 678", "fullName": "...", "relationship": "MOTHER" \| "FATHER" \| "GUARDIAN" \| "OTHER", "confirmUserId": 61 }` | 201 `{ userId, username, fullName, relationship, status, newAccount, temporaryPassword }` |
| `DELETE /children/{childId}/guardians/{userId}` | — | 200, `data` rỗng; tài khoản giữ nguyên |
| `PATCH /parent-accounts/{userId}/status` | `{ "status": "INACTIVE" \| "ACTIVE" }` | `{ userId, username, fullName, email, status }` |
| `POST /parent-accounts/{userId}/temporary-password` | — | `{ userId, username, temporaryPassword }` (mật khẩu mới hệ thống sinh, hủy mọi phiên) |

- `phone` chấp nhận `+84`/`84`, khoảng trắng, `.`, `-`, `()`; chuẩn hóa thành 10 số bắt đầu `0` = tên đăng nhập.
- `newAccount = true` ⇒ `temporaryPassword` (10 ký tự) trả **đúng lần này** để in phiếu, CareNest không lưu/log; bắt đổi ở lần đăng nhập đầu. `newAccount = false` ⇒ SĐT đã có tài khoản, chỉ thêm liên kết, giữ mật khẩu, `temporaryPassword = null`.
- Một người một tài khoản (chốt 30/10/2026): SĐT đã có tài khoản (phụ huynh **hoặc** nhân sự) ⇒ FE gọi `lookup` trước, hiện `maskedFullName` (chữ đầu mỗi từ, vd. `N***** T** L**`) để người nhập xác nhận đúng người, rồi gửi `confirmUserId` = `userId`. Thiếu/sai `confirmUserId` ⇒ 409. Tài khoản nhân sự (`staffAccount=true`) được thêm role `PARENT`, giữ role/phân công nhân sự; phải nằm trong phạm vi quản lý tài khoản của người thao tác (AUTH-17), không được là chính mình. GV kiêm phụ huynh: Web chỉ thấy quyền GV, App chuyển chế độ (ADR-0022).

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Không có `PARENT_ACCOUNT_MANAGE` | 403 | `errorCode` 1101 `FORBIDDEN` |
| Trẻ ngoài campus / chưa có lớp (PHT) | 403 | `You cannot manage guardians of this child` |
| Lọc campus/lớp ngoài phạm vi | 403 | `You do not have access to this campus` / `... classroom` |
| Trẻ không tồn tại (HT) | 404 | `Child not found` |
| SĐT sai định dạng | 400 | `Invalid phone number` |
| `relationship` không thuộc danh sách, thiếu field | 400 | `Malformed request` / `Invalid request data` |
| SĐT đã có tài khoản mà thiếu/sai `confirmUserId` | 409 | `errorCode` 3007 `GUARDIAN_ACCOUNT_CONFIRMATION_REQUIRED` (bỏ 1125 `PHONE_BELONGS_TO_STAFF`) |
| SĐT là tài khoản nhân sự ngoài phạm vi quản lý / của chính mình | 403 / 400 | `errorCode` 1112 `ACCOUNT_MANAGE_DENIED` / 1114 `CANNOT_MANAGE_OWN_ACCOUNT` |
| Phụ huynh đã liên kết trẻ này | 409 | `This parent is already linked to the child` |
| Gỡ liên kết không tồn tại | 404 | `Guardian link not found` |
| Khóa/mật khẩu: tài khoản không phải phụ huynh / của chính mình | 400 | `This account is not a parent account` / `You cannot manage your own account here` |
| Khóa/mật khẩu: phụ huynh ngoài phạm vi | 403 | `You cannot manage this account` |

## Tác động FE/APP (BE không sửa repo khác)

Cần thống nhất và làm ở `CareNest_FE` / `CareNest_APP` (cập nhật `docs/integration/AUTH_FLOW.md`, `BACKEND_INTEGRATION.md` của từng repo):

- Web Phase 3: ghép các màn hình login/forgot-password/verify-otp/reset-password/change-password và AuthContext/HTTP client vào gateway phía trên. Public client `carenest-web` vẫn giữ PKCE cho legacy; Mobile `carenest-mobile` tiếp tục public + PKCE S256, cùng issuer/audience.
- Gửi `Authorization: Bearer <access token>` cho mọi `{API_PREFIX}/**`; refresh: Web gateway gọi `POST /auth/refresh` (cookie + `withCredentials`), PKCE tự refresh với Keycloak; 401 ⇒ refresh 1 lần rồi gọi lại, thất bại ⇒ về đăng nhập.
- 403 `ACCOUNT_NOT_PROVISIONED` / `ACCOUNT_INACTIVE` ⇒ báo liên hệ nhà trường, không retry. Thiếu/sai CSRF và chặn role Web cùng trả 403 `FORBIDDEN` 1101 (chốt 30/10/2026, không thêm mã riêng) ⇒ POST `/auth/**` nhận 403 1101 thì gọi lại `GET /auth/csrf` rồi gửi lại **1 lần**; vẫn 403 1101 ở login, đổi mật khẩu lần đầu hoặc refresh ⇒ tài khoản bếp/phụ huynh, báo dùng ứng dụng di động (AUTH-21). 403 khác ⇒ báo không có quyền. 503 `AUTH_SERVICE_UNAVAILABLE` ⇒ báo thử lại sau. Phân biệt bằng `errorCode` (không so `desc`, `desc` là câu tiếng Việt từ 27/10/2026 — `docs/contracts/ERROR_CONTRACT.md`).
- Role theo nền tảng (ADR-0022): token Web (`carenest-auth-gateway`, `carenest-web`) chỉ mang ADMIN/PRINCIPAL/VICE_PRINCIPAL/TEACHER; token App (`carenest-mobile`) chỉ TEACHER/KITCHEN_STAFF/PARENT. GV kiêm phụ huynh: Web `/accounts/me` trả `roles=[TEACHER]`, `childIds=[]`; App trả cả hai ⇒ App hiện chuyển chế độ khi có PARENT và `childIds` không rỗng. Chế độ đang chọn chỉ để dựng giao diện, không gửi lên BE. Token `carenest-dev` chỉ chạy ở profile `dev`.
- Sau đăng nhập gọi `GET /accounts/me` lấy `roles`, `permissions`, `assignments`, `childIds` để dựng menu/màn hình (menu/route theo `permissions`); ẩn nút ở UI không phải phân quyền (AUTH-07).
- Web: màn ma trận quyền/ngoại lệ/lịch sử (`/school/roles`) đổi sang API phân quyền ở trên; bỏ mức F/R/N, mã dạng `user.manage` và role đăng nhập TEAM_LEADER của mock; tổ trưởng là GV có phân công, ma trận có cột `TEAM_LEADER` (AUTH-23).
- Màn hình Admin (Web): `POST /accounts`, `PATCH /accounts/{userId}/status`; ẩn nút khóa trên chính tài khoản đang đăng nhập (BE trả 400, AUTH-09).
- Logout: Web gateway gọi `/auth/logout` + CSRF và xóa access token trong memory; Web legacy gọi `end_session`; Mobile gọi `logout` + xóa token trong secure storage.
- Types: sinh lại từ `docs/api/openapi.yaml`.

## Còn chờ

- ~~Phụ huynh đăng nhập bằng gì~~ — chốt: username = SĐT (AUTH-18), xem mục "Tài khoản phụ huynh gắn trẻ".
- Mobile có dùng `offline_access` (phiên dài) không; lưu token ở secure storage (APP `AUTH_FLOW.md`).
