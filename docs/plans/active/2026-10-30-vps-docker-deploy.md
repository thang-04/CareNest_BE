---
title: Deploy BE + Keycloak lên VPS qua Docker Hub
status: in-progress
owner: thangnd
jira: G94-255
branch: feature/G94-60-BE-FEAT-01-authentication
modules: []
rules: []
risk: high
created: 2026-10-30
updated: 2026-10-30
---
# Deploy BE + Keycloak lên VPS qua Docker Hub

## Context
BE chưa deploy (`docs/context/CURRENT_STATE.md:22`, `docs/system/DEPLOYMENT.md:3` trống). FE đã chạy trên VPS `k8selchat` (CI build image Docker Hub `thangnd04/carenest-web`, mạng host, Caddy host — `FE:docs/integration/DEPLOYMENT.md`). `compose.yaml` hiện chỉ cho dev: gọi nhau theo tên service qua bridge (`compose.yaml:39,90,95`), publish `0.0.0.0` (`:11,51,87`), Keycloak `start-dev` (`:22`), client dev bật (`:44`). VPS mất chain iptables Docker ⇒ không có bridge (`FE:docs/knowledge/TROUBLESHOOTING.md`).

VPS kiểm 2026-10-30 (user chạy): RAM 21Gi available, 4 CPU; `*:8080` và `127.0.0.1:18080` đã bị `java` khác chiếm; dải `:183xx` chỉ có `127.0.0.1:18300` (FE). `dig auth.carenest.edu.vn` rỗng. Hệ quả: Caddy FE đang proxy `/api/*` sang `127.0.0.1:8080` = app người khác (`FE:deploy/caddy/carenest-web.caddy`).

Gateway auth: Keycloak cần extension (`docker/keycloak/auth/Dockerfile:1-3`), realm dẫn xuất từ `prepare.mjs` (thư mục `generated` bị ignore), cấu hình client/flow bằng `bootstrap.mjs` (preview rồi `--apply`) — `docker/keycloak/auth/README.md`. README ghi triển khai thật cần review bảo mật (`docker/keycloak/auth/README.md:3`).

## Quyết định đã chốt
1. CI build `api` + Keycloak có extension, push **1 repo private** `thangnd04/carenest-be`, tag `api-<sha>` / `keycloak-<sha>`; VPS `docker login` token Read-only, pull, `up --no-build` — user, 2026-10-30.
2. Dữ liệu: giai đoạn đầu chạy seed dev (`SPRING_PROFILES_ACTIVE=dev`, user Keycloak dev); sau xoá và dùng dữ liệu thật — user, 2026-10-30.
3. Email: Mailpit trên VPS (bind loopback, xem qua SSH tunnel) trước, SMTP thật sau (chỉ đổi `.env`) — user, 2026-10-30.
4. FE đăng nhập qua gateway API (`compose.auth.yaml`, ADR-0013); Mobile dùng redirect qua `auth.carenest.edu.vn` — user, 2026-10-30.
5. Mạng host, mọi service bind `127.0.0.1`; cổng: api 18310, Keycloak 18320 (management 18321), Postgres 18330, Mailpit SMTP 18340 / UI 18341 — user duyệt thiết kế, 2026-10-30.
6. Keycloak admin console không mở ra Internet (site Caddy chặn `/admin`), chỉ qua SSH tunnel — user duyệt, 2026-10-30.
7. Deploy khi push `dev` + chạy tay, giống FE — user duyệt thiết kế, 2026-10-30.
8. Bật gateway auth trên VPS cho demo (chỉ dữ liệu seed) trước review bảo mật ADR-0013; review bảo mật là điều kiện bắt buộc trước Phase 3 — user, 2026-10-30.

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ, API contract hay migration; chỉ hạ tầng. Dữ liệu seed là dữ liệu giả (`db/dev`). Chuyển sang dữ liệu thật (trẻ em, sức khỏe — nguyên tắc 6) là bước riêng, có điều kiện ở Phase 3.

### Câu hỏi mở
- [x] Bật gateway auth trên VPS trước review bảo mật ADR-0013? ⇒ bật cho demo, review trước Phase 3 (quyết định 8).
- [x] Mã Jira / branch ⇒ `G94-255`, dùng branch hiện tại `feature/G94-60-BE-FEAT-01-authentication` (user, 2026-10-30).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Image BE ở đâu | 1 repo private, 2 tag | user | 2026-10-30 | plan |
| Dữ liệu VPS | seed trước, dữ liệu thật sau | user | 2026-10-30 | plan |
| Email | Mailpit trước | user | 2026-10-30 | plan |
| Auth FE | gateway | user | 2026-10-30 | plan |
| Gateway trước review bảo mật | bật cho demo; review trước Phase 3 | user | 2026-10-30 | plan |

## Acceptance criteria
- **AC-1:** Given push `dev`, When workflow chạy, Then verify xanh và Docker Hub private có `api-<sha>`, `keycloak-<sha>`; verify fail ⇒ không push, không deploy.
- **AC-2:** Given stack chạy, When `sudo ss -ltnp | grep -E ':(183[1-4][0-9]|7800|9000)\b'`, Then chỉ thấy `127.0.0.1`.
- **AC-3:** Given DNS + Caddy, When GET `https://auth.carenest.edu.vn/realms/carenest/.well-known/openid-configuration`, Then 200 và `issuer` = `https://auth.carenest.edu.vn/realms/carenest`.
- **AC-4:** Given Caddy FE đã trỏ 18310, When GET `https://web.carenest.edu.vn/api/auth/csrf`, Then 200 từ CareNest (không phải app cổng 8080).
- **AC-5:** Given bootstrap gateway đã apply, When đăng nhập user seed qua `/api/auth/*`, Then thành công; email OTP/setup xuất hiện trong Mailpit.
- **AC-6:** When GET `https://auth.carenest.edu.vn/admin/`, Then không vào được admin console (403/404).
- **AC-7:** Given API không healthy sau deploy, Then CI fail và in `docker compose logs --tail`.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Image Keycloak | Build trong CI, cùng repo private khác tag | Image chính thức + rsync jar | Tái lập, rollback theo tag, 1 slot private |
| Nơi build | GitHub runner | VPS tự build | VPS lỗi mạng khi build; khớp FE |
| Compose VPS | File riêng `compose.vps.yaml` | Overlay lên `compose.yaml` | Mạng host thay hết địa chỉ/cổng; overlay không xoá được `ports`/`depends_on` bridge gọn |
| Secret | `.env` đặt tay trên VPS (600) | GitHub Secrets đẩy qua SSH | Secret runtime không đi qua CI log/runner |
| Bootstrap gateway | Chạy tay 1 lần từ máy dev qua SSH tunnel | Chạy trong CI | Cần admin Keycloak + duyệt preview; không đưa admin secret vào CI |

## Phases
### Phase 1 — File deploy trong repo (local)
- Files: C `compose.vps.yaml` · C `.env.vps.example` · C `.dockerignore` · C `.github/workflows/deploy.yml` · C `deploy/caddy/carenest-auth.caddy` · M `docs/system/DEPLOYMENT.md` · M `docs/context/CURRENT_STATE.md`
- Steps:
  1. `compose.vps.yaml`: `network_mode: host`; postgres `PGPORT=18330` + `listen_addresses=127.0.0.1`, mount `docker/postgres/init`; keycloak `start --import-realm`, `KC_HTTP_HOST=127.0.0.1`, `KC_HTTP_PORT=18320`, `KC_HTTP_MANAGEMENT_PORT=18321`, `KC_CACHE=local`, `KC_HOSTNAME=https://auth.carenest.edu.vn`, `KC_PROXY_HEADERS=xforwarded`, `CARENEST_DEV_CLIENT_ENABLED=false`, SMTP `127.0.0.1:18340`, mount realm generated + user dev; mailpit `MP_SMTP_BIND_ADDR`/`MP_UI_BIND_ADDR` loopback; api `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=18310`, DB/Keycloak qua `127.0.0.1`, biến gateway như `compose.auth.yaml`. Image qua `${API_IMAGE}`/`${KEYCLOAK_IMAGE}`.
  2. `.env.vps.example`: chỉ tên biến + mô tả, không giá trị secret.
  3. `.dockerignore`: loại `.env*`, `target` (trừ jar cần), `.git`, `.idea`, `docs`.
  4. Workflow: job verify (Java 21, `node scripts/verify.mjs`, extension `mvnw -f docker/keycloak/extensions/carenest-auth/pom.xml verify`) ⇒ build jar extension + `prepare.mjs` ⇒ build/push 2 image ⇒ rsync `compose.vps.yaml`, realm generated, user dev, `docker/postgres/init` ⇒ SSH: pull, `up -d --no-build`, chờ health API `127.0.0.1:18310/actuator/health` + Keycloak `127.0.0.1:18321/health/ready`, lỗi ⇒ logs + fail ⇒ áp Caddy như FE ⇒ smoke HTTPS.
  5. Caddy `auth.carenest.edu.vn` ⇒ `127.0.0.1:18320`, chặn `/admin*`.
  6. `DEPLOYMENT.md`: luồng, bảng cổng, cài một lần, secrets, rollback, thủ tục chuyển dữ liệu thật.
- Tests: AC-1/7 → `docker compose -f compose.vps.yaml --env-file .env.vps.example config --quiet`; YAML workflow parse; `bash -n` script SSH; `node scripts/check-ai-layer.mjs`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0. **Dừng chờ user review.**

### Phase 2 — Cài VPS + deploy đầu (user chạy, mình hướng dẫn)
- Steps: DNS A `auth`; repo Docker Hub Private; GitHub Secrets (`DOCKERHUB_TOKEN`, `VPS_*`); VPS `docker login` Read-only, `.env` 600; Caddy chấp nhận file `carenest-auth.caddy`; push `dev`; bootstrap gateway qua tunnel (preview ⇒ user duyệt ⇒ `--apply --enable`, quyết định 8).
- Repo FE (bước riêng, plan FE): Caddy `/api/*` ⇒ 18310; build arg `VITE_AUTH_MODE`; `VITE_USE_MOCK=false`.
- Tests: AC-2..AC-6 bằng lệnh trên VPS/`curl`.
- **Dừng chờ user review.**

### Phase 3 — Chuyển dữ liệu thật (sau, cần duyệt riêng)
- Điều kiện: review bảo mật gateway ADR-0013 xong, finding chặn đã xử lý (quyết định 8); backup Postgres theo lịch đã chạy thử khôi phục; người quản lý server đồng ý; SMTP thật.
- Steps: dừng stack ⇒ xoá volume ⇒ `SPRING_PROFILES_ACTIVE` rỗng, bỏ mount user dev ⇒ `up` ⇒ tạo admin.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-7 | — | Run GitHub Actions | 1–2 | |
| AC-2 | — | `ss -ltnp` VPS | 2 | |
| AC-3, AC-4, AC-6 | — | `curl` HTTPS | 2 | |
| AC-5 | — | Đăng nhập thử + Mailpit UI | 2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Push đầu tạo repo public | Lộ jar BE | Tạo repo Private trước push đầu | Đổi Private, xoá tag |
| Service bind `0.0.0.0` trên mạng host | Lộ Postgres/Keycloak ra Internet (firewall tắt) | Bind loopback, `KC_CACHE=local`, AC-2 mỗi deploy | `down` service đó |
| Migration lỗi | API không lên | CI health fail + logs | Sửa bằng migration mới; không rollback DB |
| Rollback image qua migration | Bản cũ chạy trên schema mới | Chỉ rollback khi không có migration giữa 2 bản | Deploy tag mới |
| Gateway bật trước review | Lỗ hổng auth trên Internet | Chỉ dữ liệu seed; review chặn Phase 3 (quyết định 8) | Tắt `CARENEST_AUTH_ENABLED`, disable client |
| Caddy FE còn trỏ 8080 | `/api` vào app người khác | Đổi trong Phase 2 trước khi `VITE_USE_MOCK=false` | Revert file Caddy FE |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Không đổi contract API (chỉ hạ tầng); FE bị ảnh hưởng ở Caddy + build arg — liệt kê Phase 2
- [x] Mỗi AC có cách kiểm; mỗi phase có Exit/điểm dừng
- [x] 7 file Phase 1, 3 phase — không cần tách
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review: không placeholder ngoài trường jira/branch user để trống

## Progress log
### 2026-10-30 — Draft
- Commit: chưa commit
- Verify: chưa sửa code
- Tiếp: user trả lời 2 câu hỏi mở ⇒ duyệt plan ⇒ tạo branch ⇒ Phase 1.
- 2026-10-30: câu hỏi 1 chốt (bật gateway cho demo, review trước Phase 3). Còn: mã Jira/branch.
- 2026-10-30: user duyệt plan, Jira `G94-255`. Còn mã công việc ⇒ tên branch; có branch ⇒ `approved`.
- 2026-10-30: user chọn dùng branch hiện tại `feature/G94-60-BE-FEAT-01-authentication` ⇒ `approved` ⇒ bắt đầu Phase 1 (`in-progress`).

### 2026-10-30 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 333 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; `node scripts/check-ai-layer.mjs` → 0 lỗi; `docker compose -f compose.vps.yaml --env-file deploy/vps.env.example config` OK (thiếu `API_IMAGE`/`KEYCLOAK_IMAGE` ⇒ báo lỗi rõ; `SPRING_PROFILES_ACTIVE=` giữ rỗng); workflow parse YAML OK; 2 script SSH `bash -n` OK. Chưa chạy: actionlint, `caddy validate`, build 2 image (chạy ở CI Phase 2).
- Khác plan: mẫu env đặt ở `deploy/vps.env.example` (hook repo chặn tên `.env.*`); thêm cập nhật `docs/INDEX.md`, `.ai/CONTEXT_MAP.yaml`, `.ai/workflows/deploy.md` (DEPLOYMENT hết SKELETON); Caddy chặn thêm `/realms/master*`; `KC_HOSTNAME_ADMIN=http://localhost:18320` để admin console chạy qua tunnel; `CARENEST_AUTH_ENABLED` mặc định `false`, bật trong `.env` sau `bootstrap.mjs --apply --enable`; smoke CI chỉ kiểm issuer Keycloak (AC-4 cần FE đổi Caddy trước).
- Tiếp: user review Phase 1 ⇒ Phase 2 (DNS, Docker Hub Private, secrets, `.env` VPS, push `dev`).
- 2026-10-30 (user cho phép sửa FE): phần FE của Phase 2 đã sửa sẵn, chưa commit — `FE:deploy/caddy/carenest-web.caddy` `/api/*` ⇒ 18310; build arg `VITE_AUTH_MODE` (mặc định `mock`) trong `Dockerfile`, `compose.yaml`, workflow FE. Còn: bật Variables FE sau khi gateway BE bật.

### 2026-10-30 — Phase 2 (chờ merge PR vào `dev`)
- Commit: `a728906` (G94-255, Phase 1), `adb049a` (merge `origin/dev`; migration TEAM_LEADER V10 ⇒ V38 vì trùng V10 `dev`, user duyệt); đã push nhánh, PR vào `dev` user tạo.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 570 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok` (cây sau merge).
- User xác nhận xong: `.env` VPS (14 biến, owner `thangnd`, 600, 2 khóa base64 44 ký tự), DNS `auth` (cùng IP `web`), repo Docker Hub Private + 2 token, `docker login` user `thangnd`, secrets GitHub BE/FE.
- Quyết định: giữ image Docker Hub (Quyết định 1); không rsync jar/`.class` lên VPS — user hỏi, so sánh rồi chọn giữ (rollback theo tag, Keycloak cùng cơ chế).
- Media S3 (`CARENEST_EDUCATION_MEDIA_*`) chưa có bucket ⇒ `compose.vps.yaml` chưa truyền; API vẫn khởi động, chỉ upload/tải học liệu lỗi khi gọi. Có bucket ⇒ thêm biến vào compose + mẫu env.
- Tiếp: merge PR ⇒ workflow Deploy VPS ⇒ AC-1..AC-3 ⇒ bootstrap gateway qua tunnel ⇒ `CARENEST_AUTH_ENABLED=true` ⇒ FE Variables.
