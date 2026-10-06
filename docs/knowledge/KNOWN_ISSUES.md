# Known Limitations

Giới hạn **cố ý hoặc chưa làm** của hệ thống mà agent cần biết để không coi là bug, ví dụ "chưa hỗ trợ X", "contract thiếu Y". Bug (kể cả chưa rõ root cause) **không** ghi ở đây mà ghi vào `ISSUE_INDEX.md` với status `open`. Trạng thái dự án và PENDING xem `docs/context/CURRENT_STATE.md`.

| ID | Module | Giới hạn | Cách làm việc với nó | Ngày |
| --- | --- | --- | --- | --- |
| KI-001 | toàn hệ thống | Mới có xác thực Keycloak, phạm vi campus/lớp/trẻ, cấp/khóa tài khoản; nghiệp vụ khác chưa có; mọi rule PENDING (P-xx) chưa có hành vi chốt (Mở) | Làm cấu hình được, không hard-code | 2026-10-03 |
| KI-002 | operations / identity-access | User báo pgAdmin đọc DB CareNest gặp `permission denied for table user_accounts` (SQLSTATE `42501`); chưa xác minh user/role và owner trong kết nối thực tế (Đang điều tra) | Kiểm `current_user`, `session_user`, owner và quyền SELECT; kết nối lại bằng user khởi tạo PostgreSQL, chưa cấp thêm quyền | 2026-10-06 |
| KI-003 | identity-access | Validate body và tra `CurrentUser` chạy trước `@PreAuthorize` ⇒ non-ADMIN gửi body sai tới `POST/PATCH /accounts` nhận 400 kèm lỗi theo field thay vì 403 (lộ tên field — vốn đã công khai trong OpenAPI) (Chấp nhận (V1)) | Không cần; nếu muốn chặn sớm thì thêm rule URL `hasRole('ADMIN')` trong SecurityConfig | 2026-10-06 |
| KI-004 | identity-access | `createUser` timeout sau khi Keycloak đã tạo user ⇒ trả 503, user Keycloak (không role, không hồ sơ) còn lại; tạo lại cùng username nhận 409 (Mở) | Xóa user đó trên Keycloak Console rồi tạo lại qua API | 2026-10-06 |
| KI-005 | identity-access | Tạo tài khoản chỉ bằng email (không mật khẩu tạm) mà gửi email lỗi ⇒ tài khoản chưa có mật khẩu; chưa có API gửi lại email/đặt lại mật khẩu (Mở) | Người dùng dùng "Quên mật khẩu" ở trang đăng nhập (cần SMTP) hoặc Admin đặt mật khẩu trên Keycloak Console | 2026-10-06 |
| KI-006 | identity-access | `PATCH /accounts/{id}/status` không có khóa lạc quan: 2 Admin đổi đồng thời có thể làm DB và Keycloak lệch (lệch theo hướng chặn) (Chấp nhận (V1)) | Đổi lại trạng thái lần nữa | 2026-10-06 |
| KI-007 | identity-access | ADMIN tự khóa mình/khóa ADMIN khác được (chưa có rule) (Chờ chốt nghiệp vụ) | Bootstrap admin Keycloak + SQL mở lại | 2026-10-06 |
| KI-008 | identity-access | Service account `carenest-backend` có `manage-users` ⇒ map được mọi realm role (kể cả ADMIN) và reset mật khẩu mọi user; lộ `KEYCLOAK_ADMIN_CLIENT_SECRET` = chiếm quyền CareNest. ADMIN tạo được tài khoản PRINCIPAL (khi có API phân công, cần tách quyền để giữ AUTH-06) (Chờ quyết định khi deploy) | Giữ secret trong secret store, xoay khi nghi lộ; cân nhắc Fine-Grained Admin Permissions khi deploy | 2026-10-06 |
| KI-009 | operations | Swagger/OpenAPI public ở mọi profile (lộ schema API admin trên server) (Chờ DEPLOYMENT) | Khi deploy: `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` | 2026-10-06 |
