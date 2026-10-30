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

### FE `:5173` báo "Không thể kết nối máy chủ" ngay sau khi build lại API; `GET /api/auth/csrf` trả 401, preflight CORS 403 không có `Access-Control-Allow-Origin`
- Nguyên nhân: build/recreate API chỉ với `compose.yaml` (`docker compose up -d --build api`) trong khi stack đang chạy overlay `compose.auth.yaml` ⇒ container API mới mất `CARENEST_AUTH_ENABLED=true`, secret gateway và origin `http://localhost:5173` (mặc định `application.yml`: auth tắt, CORS chỉ `:3000`). Endpoint `/auth/*` không còn `permitAll` nên trả 401.
- Kiểm: `docker inspect carenest_be-keycloak-1 --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}'` thấy cả `compose.auth.yaml`; `docker inspect carenest_be-api-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep CARENEST_AUTH_ENABLED` không có dòng nào.
- Xử lý: luôn dùng cùng bộ file: `docker compose -f compose.yaml -f compose.auth.yaml up -d --build --no-deps api`; kiểm lại `curl -H "Origin: http://localhost:5173" http://localhost:8080/api/auth/csrf` trả 200.

### `POST /api/auth/login` trả 503 `Authentication service unavailable`; Keycloak log `client_not_found` cho `carenest-auth-gateway`
- Nguyên nhân: realm `carenest` đã có trong DB Keycloak từ base Compose ⇒ `compose.auth.yaml` import bị bỏ qua (`Realm 'carenest' already exists. Import skipped`), client gateway + flow chưa từng được tạo. Token endpoint trả 401 `invalid_client` ⇒ BE map 503.
- Xử lý: chạy bootstrap theo `docker/keycloak/auth/README.md` mục Opt-in: preview `node docker/keycloak/auth/bootstrap.mjs` (cần `KC_BOOTSTRAP_ADMIN_*`, `KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET` trong env), duyệt rồi `--apply --enable` (chỉ môi trường local/test).
- Kiểm: `docker logs carenest_be-keycloak-1 2>&1 | grep -E "Import skipped|client_not_found"`.

### Dev user (`teacher.a`…) báo sai mật khẩu — Keycloak `user_not_found`
- Nguyên nhân: realm `carenest` đã tồn tại trước khi mount `docker/keycloak/dev/carenest-users-0.json`; `--import-realm` dùng `IGNORE_EXISTING` nên log `Realm 'carenest' already exists. Import skipped` và user dev không bao giờ được tạo. DB app vẫn có seed `user_accounts` nên lệch hai bên. API map `invalid_grant` thành 401 sai mật khẩu (không lộ user tồn tại hay không).
- Kiểm: `docker compose logs keycloak | grep LOGIN_ERROR` thấy `error="user_not_found"`; liệt kê user realm: `docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d keycloak -tAc "select username from user_entity u join realm r on r.id=u.realm_id where r.name=$carenest$"'`.
- Xử lý (giữ realm, không xóa volume): partial import file user dev (mật khẩu đã hash sẵn, gửi nguyên file) với `ifResourceExists=SKIP` — hoặc `OVERWRITE` khi file user dev đổi username/email/mật khẩu (Admin REST `POST /admin/realms/carenest/partialImport`). Cách nặng hơn: xóa DB `keycloak` để import lại từ đầu — mất user/client tạo tay và cấu hình gateway đã bootstrap.
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

### `Found more than one migration with version N` sau khi đổi nhánh
- Nguyên nhân: `target/classes/db/migration` còn migration của nhánh trước (Maven không xóa resource đã bỏ khỏi source).
- Xử lý: `mvnw clean` rồi chạy lại test/app. Không sửa/đổi tên migration trong source khi source chỉ có 1 file version đó.
- Incident: ENV-261016-flyway-duplicate-version-stale-target

### `FATAL: password authentication failed for user "carenest"`
- Nguyên nhân:
  - Spring không đọc `.env`. Postgres (compose) được tạo với `POSTGRES_PASSWORD` lấy từ `.env`, còn app chạy bằng `mvnw` dùng mặc định `DB_PASSWORD=carenest`. <!-- carenest:allow-secret: mặc định dev trong application.yml -->
  - Hoặc volume `postgres-data` đã được tạo trước đó với mật khẩu khác.
- Xử lý:
  - Khai báo `DB_PASSWORD` (env hoặc IDE run config) khớp với `POSTGRES_PASSWORD`.
  - Hoặc xóa volume nếu dữ liệu local không cần giữ: `docker compose down -v`.
- Nguồn: suy ra từ phân tích cấu hình (`.env.example`, `compose.yaml`, `application.yml`); chưa có incident.

### VPS: `docker network create` lỗi `No chain/target/match by that name`; container bridge không ra Internet
- Nguyên nhân: VPS `k8selchat` mất chain iptables của Docker (tầng k8s của server, ngoài quyền nhóm). Không sửa iptables, không restart Docker trên server chung.
- Xử lý: mọi service CareNest chạy `network_mode: host`, bind `127.0.0.1:<port>`, gọi nhau qua `127.0.0.1` (`compose.vps.yaml`); image build ở GitHub Actions, VPS chỉ pull. Chi tiết chẩn đoán: `FE:docs/knowledge/TROUBLESHOOTING.md`.
- Nguồn: user chạy lệnh trên VPS, 2026-10-21 (FE) và 2026-10-30.

### VPS: cổng `8080` / `18080` đã bị chiếm; `web.carenest.edu.vn/api/*` trả nội dung lạ
- Nguyên nhân: `*:8080` và `127.0.0.1:18080` là `java` của app khác trên server chung (`ss -ltnp`, 2026-10-30). Caddy FE ban đầu proxy `/api/*` ⇒ `127.0.0.1:8080` nên đi vào app đó.
- Xử lý: API CareNest nghe `127.0.0.1:18310`; Caddy FE phải trỏ `/api/*` ⇒ `127.0.0.1:18310` (`FE:deploy/caddy/carenest-web.caddy`). Chọn cổng mới: `sudo ss -ltnp | grep :<port>` rỗng rồi ghi bảng cổng `docs/system/DEPLOYMENT.md`.

### Hook chặn ghi file tên `.env.*` (vd. `.env.vps.example`): `Không đọc/ghi .env hay khóa bí mật`
- Nguyên nhân: hook bảo vệ secret của repo khớp mọi tên `.env*`, kể cả file mẫu mới.
- Xử lý: mẫu biến môi trường mới đặt tên không bắt đầu bằng `.env` — mẫu VPS ở `deploy/vps.env.example`. Không lách hook.

## Format

```markdown
### <chuỗi lỗi hoặc triệu chứng ngắn>
- Nguyên nhân / Xử lý (lệnh cụ thể) / Đã thử không hiệu quả / Incident: ENV-YYMMDD-slug
```
