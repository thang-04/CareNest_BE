---
id: ENV-261115-dev-keycloak-password-policy
type: env
modules: [identity-access, deployment]
rules: []
status: fixed
date: 2026-11-15
keywords: [keycloak, invalidPasswordMinLengthMessage, passwordPolicy, length(8), KEYCLOAK_DEV_USER_PASSWORD, demo123, demo1234, import-realm, deploy dev, ROLLBACK FAILED, 1013, authGatewayToken, keycloakStatus=401, bootstrap.mjs, gateway secret, carenest-users-0.json]
similar_to: [ENV-261112-dev-seed-staff-username-conflict]
---

# ENV-261115-dev-keycloak-password-policy — Dựng lại DB dev: Keycloak không lên với mật khẩu 7 ký tự, rồi web 503 vì sai gateway secret

## Symptom
Dựng lại bộ dữ liệu dev (tạo lại `carenest_dev` + `keycloak_dev`, `KEYCLOAK_DEV_USER_PASSWORD=demo123`) rồi chạy job `deploy-dev`:
```text
keycloak-1 | ERROR: Failed to start server in (production) mode
keycloak-1 | ERROR: invalidPasswordMinLengthMessage
[deploy:dev] ROLLBACK FAILED: previous version is not healthy
```
`https://auth-dev.carenest.edu.vn` 502. Sau khi Keycloak lên (bước dưới), đăng nhập web trả 503 `errorCode 1013` "Dịch vụ đăng nhập đang gián đoạn", log API `[authGatewayToken]|keycloakStatus=401|oauthErrorCode=unknown`. Thêm nữa `teacher2` đăng nhập ra người của bản seed cũ.

## Root cause
1. Realm có `passwordPolicy: length(8) and notUsername` từ 05/10/2026. File user VPS (`docker/keycloak/vps/carenest-users-0.json`, có từ 01/11/2026 – commit `8fb0827a`) nạp mật khẩu **thô** từ `KEYCLOAK_DEV_USER_PASSWORD` ⇒ bị kiểm chính sách ⇒ `demo123` (7 ký tự) làm import lỗi, Keycloak không khởi động. Trước 01/11 VPS mount file user **dev** chứa **hash** `demo123` (hash không bị kiểm chính sách) và realm không bao giờ bị import lại ⇒ `demo123` vẫn chạy tới khi tạo lại `keycloak_dev`.
2. Deploy lỗi ⇒ `remote-deploy.sh` rollback: khôi phục `carenest_dev` về dump đầu deploy (rỗng) rồi chạy image cũ ⇒ image cũ nạp seed cũ. Các lần deploy sau chạy seed mới nhưng `ON CONFLICT DO NOTHING` giữ dòng cũ ⇒ dữ liệu 2 phiên bản trộn nhau.
3. Realm mới chưa có cấu hình gateway; `bootstrap.mjs --apply` ghi `KEYCLOAK_AUTH_GATEWAY_CLIENT_SECRET` **của máy chạy script** vào client gateway ⇒ khác `.env` dev ⇒ Keycloak trả 401 cho API.

## Fix
- `KEYCLOAK_DEV_USER_PASSWORD=demo1234` (≥ 8 ký tự) trong `.env` dev, tạo lại `keycloak_dev`, deploy lại. Giữ quyết định bảo mật 01/11 (không đưa hash mật khẩu công khai lên VPS); local vẫn `demo123`. <!-- carenest:allow-secret: mật khẩu chung của dữ liệu giả dev -->
- Tạo lại `carenest_dev` một lần nữa sau khi Keycloak khỏe, deploy lại ⇒ hết trộn seed (kiểm `teacher2` = Phạm Thị Thanh).
- Chạy `bootstrap.mjs --apply --enable` **trên VPS** trong container `node:22-alpine`, biến lấy từ `.env` dev (đúng secret) ⇒ `"applied": true`, đăng nhập 200.
- Quy trình đầy đủ: `docs/system/DEPLOYMENT.md` mục "Dựng lại bộ dữ liệu dev"; `deploy/dev.env.example` ghi rõ ≥ 8 ký tự.

## Attempts (đã thử, không được)
- Chạy lại `deploy-dev` khi `.env` vẫn `demo123` ⇒ lỗi y hệt (import realm mới mỗi lần).
- Script reset có `set -euo pipefail` gõ thẳng ở shell đăng nhập + `docker compose -f compose.dev.yaml stop` ⇒ compose đòi `KEYCLOAK_IMAGE` (CI mới truyền) ⇒ lỗi ⇒ shell thoát, mất SSH. Dùng `bash -c '…'` và `docker stop` theo tên container.
- Chạy `bootstrap.mjs` trên VPS bằng `node` ⇒ `node: command not found` (VPS không cài Node, không có script) ⇒ chép script + chạy trong container Node.

## Regression check
- Mỗi lần dựng lại: Keycloak 200 ở `/.well-known/openid-configuration`; đăng nhập web `principal`, `teacher2` ra đúng họ tên ở `docs/TAI_KHOAN_DEV.md`; `kitchen` 403 (đúng: bếp chỉ dùng App).
- `docker/keycloak/auth/vps-users.test.mjs` giữ: file VPS chỉ dùng placeholder mật khẩu, không hash công khai.
