---
id: ENV-261031-vps-docker-pull-access-denied
type: env
modules: [deployment]
rules: []
status: fixed
date: 2026-10-31
keywords: [deploy vps, docker pull, pull access denied, docker login, private repository, docker hub]
similar_to: [ENV-261031-ci-shallow-checkout-spotless-ratchet]
---

# ENV-261031-vps-docker-pull-access-denied — Deploy VPS: build/push image xong nhưng VPS không pull được

## Symptom
Run Deploy VPS trên `cc6c95c` (merge PR #5): job `verify` xanh, `docker/build-push-action` đẩy xong `api-<sha>` và `keycloak-<sha>`, bước SSH lên VPS lỗi:
```text
Error response from daemon: pull access denied for <user>/carenest-be, repository does not exist or may require 'docker login'
```

## Điều kiện tái hiện
Repo Docker Hub `carenest-be` để Private; trên VPS, user SSH của CI (`VPS_USER`) chưa `docker login`, hoặc đã login bằng `sudo` (credential nằm ở `/root/.docker/config.json`, không phải `~/.docker/config.json` của user đó), hoặc token Read-only hết hạn/bị thu hồi.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Đọc log job deploy (`logs_*.zip` user tải) | Image đã push (có `pushing manifest`), chỉ `docker pull` trên VPS lỗi | Không phải lỗi build/CI, là credential Docker trên VPS |

## Root cause
Docker trên VPS không có credential đọc repo Private cho đúng user mà CI SSH vào.

## Fix
Trên VPS, đăng nhập bằng chính user SSH của CI (không `sudo`): `docker login -u <dockerhub-user>` với token Read-only, thử `docker pull <user>/carenest-be:api-<sha>`, rồi Re-run job `deploy`. User login trên VPS (credential ở `/data/thangnd/.docker/config.json`), Re-run job `deploy` lần 2 xanh 2026-10-31.

## Regression test
Không có test tự động; kiểm bằng run Deploy VPS kế tiếp (job `deploy` xanh, smoke issuer).

## Ảnh hưởng
`docs/system/DEPLOYMENT.md` bước cài một lần: `docker login` phải chạy bằng user SSH của CI.
