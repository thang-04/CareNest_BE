# Contract — Ảnh đại diện tài khoản (019)

Prefix `/api`. Envelope `ResponseJson { code, desc, data }`.

| Method | Path | Role | Đổi |
|---|---|---|---|
| PUT | `/accounts/me/photo` (multipart `file`) | mọi role đăng nhập | trước: `PARENT` |
| GET | `/accounts/me/photo` (ảnh, `no-store`) | mọi role đăng nhập | trước: `PARENT` |
| GET | `/accounts/me/photo/url` | mọi role đăng nhập | trước: `PARENT` |
| DELETE | `/accounts/me/photo` | mọi role đăng nhập | mới |
| POST | `/accounts/photos/urls` | mọi role đăng nhập | mới |

## POST /accounts/photos/urls
Request: `{ "userIds": [12, 15, 31] }` — 1..100 phần tử, số dương; trùng thì gộp.

Response 200 `data`:
```json
[{ "userId": 12, "url": "https://…", "expiresAt": "2026-11-14T14:15:00Z" }]
```
Chỉ người có ảnh và được xem theo AUTH-27; thứ tự không đảm bảo. Không trả 403/404 cho từng id.

Lỗi: 400 `VALIDATION_ERROR` khi rỗng, >100, id không hợp lệ.

## DELETE /accounts/me/photo
200, `data` = `AccountPhotoResponse` hiện có (`photoUploaded=false`, `photoUpdatedAt=null`). Không có ảnh vẫn 200. Tài khoản có role `PARENT` ⇒ 403 (ảnh phụ huynh bắt buộc, chỉ thay bằng PUT).

## Tác động client
- FE `CareNest_FE`: `AccountSideCard` (đổi/xóa ảnh), `components/ui/Avatar.jsx` (gom link) — plan G94-356.
- APP: không đổi ngay; phase sau dùng PUT/DELETE cho phụ huynh/bếp và POST urls cho ảnh GV.
