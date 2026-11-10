# Auth BE ↔ FE — đối chiếu tích hợp

Ngày 2026-10-22, BE branch `feature/G94-60-BE-FEAT-01-authentication` (chưa merge), FE `dev` @ `000c7bb`. Phạm vi: đọc source và git hai repo; không chạy e2e, không sửa FE/APP.

## Kết luận

Luồng auth BE **chưa ghép vào FE `dev`**. FE `dev` chạy mock (`VITE_USE_MOCK=true` mặc định trong `.env.example`), HTTP client gắn Bearer từ local/sessionStorage, không có CSRF, refresh hay `withCredentials`. Bản tích hợp gateway (ADR-0013/0015) đã viết ở FE commit `24a3a8a` nhưng chỉ nằm trên nhánh `feature/G94-209-FE-FEAT-53-permission-management`; plan FE `docs/plans/active/2026-10-18-selective-nutrition-permission-merge.md:32` chốt "không nhận gateway auth" khi merge.

## Đối chiếu từng luồng

Trạng thái: **Khớp** · **Lệch** (path/DTO/cơ chế khác BE) · **Mock** (chỉ chạy dữ liệu giả) · **Stub** (non-mock trả lỗi cố định).

| Luồng | Endpoint BE | FE `dev` | FE `24a3a8a` (nhánh G94-209) | Trạng thái `dev` |
| --- | --- | --- | --- | --- |
| CSRF bootstrap | `GET /auth/csrf` | Không gọi | `authTransport.js` lấy token, retry 1 lần khi 403 | Lệch |
| Login (+428 first-login) | `POST /auth/login {identifier, password, remember}` → `{accessToken, expiresAt, account}` + cookie | `authService.js:15` gửi `{email, password, remember}`, chờ `{accessToken, expiresAt, user}`; không CSRF/credentials | Khớp contract | Lệch |
| Refresh | `POST /auth/refresh` (cookie HttpOnly) | Không có; 401 ⇒ logout (`axiosClient.js:28-30`) | Refresh 1 lần rồi retry, gộp request đồng thời | Lệch |
| Logout | `POST /auth/logout` + CSRF | `authService.js:17` không CSRF/credentials | Khớp | Lệch |
| Phiên đầu trang | refresh rồi `GET /accounts/me` | `GET /auth/me` (`authService.js:16`) — BE không có | `POST /auth/refresh` → `GET /accounts/me` | Lệch |
| Forgot/OTP/reset | `/auth/password/forgot`, `otp/{id}`, `resend`, `verify`, `reset` | Path khớp (`accountApi.js:8-12`) nhưng đi qua `axiosClient` Bearer, thiếu CSRF ⇒ POST sẽ 403 | Khớp | Lệch |
| Đổi mật khẩu | `PUT /me/password` (Bearer) | `accountApi.js:13` | Khớp, `skipAuthRefresh` | Path khớp, chưa test BE thật |
| Hồ sơ cá nhân | Không có | `GET/PATCH /me/profile` (`accountApi.js:14-15`) | Hồ sơ read-only từ `/accounts/me` | Lệch |
| Tài khoản nhân sự | `GET/POST /accounts`, `PATCH /accounts/{id}/status`, `POST /accounts/{id}/temporary-password`, `/staff-assignments` | `/users` (`schoolConfigApi.js:42-44`) | — | Lệch + Mock |
| Tài khoản phụ huynh | `/children/{id}/guardians`, `/parent-accounts/{id}/...` | Màn `/children/activation` chạy mock | — | Mock |
| Phân quyền | `/permissions`, `/role-permissions[/{role}]`, `/accounts/{id}/permission-overrides`, `/permission-change-logs` | `permissionService.js:7` non-mock trả 501; `schoolConfigApi.js:58-59` có path `/role-permissions` | — | Stub |

## Lệch khác

- Role `TEAM_LEADER` chỉ có ở FE (`src/models/User.js:18`); BE chỉ nhận ADMIN, PRINCIPAL, VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF, PARENT (`KeycloakRealmRoleConverter`).
- FE dựng quyền từ `role` đơn + `rolePermissions` mock; BE trả `roles[]`, `permissions[]`, `assignments[]`, `childIds[]` qua `/accounts/me` (AUTH_CONTRACT mục `/accounts/me`).
- Form login FE chỉ nhận email; BE nhận `identifier` = username, email hoặc SĐT (phụ huynh, AUTH-18).
- CORS BE: API nghiệp vụ mặc định `http://localhost:3000` (`CARENEST_CORS_ALLOWED_ORIGINS`), gateway `/auth/**` mặc định `http://localhost:5173` (`CARENEST_AUTH_WEB_ORIGIN`). Khi ghép phải đặt cùng origin FE thật.
- Gateway BE mặc định tắt (`CARENEST_AUTH_ENABLED=false`); FE ghép gateway cần bật và cấu hình client `carenest-auth-gateway`.

## Quyết định đang chặn

- FE đã chốt "Giữ đăng nhập, phiên/token, HTTP client chung và cấu hình auth hiện tại; không nhận gateway auth của P." (`CareNest_FE/docs/plans/active/2026-10-18-selective-nutrition-permission-merge.md:32`). Muốn ghép thật phải đảo quyết định này — việc của team/user.

## Câu hỏi mở

- FE deploy dùng gateway (ADR-0013) hay PKCE `carenest-web`?
- SameSite/domain cookie phiên ở môi trường thật; mặc định `remember` trên FE.
- Mobile có dùng `offline_access` không (AUTH_CONTRACT "Còn chờ").
