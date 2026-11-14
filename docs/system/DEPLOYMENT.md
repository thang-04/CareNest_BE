# Deployment — VPS demo (Docker Hub + Caddy + GitHub Actions)

Môi trường: 1 VPS demo `k8selchat` dùng chung với site khác, dữ liệu seed dev (plan `docs/plans/active/2026-10-30-vps-docker-deploy.md`). Web (FE) deploy riêng: `FE:docs/integration/DEPLOYMENT.md`.

Luồng: push `main` (merge PR từ `release/*`; PR vào `main` chỉ verify, push chỉ sửa tài liệu không chạy) ⇒ `.github/workflows/deploy.yml` chạy verify ⇒ build + push 2 image vào repo **private** Docker Hub `thangnd04/carenest-be` (tag theo nội dung `api-<hash>`, `keycloak-<hash>`; tag đã có trên Docker Hub ⇒ bỏ verify/build phần đó, compose không recreate service không đổi) ⇒ chép `compose.vps.yaml` + file mount lên `/data/thangnd/CAPSTONE/CareNest_BE` ⇒ VPS `docker pull` rồi `docker compose -f compose.vps.yaml up -d --no-build` ⇒ chờ health ⇒ đồng bộ site Caddy ⇒ kiểm issuer qua HTTPS.

```
Trình duyệt ─HTTPS─▶ Caddy host (giữ 80/443 của server)
  web.carenest.edu.vn   ├─ /api/* ─▶ 127.0.0.1:18310  api (Spring Boot; /api/auth/* = gateway ADR-0013)
                        └─ /      ─▶ 127.0.0.1:18300  FE
  auth.carenest.edu.vn  ───────────▶ 127.0.0.1:18320  Keycloak (chặn /admin, /realms/master)
  không ra ngoài:  Postgres 127.0.0.1:18330 · Keycloak management 18321 · Mailpit SMTP 18340 / UI 18341
```

## Mạng host và cổng

VPS mất chain iptables của Docker ⇒ không có network bridge (`FE:docs/knowledge/TROUBLESHOOTING.md`). Mọi service `network_mode: host`, gọi nhau qua `127.0.0.1:<port>`, **chỉ bind `127.0.0.1`** (firewall VPS tắt ⇒ bind `0.0.0.0` là lộ Internet). `*:8080` và `127.0.0.1:18080` của server đã có app khác dùng.

| Service | Cổng `127.0.0.1` | Cấu hình bind |
| --- | --- | --- |
| api | 18310 | `SERVER_ADDRESS`, `SERVER_PORT` |
| Keycloak | 18320, management 18321 | `KC_HTTP_HOST`, `KC_HTTP_PORT`, `KC_HTTP_MANAGEMENT_PORT`; `KC_CACHE=local` (không mở JGroups 7800) |
| PostgreSQL | 18330 | `listen_addresses=127.0.0.1`, `PGPORT` |
| Mailpit | SMTP 18340, UI 18341 | `MP_SMTP_BIND_ADDR`, `MP_UI_BIND_ADDR` |

Kiểm sau mỗi deploy: `sudo ss -ltnp | grep -E ':(183[1-4][0-9]|7800|9000)\b'` chỉ thấy `127.0.0.1`.

## File trong repo

| File | Vai trò |
| --- | --- |
| `compose.vps.yaml` | Stack VPS: postgres, keycloak (image có extension), mailpit, api; image qua `API_IMAGE`/`KEYCLOAK_IMAGE` |
| `deploy/vps.env.example` | Mẫu `.env` trên VPS (tên biến, không giá trị thật) |
| `deploy/caddy/carenest-auth.caddy` | Site `auth.carenest.edu.vn`; CI đẩy lên VPS, không sửa tay trên VPS |
| `.github/workflows/deploy.yml` | verify → push image → chép file → pull + up + chờ health → Caddy → smoke |
| `.dockerignore` | Context image api không chứa `.env`, `target`, `docs`… |

`compose.yaml` / `compose.auth.yaml` giữ cho máy dev.

## Cài một lần

1. **DNS:** bản ghi A `auth.carenest.edu.vn` trỏ IP VPS; `dig +short auth.carenest.edu.vn` đúng IP trước deploy đầu.
2. **Docker Hub:** tạo repo `thangnd04/carenest-be` **Private trước lần push đầu** (push tạo repo theo visibility mặc định, có thể public ⇒ lộ jar). Tạo 2 Personal access token: Read & Write (GitHub) và Read-only (VPS).
3. **GitHub repo CareNest_BE** › Settings › Environments: tạo `production` (Deployment branches: No restriction hoặc cho phép `main`). Repository secrets (Settings › Secrets and variables › Actions › Repository secrets): `DOCKERHUB_TOKEN` (Read & Write) — **bắt buộc ở cấp repo** vì job `plan`/`api`/`keycloak` không chạy trong environment; `VPS_HOST`, `VPS_PORT`, `VPS_USER` (`thangnd`), `VPS_SSH_KEY` (dùng chung key CI của FE được) đặt cấp repo hoặc trong `production` đều được (chỉ job `deploy` dùng).
4. **VPS, user `thangnd`:**
   ```bash
   docker login -u thangnd04          # dán token Read-only
   mkdir -p /data/thangnd/CAPSTONE/CareNest_BE && cd $_
   nano .env                           # theo deploy/vps.env.example, secret sinh bằng: openssl rand -base64 32
   chmod 600 .env
   ```
   Caddyfile đã `import /data/thangnd/CAPSTONE/caddy/*.caddy` và sudoers reload Caddy đã có từ lúc cài FE.
5. **Deploy đầu:** merge PR vào `main` (hoặc Actions › Deploy VPS › Run workflow trên `main`; nút chỉ hiện khi workflow đã có trên nhánh mặc định). Lần đầu Keycloak tạo DB, import realm + 8 user demo từ `docker/keycloak/vps/carenest-users-0.json` với mật khẩu `KEYCLOAK_DEV_USER_PASSWORD` (không phải `demo123` của máy local). Keycloak chỉ import khi realm chưa có: VPS đã import bản cũ (file dev, mật khẩu `demo123` công khai) thì owner phải tự đổi mật khẩu 8 user (`admin`, `principal`, `viceprincipal`, `teacher1`, `teacher2`, `parent1`, `parent2`, `kitchen`) trong admin console qua tunnel (mục dưới) — đổi `.env` không tự áp lại.
6. **Bật gateway auth** (ADR-0013, quyết định demo trong plan): từ máy dev mở tunnel `ssh -L 18320:127.0.0.1:18320 thangnd@<VPS>`, đặt `KEYCLOAK_URL=http://localhost:18320`, `KEYCLOAK_REALM=carenest`, `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` trong shell rồi `node docker/keycloak/auth/bootstrap.mjs` (preview, chỉ đọc) ⇒ duyệt ⇒ chạy lại với `--apply --enable`. Sau đó trên VPS sửa `.env` `CARENEST_AUTH_ENABLED=true` và `docker compose -f compose.vps.yaml up -d --no-build api` (cần `API_IMAGE`, xem Rollback).
7. **FE:** Caddy `/api/*` ⇒ `127.0.0.1:18310`; build với `VITE_AUTH_MODE=gateway`, `VITE_USE_MOCK=false` (repo FE).

## Quản trị qua SSH tunnel

- Keycloak admin console: `ssh -L 18320:127.0.0.1:18320 thangnd@<VPS>` ⇒ `http://localhost:18320/admin/` (`KC_HOSTNAME_ADMIN`).
- Mailpit (xem email OTP/đặt mật khẩu): `ssh -L 18341:127.0.0.1:18341 thangnd@<VPS>` ⇒ `http://localhost:18341`.
- Postgres: `ssh -L 18330:127.0.0.1:18330 thangnd@<VPS>`.

## Kiểm tra sau deploy

- Actions: job `verify`, `deploy` xanh; bước smoke thấy issuer `https://auth.carenest.edu.vn/realms/carenest`.
- `curl -I https://auth.carenest.edu.vn/admin/` ⇒ 404.
- Sau bước FE: `curl -s https://web.carenest.edu.vn/api/auth/csrf` ⇒ 200 từ CareNest.
- Lỗi: `cd /data/thangnd/CAPSTONE/CareNest_BE && docker compose -f compose.vps.yaml logs --tail 100 api keycloak`.

## Rollback

Tag theo nội dung; tính tag của commit cũ bằng lệnh dưới (chạy trong repo BE), hoặc xem label `org.opencontainers.image.revision` của image trên Docker Hub:

```bash
sha=<git-sha-cũ>
tag() { git ls-tree -r "$sha" -- "$@" | grep -v '\.md$' | sha1sum | cut -c1-12; }
echo "api-$(tag src pom.xml .mvn mvnw Dockerfile docs/api/openapi.yaml) keycloak-$(tag docker/keycloak compose.yaml compose.auth.yaml)"
```

Chỉ rollback khi giữa 2 bản **không có migration Flyway** (bản cũ không chạy được trên schema mới; lỗi migration ⇒ sửa bằng migration mới):

```bash
cd /data/thangnd/CAPSTONE/CareNest_BE
export API_IMAGE=thangnd04/carenest-be:api-<hash-cũ> KEYCLOAK_IMAGE=thangnd04/carenest-be:keycloak-<hash-cũ>
docker pull "$API_IMAGE" && docker pull "$KEYCLOAK_IMAGE"
docker compose -f compose.vps.yaml up -d --no-build
```

Tắt gateway khẩn cấp: `.env` `CARENEST_AUTH_ENABLED=false` rồi `up -d --no-build api`; disable client `carenest-auth-gateway` trong admin console.

Ép build lại một image khi nội dung không đổi (vd. cập nhật base image): xoá tag tương ứng trên Docker Hub rồi Run workflow trên `main`.

## Môi trường dev (nhánh `dev`)

Luồng: PR vào `dev` (verify) ⇒ merge ⇒ job `deploy-dev` deploy lên `https://dev.carenest.edu.vn` ⇒ test ⇒ `release/*` ⇒ PR vào `main`. Image push từ `dev` đã verify nên release cùng nội dung vào `main` dùng lại tag, chỉ pull + restart.

```
dev.carenest.edu.vn       ├─ /api/* ─▶ 127.0.0.1:18311  api dev       (site Caddy của repo FE: carenest-dev.caddy)
                          └─ /      ─▶ 127.0.0.1:18301  FE dev
auth-dev.carenest.edu.vn  ───────────▶ 127.0.0.1:18322  Keycloak dev  (management 18323; deploy/caddy/carenest-auth-dev.caddy)
dùng chung production:  Postgres 18330 (DB + role carenest_dev, keycloak_dev) · Mailpit 18340
```

- `compose.dev.yaml` (project `carenest-be-dev`) chỉ có `keycloak` + `api`; thư mục VPS `/data/thangnd/CAPSTONE/CareNest_BE-dev`, `.env` theo `deploy/dev.env.example` với secret riêng.
- S3, SMS (textbee), email BE, AI: `compose.dev.yaml` truyền `CARENEST_EDUCATION_MEDIA_BUCKET`, `AWS_*`, `CARENEST_SMS_PROVIDER`, `TEXTBEE_*`, `CARENEST_EMAIL_*`, `CARENEST_AI_*` từ `.env` dev; trống ⇒ S3 báo 8010, SMS/email giả, AI tắt. Đổi `.env` xong chạy lại `docker compose -f compose.dev.yaml up -d --no-build api` (kèm `API_IMAGE`, `KEYCLOAK_IMAGE` đang chạy).
- Keycloak riêng vì seed `db/dev` gắn cứng ID user Keycloak (`user_accounts.keycloak_user_id`) mà ID user phải duy nhất trên cả instance ⇒ realm thứ hai cùng Keycloak không import được 8 user demo.
- Postgres là của stack production: production dừng thì dev cũng dừng; `deploy-dev` báo lỗi rõ khi không thấy container Postgres.
- GitHub: environment `dev` (deployment branch `dev`); `VPS_*` là repository secret hoặc chép vào environment `dev`.

**Cài một lần:** DNS A `auth-dev` trỏ IP VPS. Tạo role/DB trên Postgres production (script init chỉ chạy khi volume trống), thu hồi `CONNECT` của `PUBLIC` để role dev không vào DB production:

```sql
CREATE ROLE carenest_dev LOGIN PASSWORD '<secret>';  CREATE DATABASE carenest_dev OWNER carenest_dev;
CREATE ROLE keycloak_dev LOGIN PASSWORD '<secret>';  CREATE DATABASE keycloak_dev OWNER keycloak_dev;
REVOKE CONNECT ON DATABASE carenest, keycloak, carenest_dev, keycloak_dev FROM PUBLIC;
```

Sau deploy dev đầu tiên: bật gateway như mục Cài một lần bước 6 nhưng tunnel `ssh -L 18322:127.0.0.1:18322`, `KEYCLOAK_URL=http://localhost:18322` và secret trong `.env` dev; rồi `CARENEST_AUTH_ENABLED=true` trong `.env` dev và `docker compose -f compose.dev.yaml up -d --no-build api` (kèm `API_IMAGE`, `KEYCLOAK_IMAGE` đang chạy).

**Reset DB dev** (vd. sửa migration đã chạy trên dev làm Flyway báo checksum mismatch; chỉ mất dữ liệu dev): dừng `api` dev, `DROP DATABASE carenest_dev; CREATE DATABASE carenest_dev OWNER carenest_dev;` rồi chạy lại `deploy-dev`.

## Email từ BE — Gmail SMTP

BE gửi email qua `integration/email/EmailGateway`. Email Keycloak (đặt mật khẩu/OTP) do realm `smtpServer` gửi; `compose.yaml` local trỏ cả hai vào cùng Gmail (không còn Mailpit) (để trống username/password thì compose vẫn chạy, email Keycloak gửi thất bại). Mặc định `CARENEST_EMAIL_PROVIDER=log`: không gửi, chỉ log số người nhận. `smtp` ⇒ `SmtpEmailGateway`, mặc định `smtp.gmail.com:587` STARTTLS.

Bật Gmail:
1. Tài khoản Gmail dùng gửi (nên tạo tài khoản riêng cho team) → Google Account → Security → bật **2-Step Verification**.
2. Security → **App passwords** → tạo mật khẩu ứng dụng (16 ký tự). Không dùng mật khẩu đăng nhập Gmail (bị từ chối 535).
3. `.env`: `CARENEST_EMAIL_PROVIDER=smtp`, `CARENEST_EMAIL_SMTP_USERNAME=<gmail>`, `CARENEST_EMAIL_SMTP_PASSWORD=<app password, bỏ dấu cách>`; tùy chọn `CARENEST_EMAIL_FROM` (phải là chính tài khoản hoặc alias đã xác minh, trống ⇒ dùng username), `CARENEST_EMAIL_FROM_NAME`.
4. Thiếu biến ⇒ API dừng lúc khởi động, báo tên biến thiếu.
5. Keycloak local đã import realm từ trước (`--import-realm` bỏ qua realm có sẵn) ⇒ realm vẫn trỏ Mailpit. Sửa ở Admin Console → realm `carenest` → Realm settings → Email: host `smtp.gmail.com`, port `587`, StartTLS, Authentication = Gmail + App Password, From = Gmail; hoặc reset DB Keycloak (`KEYCLOAK.md`).

Gmail cá nhân giới hạn ~500 email/ngày, chỉ hợp dev/demo. Lộ App Password ⇒ thu hồi trong App passwords. `compose.vps.yaml`, `compose.dev.yaml` chưa truyền biến `CARENEST_EMAIL_*` nên VPS: BE ở chế độ log, Keycloak vẫn gửi vào Mailpit.

Gọi từ service (gửi đồng bộ; email không phải bước bắt buộc ⇒ bắt `EmailDeliveryException`, không làm hỏng nghiệp vụ đã lưu):

```java
emailGateway.send(EmailMessage.html(guardianEmail, "Thông báo từ CareNest", html));
```

## Chuyển sang dữ liệu thật (Phase 3 — chưa làm)

Điều kiện: review bảo mật gateway ADR-0013 xong; backup Postgres theo lịch đã thử khôi phục; người quản lý server đồng ý; SMTP thật. Xoá volume là **mất toàn bộ dữ liệu** của DB `carenest` và `keycloak`:

```bash
docker compose -f compose.vps.yaml down -v
# .env: SPRING_PROFILES_ACTIVE=  (rỗng) · SMTP thật; compose.vps.yaml: bỏ mount docker/keycloak/vps/carenest-users-0.json
docker compose -f compose.vps.yaml up -d --no-build   # kèm API_IMAGE/KEYCLOAK_IMAGE hiện tại
```

Rồi tạo tài khoản quản trị theo `docs/system/KEYCLOAK.md` mục 6.
