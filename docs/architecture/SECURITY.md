# Security Design

## Threat model ngắn

Dữ liệu cần bảo vệ: thông tin trẻ em, sức khỏe, quan hệ phụ huynh, tài khoản. Rủi ro chính: lộ dữ liệu ngoài scope (campus/lớp/trẻ khác), lộ dữ liệu qua AI provider/log, chiếm tài khoản.

## Authentication

**Keycloak + Spring Security OAuth2 Resource Server** (ADR-0011). Contract: `docs/contracts/AUTH_CONTRACT.md`; vận hành: `docs/system/KEYCLOAK.md`.

- Keycloak: đăng nhập (Authorization Code + PKCE), băm mật khẩu, password policy, reset mật khẩu, khóa tạm khi sai 5 lần (PROPOSED), session, access token 5 phút + refresh token xoay vòng, realm role toàn cục.
- BE: kiểm JWT tại chỗ bằng JWKS có cache (chữ ký, `iss`, `aud=carenest-api`, `exp`/`nbf`), map `realm_access.roles` → `ROLE_*`, `@PreAuthorize`; 401/403 dạng `ResponseJson`. Stateless, tắt CSRF (chỉ xác thực bằng header Bearer, không cookie), CORS theo danh sách origin.
- BE **không** có login/refresh, không lưu mật khẩu hay refresh token, không tự ký JWT, không log token/header `Authorization`.
- Yêu cầu tối thiểu (ACCEPTED) vẫn giữ: HTTPS khi lên server, không log token/mật khẩu.

## Authorization (ADR-0003)

- Permission theo role (`docs/business/USER_ROLES.md`) + access scope theo assignment/guardian link.
- Kiểm tra tại service; `@PreAuthorize` ở controller chỉ là lớp đầu, **không** thay lọc scope trong query.
- Truy cập theo ID (vd. `/children/{id}`) phải kiểm tra ID thuộc scope — chống IDOR.
- Parent: default-deny với dữ liệu chưa được công bố (ADR-0010).
- System Admin không mặc định có quyền đọc dữ liệu trẻ (AUTH-06, PENDING P-16).

## Dữ liệu trẻ & AI (ADR-0007)

- Data minimization: AI chỉ nhận dữ liệu cần cho task, bí danh hóa, không tên/ngày sinh/ảnh.
- Không log prompt/response chứa dữ liệu trẻ.
- Provider external ⇒ cần xem điều khoản lưu dữ liệu của provider trước khi bật (ghi vào ADR-0007 khi chốt).

## Secret & cấu hình

- Secret qua biến môi trường/secret store; không commit `.env`, key, credential.
- Test/fixture dùng dữ liệu giả.

## Audit

Hành động nhạy cảm ghi AuditEvent (`docs/modules/audit.md`).
