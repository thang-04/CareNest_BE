---
paths:
  - "src/main/java/com/carenest/security/**"
  - "src/main/java/com/carenest/config/SecurityConfig.java"
  - "src/main/java/com/carenest/integration/keycloak/**"
  - "src/main/resources/application*.yml"
  - "docker/keycloak/**"
  - ".env.example"
---

# Security — CareNest_BE

Nguồn chuẩn: guide mục 2 (Auth), 8.5, 9, 10; `docs/architecture/SECURITY.md`; ADR-0011; `docs/system/KEYCLOAK.md`.

- Auth = **Keycloak + Spring Security Resource Server** (ADR-0011). KHÔNG viết login/refresh/`JwtService`/filter JWT tự chế, KHÔNG lưu mật khẩu hay refresh token, KHÔNG tự ký JWT, KHÔNG gọi Keycloak để kiểm token mỗi request.
- Ngoại lệ Web đã duyệt: ADR-0013 cho gateway opt-in dùng confidential Direct Grant, token mã hóa tại server và cookie opaque + CSRF; Keycloak vẫn xác minh mật khẩu/ký access JWT. Mobile giữ PKCE. Không mở rộng ngoại lệ sang API nghiệp vụ hay lưu mật khẩu.
- Danh tính hiện tại lấy từ JWT `sub` (= `user_accounts.keycloak_user_id`); KHÔNG tin `userId`/`parentId`/`teacherId` client gửi.
- Role Keycloak chỉ là role toàn cục; KHÔNG tạo role theo lớp/campus/trẻ, KHÔNG đưa phân công/danh sách trẻ vào JWT.
- Mọi controller method có `@PreAuthorize("@permissionGuard.has('CODE')")` (lớp 1, ADR-0017; mã phải có trong enum `Permission`, không dùng `hasRole` cho endpoint nghiệp vụ). Quyền = permission + scope campus/class/child kiểm ở service/policy (lớp 2) — có quyền không có nghĩa được xem mọi dữ liệu (AUTH-13). Truy cập theo id ⇒ xác minh thuộc scope (IDOR).
- 401/403 từ filter ghi `ResponseJson` qua entry point/access denied handler (guide 8.5); 403 từ `@PreAuthorize` qua `GlobalExceptionHandler`.
- Nhà bếp không xem hồ sơ sức khỏe/đánh giá; Admin không mặc định xem dữ liệu trẻ (không bypass scope).
- Secret qua biến môi trường; `.env.example` chỉ tên biến/giá trị giả; không commit `.env`. Realm import dùng placeholder `${ENV}` cho secret.
- Không log password, token, header `Authorization`, refresh token, secret, dữ liệu sức khỏe/cá nhân trẻ.
- Có test cho trường hợp bị từ chối (lớp khác, cơ sở khác, phụ huynh không liên kết, assignment hết hạn, tài khoản INACTIVE).
