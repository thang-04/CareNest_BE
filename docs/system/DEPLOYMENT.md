# Deployment — VPS demo (Docker Hub + Caddy + GitHub Actions)

Môi trường: 1 VPS demo `k8selchat` dùng chung với site khác, dữ liệu seed dev (plan `docs/plans/active/2026-10-30-vps-docker-deploy.md`). Web (FE) deploy riêng: `FE:docs/integration/DEPLOYMENT.md`.

Luồng: push `main` (merge PR từ `release/*`; PR vào `main` chỉ verify, push chỉ sửa tài liệu không chạy) ⇒ `.github/workflows/deploy.yml` chạy verify ⇒ build + push 2 image vào repo **private** Docker Hub `thangnd04/carenest-be` (tag theo nội dung `api-<hash>`, `keycloak-<hash>`; tag đã có trên Docker Hub ⇒ bỏ verify/build phần đó, compose không recreate service không đổi) ⇒ chép `compose.vps.yaml` + file mount lên `/data/thangnd/CAPSTONE/CareNest_BE` ⇒ VPS `docker pull` rồi `docker compose -f compose.vps.yaml up -d --no-build` ⇒ chờ health ⇒ đồng bộ site Caddy ⇒ kiểm issuer qua HTTPS.

```
Trình duyệt ─HTTPS─▶ Caddy host (giữ 80/443 của server)
  thuonghong.carenest.edu.vn  ├─ /api/* ─▶ 127.0.0.1:18310  api (Spring Boot; /api/auth/* = gateway ADR-0013)
                              └─ /      ─▶ 127.0.0.1:18300  FE
  auth.carenest.edu.vn  ─────────────────▶ 127.0.0.1:18320  Keycloak (chặn /admin, /realms/master)
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
| `deploy/remote-deploy.sh` | Chạy trên VPS cho cả production và dev: kiểm key `.env` → dump DB → áp file mới → up → chờ health; fail ⇒ tự rollback (mục Rollback) |
| `.github/workflows/deploy.yml` | verify → push image → chép file vào `.incoming/` → `remote-deploy.sh` → Caddy → smoke (fail ⇒ rollback) |
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
5. **Deploy đầu:** merge PR vào `main` (hoặc Actions › Deploy VPS › Run workflow trên `main`; nút chỉ hiện khi workflow đã có trên nhánh mặc định). Lần đầu Keycloak tạo DB, import realm + user của bộ dữ liệu dev từ `docker/keycloak/vps/carenest-users-0.json` với mật khẩu `KEYCLOAK_DEV_USER_PASSWORD` (≥ 8 ký tự theo chính sách realm; không phải mật khẩu local). Keycloak chỉ import khi realm chưa có: đổi file user ⇒ dựng lại theo mục "Dựng lại bộ dữ liệu dev" — đổi `.env` không tự áp lại.
6. **Bật gateway auth** (ADR-0013, quyết định demo trong plan): từ máy dev mở tunnel `ssh -L 18320:127.0.0.1:18320 thangnd@<VPS>`, đặt `KEYCLOAK_URL=http://localhost:18320`, `KEYCLOAK_REALM=carenest`, `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` trong shell rồi `node docker/keycloak/auth/bootstrap.mjs` (preview, chỉ đọc) ⇒ duyệt ⇒ chạy lại với `--apply --enable`. Sau đó trên VPS sửa `.env` `CARENEST_AUTH_ENABLED=true` và `docker compose -f compose.vps.yaml up -d --no-build api` (cần `API_IMAGE`, xem Rollback).
7. **FE:** Caddy `/api/*` ⇒ `127.0.0.1:18310`; build với `VITE_AUTH_MODE=gateway`, `VITE_USE_MOCK=false` (repo FE).

**Đổi domain web** (16/11/2026: `web` ⇒ `thuonghong.carenest.edu.vn`): sửa cùng lúc `compose.vps.yaml` (`CARENEST_WEB_ORIGIN`, `CARENEST_AUTH_WEB_ORIGIN`, `CARENEST_CORS_ALLOWED_ORIGINS`) và FE (`deploy/caddy/carenest-web.caddy`, `SITE_URL` trong `deploy.yml`); sửa tay trên VPS sẽ bị deploy `main` sau ghi đè. Realm đã có thì `--import-realm` bỏ qua ⇒ client web trong realm `carenest` vẫn giữ origin cũ: thêm domain mới vào Valid redirect URIs, Post logout redirect URIs, Web origins ở Admin Console. Bucket S3 có CORS theo origin ⇒ thêm domain mới.

## Quản trị qua SSH tunnel

- Keycloak admin console: `ssh -L 18320:127.0.0.1:18320 thangnd@<VPS>` ⇒ `http://localhost:18320/admin/` (`KC_HOSTNAME_ADMIN`).
- Mailpit (hộp thư thử, chỉ nhận email khi realm trỏ SMTP về `127.0.0.1:18340`; mặc định email đi Resend, mục Email): `ssh -L 18341:127.0.0.1:18341 thangnd@<VPS>` ⇒ `http://localhost:18341`.
- Postgres: `ssh -L 18330:127.0.0.1:18330 thangnd@<VPS>`.

## Kiểm tra sau deploy

- Actions: job `verify`, `deploy` xanh; bước smoke thấy issuer `https://auth.carenest.edu.vn/realms/carenest`.
- `curl -I https://auth.carenest.edu.vn/admin/` ⇒ 404.
- Sau bước FE: `curl -s https://thuonghong.carenest.edu.vn/api/auth/csrf` ⇒ 200 từ CareNest.
- Lỗi: `cd /data/thangnd/CAPSTONE/CareNest_BE && docker compose -f compose.vps.yaml logs --tail 100 api keycloak`.

## Rollback

**Tự động (`deploy/remote-deploy.sh`):** trước khi áp bản mới, script kiểm `.env` có đủ key của file mẫu, `docker compose config` đạt, chép file cũ vào `.rollback/`. Tag api hoặc Keycloak đổi thì dừng api, `pg_dump -Fc` DB ứng dụng (thêm DB Keycloak khi tag Keycloak đổi) vào `/data/thangnd/CAPSTONE/backups/<production|dev>/`, kiểm file đọc được, ghi `max(installed_rank)` của Flyway. Health không đạt trong 3 phút, hoặc smoke HTTPS fail ⇒ trả file cũ, nếu Flyway đã đổi thì khôi phục DB ứng dụng từ dump (trước đó dump trạng thái lỗi thành `*-failed-*.dump`), chạy lại image cũ. Job luôn đỏ; log ghi `ROLLED BACK` hoặc `ROLLBACK FAILED`. DB Keycloak **không** tự khôi phục (runbook 5.4). Giữ 5 dump mỗi loại; image liền trước được giữ trên VPS; tag đang chạy ghi ở `.deploy.env`. Script chỉ khôi phục DB đúng môi trường (dev chỉ `carenest_dev`, `keycloak_dev`).

Rollback chỉ có hiệu lực khi `.env` đủ key: key thêm vào `deploy/*.env.example` mà thiếu trên VPS làm deploy dừng ở bước kiểm trước, bản cũ vẫn chạy.

**Tay:** lệnh kiểm tra nhanh, restart, backup/khôi phục DB và đổi key khi có sự cố: `docs/system/INCIDENT_RUNBOOK.md`.

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

Ép build lại image api khi nội dung không đổi (vd. cập nhật base image): Run workflow trên `main` với `force_api` (chạy lại test + build + push cùng tag). Image Keycloak: xoá tag tương ứng trên Docker Hub rồi Run workflow. Run workflow với `force_api` trên nhánh khác chỉ chạy test (đo thời gian CI), không push.

Test api chạy song song 4 job (`api-test`), mỗi job 2 JVM; artifact `api-verify-reports-<n>` giữ log và thời gian từng class 7 ngày. PR từ nhánh trong repo push image sau khi test xanh ⇒ merge vào `dev` không chạy lại test nếu nội dung không đổi.

**Luật merge PR** (G94-440): mọi PR vào `main`/`dev` đều chạy workflow (PR chỉ sửa tài liệu bỏ test vì tag đã có, xong ~1 phút). Job `ci-result` tổng hợp `plan`, `api-test`, `api`, `keycloak`: job nào `failure`/`cancelled` ⇒ đỏ. Ruleset `protect-dev` bắt `ci-result` xanh và PR phải cập nhật theo `dev` ⇒ `dev` có commit mới thì bấm *Update branch*, chờ CI chạy lại rồi mới merge (2 PR xanh riêng vẫn có thể hỏng khi gộp, vd. 2 migration cùng số V154 ngày 19/11/2026). Job `plan` chặn sớm migration trùng version (`V154`/`V0154`, `V1_1`/`V1.1`) ⇒ đổi số migration của PR sang version lớn nhất + 1. Admin có quyền bypass ruleset; chỉ dùng khi khẩn cấp.

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
- Keycloak riêng vì seed `db/dev` gắn cứng ID user Keycloak (`user_accounts.keycloak_user_id`) mà ID user phải duy nhất trên cả instance ⇒ realm thứ hai cùng Keycloak không import được user của bộ dữ liệu dev.
- Postgres là của stack production: production dừng thì dev cũng dừng; `deploy-dev` báo lỗi rõ khi không thấy container Postgres.
- GitHub: environment `dev` (deployment branch `dev`); `VPS_*` là repository secret hoặc chép vào environment `dev`.

**Cài một lần:** DNS A `auth-dev` trỏ IP VPS. Tạo role/DB trên Postgres production (script init chỉ chạy khi volume trống), thu hồi `CONNECT` của `PUBLIC` để role dev không vào DB production:

```sql
CREATE ROLE carenest_dev LOGIN PASSWORD '<secret>';  CREATE DATABASE carenest_dev OWNER carenest_dev;
CREATE ROLE keycloak_dev LOGIN PASSWORD '<secret>';  CREATE DATABASE keycloak_dev OWNER keycloak_dev;
REVOKE CONNECT ON DATABASE carenest, keycloak, carenest_dev, keycloak_dev FROM PUBLIC;
```

Sau deploy dev đầu tiên: bật gateway như mục Cài một lần bước 6 nhưng tunnel `ssh -L 18322:127.0.0.1:18322`, `KEYCLOAK_URL=http://localhost:18322` và secret trong `.env` dev; rồi `CARENEST_AUTH_ENABLED=true` trong `.env` dev và `docker compose -f compose.dev.yaml up -d --no-build api` (kèm `API_IMAGE`, `KEYCLOAK_IMAGE` đang chạy).

Dev cho nhiều người test dùng chung tài khoản: `.env` dev đặt `CARENEST_AUTH_SINGLE_WEB_SESSION=false` (tắt giới hạn một phiên Web AUTH-28) rồi tạo lại `api`; production không đặt khóa này (mặc định `true`).

**Reset DB dev** (vd. sửa migration đã chạy trên dev làm Flyway báo checksum mismatch; chỉ mất dữ liệu dev): dừng `api` dev, `DROP DATABASE carenest_dev; CREATE DATABASE carenest_dev OWNER carenest_dev;` rồi chạy lại `deploy-dev`.

**Dựng lại bộ dữ liệu dev** (sau khi đổi `scripts/dev/gen-dev-dataset.mjs` và merge; làm 15/11/2026, xem `docs/knowledge/incidents/ENV-261115-dev-keycloak-password-policy.md`). Deploy **không bao giờ xóa DB**: seed `R__` chỉ thêm, không ghi đè dòng có sẵn; file user Keycloak chỉ import khi realm mới ⇒ phải tạo lại **cả DB ứng dụng lẫn DB Keycloak**, mất mọi dữ liệu nhập tay và user tạo tay. Người có quyền VPS chạy theo đúng thứ tự:

1. `.env` dev đặt `KEYCLOAK_DEV_USER_PASSWORD` **≥ 8 ký tự** (dev đang dùng `demo1234`). Realm có `passwordPolicy: length(8) and notUsername`; file `docker/keycloak/vps/carenest-users-0.json` nạp mật khẩu dạng thô nên bị kiểm ⇒ 7 ký tự (`demo123`) làm Keycloak không khởi động (`invalidPasswordMinLengthMessage`). Local dùng được `demo123` vì file dev lưu hash, không qua kiểm.
2. Backup rồi tạo lại 2 DB. Chạy trong `bash -c '…'` (đừng `set -e` thẳng ở shell đăng nhập: lỗi sẽ thoát luôn phiên SSH) và dừng container bằng `docker stop` (không `docker compose stop`: compose đòi `API_IMAGE`/`KEYCLOAK_IMAGE` chỉ CI truyền):

```bash
bash -c '
set -euo pipefail
PG=$(docker ps -qf name=carenest-be-postgres | head -1); SU=$(docker exec "$PG" printenv POSTGRES_USER); TS=$(date +%Y%m%d-%H%M%S)
B=/data/thangnd/CAPSTONE/backups/dev; mkdir -p $B
docker exec "$PG" pg_dump -U "$SU" -Fc carenest_dev > $B/carenest_dev-before-dataset-$TS.dump
docker exec "$PG" pg_dump -U "$SU" -Fc keycloak_dev > $B/keycloak_dev-before-dataset-$TS.dump
docker ps --format "{{.Names}}" | grep -E "^carenest-be-dev-(api|keycloak)" | xargs -r docker stop
docker exec "$PG" psql -U "$SU" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE carenest_dev WITH (FORCE)" -c "CREATE DATABASE carenest_dev OWNER carenest_dev" \
  -c "DROP DATABASE keycloak_dev WITH (FORCE)" -c "CREATE DATABASE keycloak_dev OWNER keycloak_dev"
echo XONG
'
```

3. Chạy lại job `deploy-dev` (Actions › Deploy VPS › run mới nhất trên `dev` › Re-run). Deploy lỗi thì **rollback khôi phục `carenest_dev` về bản rỗng rồi chạy image cũ**, image cũ nạp seed cũ ⇒ sửa nguyên nhân xong phải tạo lại `carenest_dev` lần nữa trước khi deploy lại (nếu không, dữ liệu 2 phiên bản seed trộn nhau: `ON CONFLICT DO NOTHING` giữ dòng cũ).
4. Realm mới chưa có cấu hình gateway ⇒ đăng nhập web trả 503 `1013`, log API `[authGatewayToken]|keycloakStatus=401`. Chạy `bootstrap.mjs` với **đúng** `KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET` của `.env` dev (script ghi secret này vào client gateway; lấy secret khác ⇒ vẫn 401). VPS không có Node ⇒ chép script lên rồi chạy trong container, biến lấy thẳng từ `.env` (không dán secret ra ngoài):

```bash
# Máy dev (PowerShell/Git Bash, trong repo BE):
scp docker/keycloak/auth/bootstrap.mjs thangnd@<VPS>:/tmp/carenest-bootstrap.mjs
# Trên VPS:
bash -c '
set -euo pipefail
cd /data/thangnd/CAPSTONE/CareNest_BE-dev; set -a; . ./.env; set +a
docker run --rm --network host -v /tmp/carenest-bootstrap.mjs:/app/bootstrap.mjs:ro \
  -e KEYCLOAK_URL=http://127.0.0.1:18322 -e KEYCLOAK_REALM=carenest \
  -e KC_BOOTSTRAP_ADMIN_USERNAME -e KC_BOOTSTRAP_ADMIN_PASSWORD -e KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET \
  node:22-alpine node /app/bootstrap.mjs --apply --enable
rm -f /tmp/carenest-bootstrap.mjs
'
```

5. Kiểm: kết quả bootstrap có `"applied": true`; `https://auth-dev.carenest.edu.vn/realms/carenest/.well-known/openid-configuration` 200; đăng nhập web `principal` / mật khẩu ở bước 1 ra đúng họ tên trong `docs/TAI_KHOAN_DEV.md` (vd. `teacher2` = Phạm Thị Thanh — sai người là dấu hiệu DB còn trộn seed cũ, làm lại bước 2–3). `kitchen` bị web chặn 403 là đúng (bếp chỉ dùng App).

Production: cùng quy trình với `carenest`/`keycloak`, `compose.vps.yaml`, thư mục `/data/thangnd/CAPSTONE/CareNest_BE`, chủ DB `carenest`/`keycloak` — xóa toàn bộ dữ liệu prod, phải được duyệt riêng.

## SMS — cổng giả và textbee

Mặc định `CARENEST_SMS_PROVIDER=fake`: không gửi thật, chỉ log 2 số cuối SĐT. `textbee` gọi textbee.dev (`TextbeeSmsGateway`): điện thoại Android cài app textbee gửi tin bằng SIM của nó, **chỉ dùng dev/demo** (AUTH-18, plan `docs/plans/active/2026-11-10-twilio-sms-otp.md`). Bật nhà cung cấp thật ⇒ tài khoản phụ huynh mới nhận mã kích hoạt; tin có mật khẩu tạm (tạo tài khoản, đổi SĐT) luôn đi cổng giả.

Bật textbee ở máy dev (`compose.yaml` hoặc `mvnw`):
1. Đăng ký tại textbee.dev, cài app textbee trên điện thoại Android có SIM, đăng nhập và cấp quyền gửi SMS.
2. Dashboard textbee: tạo **API key**; lấy **Device ID** nếu tài khoản có nhiều máy (trống ⇒ textbee chọn máy mặc định).
3. `.env`: `CARENEST_SMS_PROVIDER=textbee`, `TEXTBEE_API_KEY`, tùy chọn `TEXTBEE_DEVICE_ID`. Thiếu API key ⇒ API dừng lúc khởi động, báo tên biến thiếu.
4. Khi demo: điện thoại bật app, có mạng. API trả 200 nghĩa là textbee đã nhận vào hàng đợi, chưa phải tin đã tới máy; xem trạng thái trên dashboard textbee. Cước SMS tính theo SIM.

Không commit `.env`; lộ API key ⇒ thu hồi trên dashboard textbee. `compose.vps.yaml`, `compose.dev.yaml` chưa truyền biến `TEXTBEE_*` nên VPS luôn dùng cổng giả. Production chọn nhà cung cấp có brandname (D-2).

Không dùng Twilio trial (thử 13–14/11/2026): trial chỉ gửi tới số Mỹ đã xác minh và chỉ 10 template có sẵn — số `+84` bị từ chối (lỗi `21612`), nội dung tự viết bị từ chối (lỗi `572006`).

## Email — Resend SMTP

Hai nơi gửi email, dùng chung một bộ biến `CARENEST_EMAIL_SMTP_*` + `CARENEST_EMAIL_FROM` trong `.env`:
- Keycloak (OTP, đặt mật khẩu) qua realm `smtpServer` — Keycloak chỉ gửi được qua SMTP.
- BE qua `integration/email/EmailGateway`. Mặc định `CARENEST_EMAIL_PROVIDER=log`: không gửi, chỉ log số người nhận; `smtp` ⇒ `SmtpEmailGateway`.

Cả `compose.yaml`, `compose.vps.yaml`, `compose.dev.yaml` mặc định `smtp.resend.com:587` STARTTLS, username `resend`. Dùng SMTP thay vì API HTTP của Resend vì Keycloak bắt buộc SMTP, một cấu hình cho cả hai nơi; khi BE cần idempotency key/webhook trạng thái gửi mới thêm gateway HTTP.

Bật Resend:
1. resend.com → **Domains** → thêm domain gửi, khai các bản ghi DNS (SPF/DKIM) Resend đưa ra, chờ trạng thái *Verified*. Chưa xác minh ⇒ chỉ gửi được từ `onboarding@resend.dev` tới email chủ tài khoản Resend (người khác nhận lỗi 403).
2. **API Keys** → tạo key quyền *Sending access* (giới hạn đúng domain) — key `re_...` chỉ hiện một lần.
3. `.env`: `CARENEST_EMAIL_SMTP_USERNAME=resend`, `CARENEST_EMAIL_SMTP_PASSWORD=<API key>`, `CARENEST_EMAIL_FROM=no-reply@<domain đã xác minh>` (bắt buộc: trống thì From = `resend`, Resend từ chối), tùy chọn `CARENEST_EMAIL_FROM_NAME`. BE gửi thật thì thêm `CARENEST_EMAIL_PROVIDER=smtp` (thiếu biến ⇒ API dừng lúc khởi động, báo tên biến thiếu).
4. Mạng/VPS chặn cổng 587 (log `MailConnectException`/timeout) ⇒ `CARENEST_EMAIL_SMTP_PORT=2587`, vẫn STARTTLS.
5. Realm đã import từ trước (`--import-realm` bỏ qua realm có sẵn) ⇒ biến mới không vào Keycloak. Sửa ở Admin Console (VPS: tunnel 18320 ở trên) → realm `carenest` → Realm settings → Email: From = `CARENEST_EMAIL_FROM`, host `smtp.resend.com`, port `587` (hoặc `2587`), Enable StartTLS, Authentication bật, Username `resend`, Password = API key → **Test connection**; hoặc reset DB Keycloak (`KEYCLOAK.md`).
6. Kiểm tra: tạo tài khoản có email (Keycloak gửi đặt mật khẩu, `setupEmailSent=true`) và xem Resend → **Emails** có bản ghi *Delivered*.

Gói free Resend ~100 email/ngày, 3.000/tháng — đủ demo, production cần nâng gói. Lộ API key ⇒ xóa key trên Resend, tạo key mới, cập nhật `.env` và realm. Mailpit trên VPS giữ lại làm hộp thư thử: muốn xem email mà không gửi thật thì đổi realm Email về host `127.0.0.1`, port `18340`, tắt StartTLS/Authentication.

Gọi từ service (gửi đồng bộ; email không phải bước bắt buộc ⇒ bắt `EmailDeliveryException`, không làm hỏng nghiệp vụ đã lưu):

```java
emailGateway.send(EmailMessage.html(guardianEmail, "Thông báo từ CareNest", html));
```

Thiết kế email đồng bộ: HTML dựng qua `CareNestEmailLayout` (cùng màu, header, nút, footer với `carenest-layout.ftl` của Keycloak; `CareNestEmailLayoutTest` kiểm token chung), không tự viết khung HTML riêng.

Email thông báo kế hoạch giáo dục (G94-386): không gửi đồng bộ — `NotificationService` xếp hàng `notification_email_deliveries` cùng giao dịch, `EmailOutboxWorker` gửi sau commit (cấu hình `carenest.email.outbox.*`). Nút "Mở hồ sơ" = `CARENEST_WEB_BASE_URL` + đường dẫn thông báo. Kiểm tra: `SELECT state, last_error_kind, count(*) FROM notification_email_deliveries GROUP BY 1, 2;` (`SKIPPED/NO_EMAIL` = người nhận chưa có email).

## Chuyển sang dữ liệu thật (Phase 3 — chưa làm)

Điều kiện: review bảo mật gateway ADR-0013 xong; backup Postgres theo lịch đã thử khôi phục; người quản lý server đồng ý; SMTP thật. Xoá volume là **mất toàn bộ dữ liệu** của DB `carenest` và `keycloak`:

```bash
docker compose -f compose.vps.yaml down -v
# .env: SPRING_PROFILES_ACTIVE=  (rỗng) · SMTP thật; compose.vps.yaml: bỏ mount docker/keycloak/vps/carenest-users-0.json
docker compose -f compose.vps.yaml up -d --no-build   # kèm API_IMAGE/KEYCLOAK_IMAGE hiện tại
```

Rồi tạo tài khoản quản trị theo `docs/system/KEYCLOAK.md` mục 6.
