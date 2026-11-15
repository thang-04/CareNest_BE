# Hướng dẫn xử lý sự cố — VPS CareNest

Dùng khi deploy lỗi hoặc site không vào được. CI deploy qua `deploy/remote-deploy.sh`: tự dump DB trước khi áp bản mới và tự rollback khi health/smoke fail (`docs/system/DEPLOYMENT.md` mục Rollback). Runbook này dùng khi rollback tự động báo `ROLLBACK FAILED`, hoặc sự cố xảy ra sau deploy (plan: `docs/plans/active/2026-11-12-ci-verify-speed-and-deploy-rollback.md`). Kiến trúc và cổng: `docs/system/DEPLOYMENT.md`.

> Các lệnh dưới suy ra từ `compose.vps.yaml`, `compose.dev.yaml`, `.github/workflows/deploy.yml`; **chưa chạy thử trên VPS**. Tên container giả định theo mặc định của compose (`<project>-<service>-1`); chạy mục 1 trước để xác nhận tên thật.

## 0. Thông tin chung

| | Production (`main`) | Dev (`dev`) |
| --- | --- | --- |
| Thư mục BE | `/data/thangnd/CAPSTONE/CareNest_BE` | `/data/thangnd/CAPSTONE/CareNest_BE-dev` |
| File compose BE | `compose.vps.yaml` | `compose.dev.yaml` |
| Container api | `carenest-be-api-1` | `carenest-be-dev-api-1` |
| Container Keycloak | `carenest-be-keycloak-1` | `carenest-be-dev-keycloak-1` |
| Postgres (dùng chung) | `carenest-be-postgres-1` | cùng container, DB `carenest_dev` |
| Health api | `127.0.0.1:18310/actuator/health` | `127.0.0.1:18311/actuator/health` |
| Health Keycloak | `127.0.0.1:18321/health/ready` | `127.0.0.1:18323/health/ready` |
| FE | `carenest-web`, cổng 18300 | `carenest-web-dev`, cổng 18301 |

Đăng nhập: `ssh -p <VPS_PORT> thangnd@<VPS_HOST>`.

Mọi lệnh `docker compose -f compose.vps.yaml …` cần 2 biến image, thiếu là compose từ chối chạy. Sau deploy thành công, tag đang chạy nằm trong `.deploy.env` (`set -a; . ./.deploy.env; set +a`). Hoặc lấy từ container đang có (kể cả container đang crash):

```bash
cd /data/thangnd/CAPSTONE/CareNest_BE
export API_IMAGE=$(docker inspect -f '{{.Config.Image}}' carenest-be-api-1)
export KEYCLOAK_IMAGE=$(docker inspect -f '{{.Config.Image}}' carenest-be-keycloak-1)
echo "$API_IMAGE $KEYCLOAK_IMAGE"
```

Dev: đổi thư mục thành `CareNest_BE-dev`, tên container thành `carenest-be-dev-api-1`, `carenest-be-dev-keycloak-1`, file thành `compose.dev.yaml`.

## 1. Kiểm tra nhanh (2 phút)

```bash
# Container nào đang chạy, đang Restarting, dùng image nào
docker ps -a --filter name=carenest --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'

# Sức khỏe từng service (production; dev đổi cổng theo bảng trên)
curl -fsS http://127.0.0.1:18310/actuator/health; echo
curl -fsS -o /dev/null -w 'keycloak %{http_code}\n' http://127.0.0.1:18321/health/ready
docker exec carenest-be-postgres-1 pg_isready -h 127.0.0.1
curl -fsS -o /dev/null -w 'web %{http_code}\n' http://127.0.0.1:18300/

# Từ ngoài vào (qua Caddy)
curl -sI https://web.carenest.edu.vn/ | head -1
curl -s https://web.carenest.edu.vn/api/auth/csrf | head -c 200; echo
curl -s https://auth.carenest.edu.vn/realms/carenest/.well-known/openid-configuration | head -c 120; echo

# Log 100 dòng cuối
docker logs --tail 100 carenest-be-api-1
docker logs --tail 100 carenest-be-keycloak-1

# Đĩa đầy là nguyên nhân hay gặp của Postgres và Docker
df -h /data /var/lib/docker
systemctl status caddy --no-pager | head -5
```

## 2. Triệu chứng ⇒ xử lý

| Triệu chứng | Nguyên nhân thường gặp | Làm gì |
| --- | --- | --- |
| api `Restarting`, log có `FlywayMigrateException` hoặc `Validate failed` | Migration hoặc seed lỗi trên dữ liệu đang có | Mục 5.3. Không restart lặp lại, không tự sửa bảng `flyway_schema_history` |
| api `Restarting`, log có `required` / `Could not resolve placeholder` / tên biến | Thiếu key trong `.env` | Mục 6 |
| api chạy nhưng 401 mọi request | Issuer hoặc secret Keycloak lệch | So `KEYCLOAK_ISSUER_URI` trong compose với kết quả lệnh curl issuer ở mục 1 |
| Keycloak không `ready` | Postgres chưa lên, hoặc Keycloak đang tự build lại (1–2 phút) | Chờ 2 phút, xem log; Postgres lỗi thì xử lý Postgres trước |
| Postgres không lên | Đĩa đầy, volume hỏng | `df -h`; `docker logs --tail 50 carenest-be-postgres-1`. **Không** chạy `down -v` (xóa toàn bộ dữ liệu) |
| Site ngoài lỗi 502 nhưng health trong VPS tốt | Caddy | `sudo systemctl reload caddy`; `journalctl -u caddy -n 50 --no-pager` |
| Web trắng trang sau deploy FE | Bản build lỗi | Mục 7 |

## 3. Restart

```bash
# Khởi động lại nguyên trạng (không đọc lại .env, không đổi image) — nhanh nhất
docker restart carenest-be-api-1

# Tạo lại container theo compose và .env hiện tại (dùng sau khi sửa .env); cần 2 biến ở mục 0
docker compose -f compose.vps.yaml up -d --no-build api
```

`up -d` chỉ tạo lại service có cấu hình thay đổi; Postgres và Keycloak không bị đụng nếu biến của chúng không đổi. Không bước nào build lại image.

## 4. Quay về bản BE trước

**Điều kiện:** giữa hai bản không có migration Flyway mới. Có migration mới thì bản cũ có thể không chạy được trên schema mới ⇒ xem mục 5.

```bash
cd /data/thangnd/CAPSTONE/CareNest_BE
# 1. Bản đang lỗi và các bản còn trên máy (bản mới nhất ở trên cùng)
docker ps -a --filter name=carenest-be --format '{{.Names}}\t{{.Image}}'
docker images thangnd04/carenest-be --format '{{.CreatedAt}}\t{{.Tag}}' | sort -r

# 2. Chọn tag api-… và keycloak-… của bản trước rồi chạy lại
export API_IMAGE=thangnd04/carenest-be:api-<hash-cũ>
export KEYCLOAK_IMAGE=thangnd04/carenest-be:keycloak-<hash-cũ>   # không đổi thì giữ tag đang chạy
docker compose -f compose.vps.yaml up -d --no-build

# 3. Kiểm tra
curl -fsS http://127.0.0.1:18310/actuator/health; echo
```

- Image bản liền trước luôn còn trên VPS (CI không dọn). Cũ hơn nữa thì tính tag từ commit cũ theo `docs/system/DEPLOYMENT.md` mục Rollback rồi `docker pull`.
- File compose/realm bản trước nằm trong `.rollback/files.tar` (`tar -xf .rollback/files.tar`); thiếu thì lấy từ git (`git show <sha-cũ>:compose.vps.yaml`). `.rollback/state.env` ghi image cũ/mới và dump của lần deploy gần nhất.
- Sau khi ổn: revert commit lỗi trên nhánh, nếu không lần deploy kế tiếp lại đưa bản lỗi lên.

## 5. Database

### 5.1 Xem lịch sử migration

```bash
docker exec carenest-be-postgres-1 psql -U carenest -d carenest -c \
  "select installed_rank, version, description, success, installed_on from flyway_schema_history order by installed_rank desc limit 10"
```
Dev: `-d carenest_dev`. User `carenest` là giá trị mặc định của `POSTGRES_USER`; `.env` đặt khác thì đổi theo.

### 5.2 Backup tay (chạy trước mọi thao tác rủi ro)

```bash
mkdir -p /data/thangnd/CAPSTONE/backups
docker exec carenest-be-postgres-1 pg_dump -U carenest -Fc carenest \
  > /data/thangnd/CAPSTONE/backups/carenest-$(date +%Y%m%d-%H%M).dump
ls -lh /data/thangnd/CAPSTONE/backups | tail -3
```
Dev: thay `carenest` cuối lệnh bằng `carenest_dev`. Keycloak: DB `keycloak` (dev: `keycloak_dev`). File dump chứa dữ liệu trẻ và tài khoản: không chép ra khỏi VPS, `chmod 600`.

### 5.3 Migration hoặc seed lỗi lúc deploy

1. Đọc log api, ghi lại tên file migration và dòng lỗi.
2. Chạy 5.1. Các migration version chạy **trước** file lỗi đã được ghi vào DB và không tự hoàn lại.
3. Chọn một hướng:
   - **Sửa tiến (ưu tiên):** sửa migration/seed lỗi bằng commit mới, merge, CI deploy lại. Trong lúc chờ, site ngừng.
   - **Có file dump trước deploy:** khôi phục theo 5.4 rồi quay về image cũ theo mục 4.
   - **Chỉ dev, chấp nhận mất dữ liệu dev:** reset DB dev theo `docs/system/DEPLOYMENT.md` mục "Reset DB dev".
4. CI tạo dump trước mỗi deploy đổi tag: `/data/thangnd/CAPSTONE/backups/<production|dev>/<env>-app-<thời-điểm>.dump` (và `-kc-` khi Keycloak đổi). Rollback tự động khôi phục từ file này; nó fail thì dùng 5.4 với file đó. File `-failed-` là trạng thái DB ngay trước khi khôi phục.

### 5.4 Khôi phục từ file dump

Mất mọi dữ liệu ghi sau thời điểm dump. Backup trạng thái hiện tại theo 5.2 trước khi làm.

```bash
F=/data/thangnd/CAPSTONE/backups/<file>.dump
docker stop carenest-be-api-1                      # dev: carenest-be-dev-api-1

# Production
docker exec carenest-be-postgres-1 psql -U carenest -d postgres \
  -c 'DROP DATABASE carenest WITH (FORCE)' -c 'CREATE DATABASE carenest OWNER carenest' \
  -c 'REVOKE CONNECT ON DATABASE carenest FROM PUBLIC'
docker exec -i carenest-be-postgres-1 pg_restore -U carenest -d carenest --no-owner < "$F"

# Dev
docker exec carenest-be-postgres-1 psql -U carenest -d postgres \
  -c 'DROP DATABASE carenest_dev WITH (FORCE)' -c 'CREATE DATABASE carenest_dev OWNER carenest_dev' \
  -c 'REVOKE CONNECT ON DATABASE carenest_dev FROM PUBLIC'
docker exec -i carenest-be-postgres-1 pg_restore -U carenest -d carenest_dev --no-owner --role=carenest_dev < "$F"
```
Sau đó khởi động image cũ theo mục 4 và chạy lại 5.1 để xác nhận version.

## 6. Thêm hoặc đổi key cấu hình

Giá trị thật chỉ nằm trong `.env` trên VPS (đặt tay, `chmod 600`). CI không ghi vào file này.

**Thêm key mới** — phải làm đủ, và làm bước 3 **trước khi merge**:
1. `compose.vps.yaml` và `compose.dev.yaml`: thêm dòng trong `environment` của service dùng key (compose chỉ truyền biến được liệt kê; key có trong `.env` nhưng không liệt kê thì container không thấy).
2. `deploy/vps.env.example`, `deploy/dev.env.example`: thêm tên key, không ghi giá trị thật.
3. Trên VPS: thêm key vào `.env` của production và dev.
4. Merge. Tên key có trong file mẫu mà thiếu trong `.env` trên VPS ⇒ `remote-deploy.sh` dừng ở bước kiểm trước, in tên key thiếu, job đỏ, container cũ không bị đụng. Key chỉ có trong compose mà không có trong file mẫu thì không được kiểm: nhớ làm bước 2.

**Đổi giá trị (rotate)** — không build lại image, chỉ tạo lại service bị ảnh hưởng:
```bash
cd /data/thangnd/CAPSTONE/CareNest_BE && nano .env        # sửa giá trị
# đặt API_IMAGE, KEYCLOAK_IMAGE theo mục 0
docker compose -f compose.vps.yaml up -d --no-build        # chỉ service có biến thay đổi bị tạo lại
```
`docker restart` không đọc lại `.env`.

Key không đổi được chỉ bằng `.env`:

| Key | Vì sao | Cách đổi |
| --- | --- | --- |
| `POSTGRES_PASSWORD`, `KEYCLOAK_DB_PASSWORD` | Chỉ áp lúc tạo volume lần đầu | `ALTER ROLE <role> PASSWORD '…'` trong Postgres, rồi sửa `.env`, rồi `up -d` |
| `KEYCLOAK_ADMIN_CLIENT_SECRET`, `KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET` | Realm chỉ import lần đầu | Đổi secret client trong admin console (tunnel), rồi sửa `.env`, rồi `up -d api` |
| `KEYCLOAK_DEV_USER_PASSWORD` | Chỉ áp lần import đầu | Đổi mật khẩu từng user trong admin console |
| `CARENEST_AUTH_ENCRYPTION_KEY`, `KEYCLOAK_AUTH_RECOVERY_ASSERTION_SECRET` | Dùng để mã hóa/ký dữ liệu phiên | Chưa kiểm chứng tác động; coi như mọi phiên đăng nhập hiện có mất hiệu lực. Hỏi người phụ trách auth trước khi đổi |
| `VITE_*` (FE) | Nhúng vào bundle lúc build | GitHub › Variables, rồi chạy lại workflow FE (bắt buộc build lại) |
| `DOCKERHUB_TOKEN`, `VPS_SSH_KEY` | Secret của GitHub Actions | GitHub › Settings › Secrets; token đọc trên VPS: `docker login -u thangnd04` |

## 7. Frontend

```bash
docker ps -a --filter name=carenest-web --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'
docker logs --tail 50 carenest-web
docker restart carenest-web
```
Hiện tại **không quay về bản trước ngay được**: CI dọn image cũ và ghi đè source trên VPS trong mỗi lần deploy. Cách duy nhất: revert commit trên nhánh rồi để CI build lại.

## 8. Sau sự cố

Ghi lại theo `.ai/workflows/update-knowledge.md` (file trong `docs/knowledge/incidents/`, thêm dòng vào `ISSUE_INDEX.md`): triệu chứng, nguyên nhân, lệnh đã chạy, cách phòng ngừa.
