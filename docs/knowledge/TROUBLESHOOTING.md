# Troubleshooting

Lỗi môi trường, build, chạy local, deploy. Mỗi mục ghi: chuỗi lỗi nguyên văn → nguyên nhân → cách xử lý → cách đã thử không hiệu quả. Có incident thì link ID. Cấu hình: `README.md`, mục Cấu hình.

Keycloak/xác thực: bảng lỗi đầy đủ ở `docs/system/KEYCLOAK.md` mục 8.

### FE báo không kết nối được API; Docker Desktop `starting` kéo dài
- Quan sát: chỉ Vite 5173 lắng nghe; API 8080, Keycloak 8180 và PostgreSQL 5432 không lắng nghe. Docker backend lặp `init GET /ping ... context deadline exceeded`.
- Xử lý: kiểm `docker desktop status` phải là `running` trước khi chạy Compose; nếu restart thường thất bại, không tự `--force` khi còn project khác. Kiểm history Flyway của volume `carenest_be_postgres-data` trước khi khởi động API trên DB cũ.
- Đã thử: `docker version` trả Server 29.6.1 nhưng engine vẫn không start container; `docker desktop restart --timeout 120` thất bại.
- Incident: ENV-261013-docker-desktop-init-timeout

### React `:5173` báo lỗi CSRF/phiên bảo mật; `GET /api/auth/csrf` trả `403 Invalid CORS request`
- Nguyên nhân: FE chạy `VITE_AUTH_MODE=gateway` nhưng container `api` có `CARENEST_AUTH_ENABLED=false` (mặc định `.env.example`/`compose.auth.yaml`). Khi tắt: CORS chỉ cho `CARENEST_CORS_ALLOWED_ORIGINS`, `/auth/**` không permitAll, CSRF tắt ⇒ bước lấy CSRF đầu tiên của FE fail.
- Xử lý: `.env` BE đặt `CARENEST_AUTH_ENABLED=true`; chạy HTTP localhost thì `CARENEST_AUTH_SECURE_COOKIES=false`; tạo lại API: `docker compose -f compose.yaml -f compose.auth.yaml up -d api`. Kiểm: `curl -i -H "Origin: http://localhost:5173" http://localhost:8080/api/auth/csrf` phải 200 + `Set-Cookie: XSRF-TOKEN`.
- Kiểm nhanh không lộ secret: `docker inspect carenest_be-api-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep CARENEST_AUTH_ENABLED`.

### `POST /api/auth/login` trả 503 `Authentication service unavailable`; Keycloak log `client_not_found` cho `carenest-auth-gateway`
- Nguyên nhân: realm `carenest` đã có trong DB Keycloak từ base Compose ⇒ `compose.auth.yaml` import bị bỏ qua (`Realm 'carenest' already exists. Import skipped`), client gateway + flow chưa từng được tạo. Token endpoint trả 401 `invalid_client` ⇒ BE map 503.
- Xử lý: chạy bootstrap theo `docker/keycloak/auth/README.md` mục Opt-in: preview `node docker/keycloak/auth/bootstrap.mjs` (cần `KC_BOOTSTRAP_ADMIN_*`, `KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET` trong env), duyệt rồi `--apply --enable` (chỉ môi trường local/test).
- Kiểm: `docker logs carenest_be-keycloak-1 2>&1 | grep -E "Import skipped|client_not_found"`.

### Dev user (`teacher.a`…) báo sai mật khẩu — Keycloak `user_not_found`
- Nguyên nhân: realm `carenest` đã tồn tại trước khi mount `docker/keycloak/dev/carenest-users-0.json`; `--import-realm` dùng `IGNORE_EXISTING` nên log `Realm 'carenest' already exists. Import skipped` và user dev không bao giờ được tạo. DB app vẫn có seed `user_accounts` nên lệch hai bên. API map `invalid_grant` thành 401 sai mật khẩu (không lộ user tồn tại hay không).
- Kiểm: `docker compose logs keycloak | grep LOGIN_ERROR` thấy `error="user_not_found"`; liệt kê user realm: `docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d keycloak -tAc "select username from user_entity u join realm r on r.id=u.realm_id where r.name=$carenest$"'`.
- Xử lý (giữ realm, không xóa volume): partial import file user dev với `ifResourceExists=SKIP`, thay placeholder `${KEYCLOAK_DEV_USER_PASSWORD}` trước khi gửi (Admin REST `POST /admin/realms/carenest/partialImport`). Cách nặng hơn: xóa DB `keycloak` để import lại từ đầu — mất user/client tạo tay và cấu hình gateway đã bootstrap.
- Incident: ENV-261013-keycloak-dev-users-missing

### `invalid_grant` — `Account is not fully set up` (Keycloak)
- Nguyên nhân: user còn required action (mật khẩu tạm) hoặc thiếu field bắt buộc của user profile.
- Xử lý: đăng nhập trình duyệt một lần; kiểm *Realm settings → User profile* (`email`, `lastName` không bắt buộc).
- Đã thử không hiệu quả: kiểm `requiredActions` (rỗng).
- Incident: ENV-261004-keycloak-user-profile-account-not-fully-set-up

### `make mountpoint ... read-only file system` khi `docker compose up keycloak`
- Nguyên nhân: mount một file vào bên trong thư mục đã bind mount `:ro` (Docker không tạo được mountpoint con).
- Xử lý: mount từng file vào thư mục đích (như `compose.yaml` hiện tại), không mount cả thư mục rồi lồng file.

### `docker exec ... psql -f /tmp/x.sql` báo `C:/Users/.../Temp/x.sql: No such file` (Git Bash trên Windows)
- Nguyên nhân: Git Bash tự đổi đường dẫn dạng `/tmp/...` thành đường dẫn Windows.
- Xử lý: đưa file qua stdin: `docker exec -i <container> psql -U carenest -d carenest < file.sql`, hoặc đặt `MSYS_NO_PATHCONV=1`.

### `FATAL: password authentication failed for user "carenest"`
- Nguyên nhân:
  - Spring không đọc `.env`. Postgres (compose) được tạo với `POSTGRES_PASSWORD` lấy từ `.env`, còn app chạy bằng `mvnw` dùng mặc định `DB_PASSWORD=carenest`. <!-- carenest:allow-secret: mặc định dev trong application.yml -->
  - Hoặc volume `postgres-data` đã được tạo trước đó với mật khẩu khác.
- Xử lý:
  - Khai báo `DB_PASSWORD` (env hoặc IDE run config) khớp với `POSTGRES_PASSWORD`.
  - Hoặc xóa volume nếu dữ liệu local không cần giữ: `docker compose down -v`.
- Nguồn: suy ra từ phân tích cấu hình (`.env.example`, `compose.yaml`, `application.yml`); chưa có incident.

## Format

```markdown
### <chuỗi lỗi hoặc triệu chứng ngắn>
- Nguyên nhân / Xử lý (lệnh cụ thể) / Đã thử không hiệu quả / Incident: ENV-YYMMDD-slug
```
