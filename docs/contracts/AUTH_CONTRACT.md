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

Client xử lý 401: refresh token 1 lần rồi gọi lại; refresh thất bại ⇒ về màn hình đăng nhập.

## Sắp có (chốt khi implement)

- [ ] `GET /accounts/me` — user hiện tại: profile, roles, tóm tắt scope (assignment đang hiệu lực, id trẻ liên kết).
- [ ] 403 `Account is not provisioned` (có tài khoản Keycloak nhưng chưa có hồ sơ CareNest) / `Account is inactive`.
- [ ] 403 ngoài scope campus/lớp/trẻ; id không tồn tại cũng trả 403 cùng `desc` (không lộ id để dò).
- [ ] Cấp/khóa tài khoản (Admin): `POST /accounts`, `PATCH /accounts/{userId}/status`.

## Còn chờ

- Phụ huynh đăng nhập bằng gì (username trường cấp / SĐT / email).
- Mobile có dùng `offline_access` (phiên dài) không; lưu token ở secure storage (APP `AUTH_FLOW.md`).
