# Auth Contract

> Status: **ĐANG TRIỂN KHAI** (ADR-0011). Phần "Đã có" dùng được làm nguồn; phần "Sắp có" sẽ chốt khi endpoint được implement. Hướng dẫn vận hành Keycloak: `docs/system/KEYCLOAK.md`.

## Cơ chế (đã chốt)

- **Keycloak** (realm `carenest`) đăng nhập và phát token theo **OAuth2 Authorization Code + PKCE (S256)**. CareNest **không** có endpoint login/refresh/logout và không nhận mật khẩu khi đăng nhập.
- Client gửi `Authorization: Bearer <access_token>` cho mọi API (trừ `/actuator/health`, Swagger).
- Access token: JWT RS256, thời hạn 5 phút, `aud` chứa `carenest-api`, `sub` = id user Keycloak, role ở `realm_access.roles`.
- Refresh token: client tự giữ và đổi với Keycloak (xoay vòng — mỗi refresh token chỉ dùng 1 lần). CareNest không lưu.
- Logout: client gọi `end_session` của Keycloak (Web) / `logout` + xóa token (Mobile). Access token đã cấp còn hiệu lực tới khi hết hạn (≤ 5 phút).

| Client Keycloak | Dùng cho | Loại | Redirect |
| --- | --- | --- | --- |
| `carenest-web` | CareNest_FE | public + PKCE | `${CARENEST_WEB_ORIGIN}/*` |
| `carenest-mobile` | CareNest_APP | public + PKCE | `${CARENEST_MOBILE_REDIRECT_URI}` (mặc định `carenest://oauth/callback`) |

Endpoint OIDC lấy từ discovery: `{KEYCLOAK_HOSTNAME}/realms/carenest/.well-known/openid-configuration`.

## Lỗi (đã có)

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Thiếu token, sai chữ ký, hết hạn, sai issuer/audience | 401 | `Unauthorized` (kèm header `WWW-Authenticate: Bearer`) |
| Role không đủ (`@PreAuthorize`) | 403 | `Forbidden` |
| Không kiểm được token vì Keycloak tạm không khả dụng | 503 | `Authentication service unavailable` — báo thử lại, **không** về màn hình đăng nhập |

Client xử lý 401: refresh token 1 lần rồi gọi lại; refresh thất bại ⇒ về màn hình đăng nhập.

## Tài khoản hiện tại (đã có)

`GET {API_PREFIX}/accounts/me` — mọi user đã đăng nhập:

```json
{ "code": 200, "desc": "Get my account success",
  "data": { "userId": 12, "username": "teacher.a", "fullName": "...", "email": null, "roles": ["TEACHER"],
            "assignments": [ { "staffRole": "TEACHER", "campusId": null, "classroomId": 3,
                               "validFrom": "2026-09-01", "validTo": null } ],
            "childIds": [] } }
```

- `assignments`: phân công **đang hiệu lực hôm nay** và khớp role trong token (`staffRole` PRINCIPAL: cả hai id null; VICE_PRINCIPAL/KITCHEN_STAFF: `campusId`; TEACHER: `classroomId`).
- `childIds`: trẻ liên kết với tài khoản (chỉ khi có role PARENT).

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Có tài khoản Keycloak nhưng chưa có hồ sơ CareNest | 403 | `Account is not provisioned` |
| Tài khoản CareNest bị khóa (token còn hạn vẫn bị chặn) | 403 | `Account is inactive` |

Hai lỗi trên áp dụng cho **mọi** API dưới `{API_PREFIX}` (không riêng `/accounts/me`).

`roles` là role toàn cục (dùng ẩn/hiện menu). FE/APP không tự quyết quyền: BE kiểm scope ở từng API.

## Phân quyền theo phạm vi (đã có)

API nhận id (trẻ, lớp, campus) kiểm hai lớp: role (`@PreAuthorize`) rồi phạm vi (phân công/liên kết còn hiệu lực). **Ngoài phạm vi và id không tồn tại cùng trả 403 với cùng `desc`** — client không phân biệt được hai trường hợp (chống dò id). 404 chỉ xảy ra với người có phạm vi.

`GET {API_PREFIX}/children/{childId}` — role TEACHER, VICE_PRINCIPAL, PRINCIPAL, PARENT:

```json
{ "code": 200, "desc": "Get child profile success",
  "data": { "id": 2, "fullName": "...", "dateOfBirth": "2022-03-15", "classroomId": 1 } }
```

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Role KITCHEN_STAFF, ADMIN | 403 | `Forbidden` |
| Trẻ ngoài phạm vi hoặc id không tồn tại | 403 | `You do not have access to this child` |
| `childId` không phải số | 400 | `Malformed request` |

`classroomId` = lớp trẻ đang học hôm nay (`null` nếu không có enrollment hiện tại — khi đó chỉ phụ huynh liên kết xem được).

## Cấp / khóa tài khoản (đã có, chỉ ADMIN)

`POST {API_PREFIX}/accounts`

```json
{ "username": "teacher.c", "fullName": "Giáo viên C", "email": "teacher.c@example.test",
  "role": "TEACHER", "temporaryPassword": "..." }
```

- `username`: 3–100 ký tự `[A-Za-z0-9._-]`, lưu chữ thường; `fullName` bắt buộc; `email` tùy chọn; `role` ∈ PRINCIPAL, VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF, PARENT.
- `temporaryPassword` (8–128 ký tự): bắt buộc trừ khi có `email` và server bật gửi email đặt mật khẩu. Người dùng phải đổi ở lần đăng nhập đầu. CareNest không lưu/log mật khẩu.
- 201: `{ userId, username, fullName, email, role, status: "ACTIVE", setupEmailSent }`.

`PATCH {API_PREFIX}/accounts/{userId}/status` — body `{ "status": "INACTIVE" | "ACTIVE" }` ⇒ 200 `{ userId, username, fullName, email, status }`. INACTIVE có hiệu lực ngay (kể cả token còn hạn) và hủy session Keycloak.

| Tình huống | HTTP / `code` | `desc` |
| --- | --- | --- |
| Không phải ADMIN | 403 | `Forbidden` |
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

## Còn chờ

- Phụ huynh đăng nhập bằng gì (username trường cấp / SĐT / email).
- Mobile có dùng `offline_access` (phiên dài) không; lưu token ở secure storage (APP `AUTH_FLOW.md`).
