# ADR-0011 — Xác thực bằng Keycloak; CareNest là OAuth2 Resource Server

- Status: ACCEPTED
- Date: 2026-10-04
- Liên quan: AUTH-01..08, ADR-0003 (mô hình scope, giữ nguyên), module identity-access; thay định hướng "Spring Security + JWT tự phát hành, không dùng Keycloak trong v1" trong guide §2 (bản trước)
- Nguồn: nhóm chốt, user xác nhận ngày 2026-10-04

## Bối cảnh
Guide §15 bước 2 (đăng nhập và phạm vi) bị chặn vì auth chưa chốt. Hệ thống có Web + Mobile, dữ liệu trẻ em nhạy cảm, team 5 người, 15 tuần. Tự viết đăng nhập/băm mật khẩu/phát JWT/refresh/khóa brute-force là nhiều code bảo mật phải tự bảo trì và tự bảo vệ trước hội đồng.

## Quyết định
Tách ba trách nhiệm:

| Thành phần | Trách nhiệm |
| --- | --- |
| **Keycloak** (1 realm `carenest`) | Đăng nhập (Authorization Code + PKCE), mật khẩu (băm, policy, reset), khóa brute-force, session, access/refresh token, ký token, realm role toàn cục |
| **Spring Security** (Resource Server) | Kiểm chữ ký JWT qua JWKS (local, cache), `iss`, `aud=carenest-api`, `exp`/`nbf`; map `realm_access.roles` → `ROLE_*`; `@PreAuthorize`; 401/403 dạng `ResponseJson` |
| **CareNest** (service + `security/authorization`) | Phân quyền nghiệp vụ theo scope: `user_accounts.keycloak_user_id` = JWT `sub`; StaffAssignment, Enrollment, GuardianChildLink trong PostgreSQL (ADR-0003) |

Quy tắc kèm theo:
- CareNest **không** có endpoint login/refresh, **không** lưu mật khẩu, **không** tự ký token, **không** lưu refresh token.
- Role Keycloak chỉ là role toàn cục: ADMIN, PRINCIPAL, VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF, PARENT. Phân công lớp/campus/con **không** làm thành role, **không** đưa vào JWT.
- Không dùng Keycloak Authorization Services (resource/policy/UMA) — quy tắc scope nằm trong CareNest.
- Không gọi Keycloak ở mỗi request. API khởi động được khi Keycloak tắt; token đã cấp vẫn kiểm được khi JWKS đã cache.
- Cấp tài khoản qua CareNest (Admin) → Keycloak Admin REST API trong `integration/keycloak` (RestClient, client `carenest-backend` dùng service account), có bù trừ khi ghi DB lỗi. Mật khẩu ban đầu: Admin nhập mật khẩu tạm (`temporary=true`, buộc đổi lần đầu, không lưu/log); có email và SMTP bật thì gửi thêm email `UPDATE_PASSWORD`.
- Trạng thái `user_accounts.status` kiểm ở mỗi request: tài khoản INACTIVE bị chặn ngay dù token còn hạn.

Giá trị realm (PROPOSED, chỉnh trong `docker/keycloak/import/carenest-realm.json`): access token 5 phút; SSO idle 30 phút, max 10 giờ; xoay refresh token; khóa tạm sau 5 lần sai; mật khẩu tối thiểu 8 ký tự, khác username; tắt tự đăng ký; `email`/`lastName` không bắt buộc (phụ huynh có thể không có email; họ tên đầy đủ lưu ở `firstName`), user không tự sửa tên/email.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Keycloak + Resource Server (chọn) | Chuẩn OAuth2/OIDC; không tự viết mật khẩu/token; brute-force, reset, session có sẵn; Web + Mobile dùng chung; dễ SSO sau này | Thêm 1 dịch vụ (~512MB RAM), nhóm phải học OIDC, cấu hình deploy nhiều hơn, phải đồng bộ danh tính Keycloak ↔ CareNest |
| Spring Security + JWT tự phát hành (định hướng cũ) | Ít hạ tầng | Tự viết login, băm mật khẩu, refresh/thu hồi, khóa brute-force, reset mật khẩu — nhiều code bảo mật tự bảo trì |
| Session cookie | Đơn giản cho Web | Mobile khó dùng, phải xử lý CSRF |
| Keycloak Authorization Services cho scope | Tập trung policy trong IAM | Gắn quy tắc nghiệp vụ vào cấu hình IAM; scope vốn nằm sẵn ở dữ liệu quan hệ |

## Hệ quả
- Code: `config/SecurityConfig`, `security/*` (converter, entry point, access denied handler), `GlobalExceptionHandler` map `AccessDeniedException`/`AuthenticationException`. Mọi controller method phải có `@PreAuthorize`; role chỉ là lớp 1, scope kiểm ở service/policy (lớp 2).
- Hạ tầng dev: `compose.yaml` thêm `keycloak` (26.8.0, realm import), `mailpit`, DB `keycloak` riêng trên cùng PostgreSQL. Hướng dẫn: `docs/system/KEYCLOAK.md`.
- Keycloak tắt ⇒ không đăng nhập/refresh/cấp tài khoản được; API vẫn phục vụ token còn hạn.
- Token đã cấp hợp lệ tối đa 5 phút sau logout; khóa tài khoản có hiệu lực ngay nhờ kiểm `status`.
- FE/APP: đăng nhập qua Keycloak (PKCE), gửi `Authorization: Bearer`, refresh/logout qua Keycloak; không gửi mật khẩu cho CareNest.

## Còn chờ
- Phụ huynh đăng nhập bằng gì (username do trường cấp / SĐT / email) — AUTH_CONTRACT.
- Mobile có dùng `offline_access` (phiên dài) không — APP quyết.
- Hạ tầng server (domain, HTTPS, backup DB `keycloak`) — `docs/system/DEPLOYMENT.md`.
