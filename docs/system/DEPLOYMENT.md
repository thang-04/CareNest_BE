# Deployment — VPS demo (Docker Hub + Caddy + GitHub Actions)

Môi trường: 1 VPS demo `k8selchat` dùng chung với site khác, dữ liệu seed dev (plan `docs/plans/active/2026-10-30-vps-docker-deploy.md`). Web (FE) deploy riêng: `FE:docs/integration/DEPLOYMENT.md`.

Luồng: push `dev` ⇒ `.github/workflows/deploy.yml` chạy verify ⇒ build + push 2 image vào repo **private** Docker Hub `thangnd04/carenest-be` (tag `api-<git-sha>`, `keycloak-<git-sha>`) ⇒ chép `compose.vps.yaml` + file mount lên `/data/thangnd/CAPSTONE/CareNest_BE` ⇒ VPS `docker pull` rồi `docker compose -f compose.vps.yaml up -d --no-build` ⇒ chờ health ⇒ đồng bộ site Caddy ⇒ kiểm issuer qua HTTPS.

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
3. **GitHub repo CareNest_BE** › Settings › Environments: tạo `production`; Secrets: `DOCKERHUB_TOKEN` (Read & Write), `VPS_HOST`, `VPS_PORT`, `VPS_USER` (`thangnd`), `VPS_SSH_KEY` (dùng chung key CI của FE được).
4. **VPS, user `thangnd`:**
   ```bash
   docker login -u thangnd04          # dán token Read-only
   mkdir -p /data/thangnd/CAPSTONE/CareNest_BE && cd $_
   nano .env                           # theo deploy/vps.env.example, secret sinh bằng: openssl rand -base64 32
   chmod 600 .env
   ```
   Caddyfile đã `import /data/thangnd/CAPSTONE/caddy/*.caddy` và sudoers reload Caddy đã có từ lúc cài FE.
5. **Deploy đầu:** push `dev` (hoặc Actions › Deploy VPS › Run workflow). Lần đầu Keycloak tạo DB, import realm + user dev.
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

Tag theo commit có trên Docker Hub. Chỉ rollback khi giữa 2 bản **không có migration Flyway** (bản cũ không chạy được trên schema mới; lỗi migration ⇒ sửa bằng migration mới):

```bash
cd /data/thangnd/CAPSTONE/CareNest_BE
export API_IMAGE=thangnd04/carenest-be:api-<git-sha-cũ> KEYCLOAK_IMAGE=thangnd04/carenest-be:keycloak-<git-sha-cũ>
docker pull "$API_IMAGE" && docker pull "$KEYCLOAK_IMAGE"
docker compose -f compose.vps.yaml up -d --no-build
```

Tắt gateway khẩn cấp: `.env` `CARENEST_AUTH_ENABLED=false` rồi `up -d --no-build api`; disable client `carenest-auth-gateway` trong admin console.

## Chuyển sang dữ liệu thật (Phase 3 — chưa làm)

Điều kiện: review bảo mật gateway ADR-0013 xong; backup Postgres theo lịch đã thử khôi phục; người quản lý server đồng ý; SMTP thật. Xoá volume là **mất toàn bộ dữ liệu** của DB `carenest` và `keycloak`:

```bash
docker compose -f compose.vps.yaml down -v
# .env: SPRING_PROFILES_ACTIVE=  (rỗng) · SMTP thật; compose.vps.yaml: bỏ mount carenest-users-0.json
docker compose -f compose.vps.yaml up -d --no-build   # kèm API_IMAGE/KEYCLOAK_IMAGE hiện tại
```

Rồi tạo tài khoản quản trị theo `docs/system/KEYCLOAK.md` mục 6.
