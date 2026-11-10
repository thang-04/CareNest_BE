# ADR-0022 — Chặn theo nền tảng bằng client của token (`azp`)

- Status: ACCEPTED
- Date: 2026-10-30
- Liên quan: AUTH-21, ADR-0011, ADR-0013, plan `docs/plans/active/2026-10-30-multi-role-phone-identity.md`

## Bối cảnh
Một người có thể vừa là GV vừa là phụ huynh (một tài khoản, nhiều role). Web chỉ dùng quyền nhân sự, App chỉ cho phụ huynh/GV/bếp (AUTH-21). Trước đây chỉ gateway Web chặn lúc đăng nhập; API chỉ kiểm `aud`, nên token của client public `carenest-web` (PKCE) hay `carenest-mobile` mang đủ mọi role trên mọi nền tảng, Ban giám hiệu vẫn gọi được API từ App.

## Quyết định
- Nền tảng lấy từ claim `azp` của access token (Keycloak ký): `carenest.security.platform.web-clients` (mặc định `carenest-auth-gateway`, `carenest-web`, cộng `carenest.auth.client-id`) ⇒ WEB; `mobile-clients` (`carenest-mobile`) ⇒ MOBILE; `dev-clients` (rỗng; profile `dev` = `carenest-dev`) ⇒ giữ mọi role.
- `KeycloakRealmRoleConverter` chỉ giữ role của nền tảng: WEB = ADMIN, PRINCIPAL, VICE_PRINCIPAL, TEACHER; MOBILE = TEACHER, KITCHEN_STAFF, PARENT (`ClientPlatform`).
- Không còn role nào (phụ huynh-only trên Web, BGH trên App, client lạ, thiếu `azp`) ⇒ authority `PLATFORM_DENIED` ⇒ `SecurityConfig` trả 403 `FORBIDDEN` 1101 cho mọi API.
- Chế độ GV/phụ huynh trên App là giao diện; BE không nhận `activeRole` từ client, quyền vẫn theo role + permission + scope.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Claim `azp` (chọn) | Do Keycloak ký, không cần thêm hạ tầng | Client public ai cũng xin token được ⇒ chỉ giữ đúng phạm vi nền tảng, không chống người cố ý |
| Header `X-Platform` | Đơn giản | Client tự khai, giả được |
| Audience riêng mỗi nền tảng | Tách rõ | Phải sửa realm + mọi client, nhiều cấu hình hơn |

## Hệ quả
- Thêm client Keycloak mới phải khai vào `CARENEST_WEB_CLIENT_IDS` / `CARENEST_MOBILE_CLIENT_IDS`, nếu không mọi request bị 403.
- Ngoài profile `dev`, token `carenest-dev` bị 403.
- Bảo vệ dữ liệu thật vẫn là permission + scope (ADR-0003, ADR-0017); lớp này chỉ ngăn dùng role ngoài nền tảng.

## Còn chờ
- Tắt client `carenest-web` (PKCE cũ) khi FE chuyển hẳn sang gateway.
