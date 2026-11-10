---
id: ENV-261110-keycloak-username-read-only
type: env
modules: [identity-access]
rules: [G94-180/AUTH-21]
status: fixed
date: 2026-11-10
keywords: [Keycloak, username, editUsernameAllowed, error-user-attribute-read-only, user profile, Admin API, đổi tên đăng nhập, đổi SĐT]
similar_to: []
---

# ENV-261110-keycloak-username-read-only — Admin API không đổi được username phụ huynh

## Symptom
Duyệt yêu cầu đổi SĐT phụ huynh (G94-180/AUTH-21) cần đổi `username` Keycloak. Gọi như `KeycloakIdentityProviderClient.updateUsername` (GET user → đổi `username` → `PUT /admin/realms/carenest/users/{id}` bằng service account `carenest-backend`) trả:

```text
400 {"field":"username","errorMessage":"error-user-attribute-read-only","params":["username"]}
```

Username giữ nguyên.

## Điều kiện tái hiện
Keycloak 26.8.0, realm import từ `docker/keycloak/import/carenest-realm.json` với `"editUsernameAllowed": false` (giá trị trước 2026-11-10). Lỗi xảy ra cả với Admin API, không chỉ người dùng tự đổi.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Giả định Admin API luôn đổi được username (plan tiếp nhận mục 6.3 ghi "cần thử") | Không kiểm được lúc đầu vì máy không chạy Docker | Đọc lại sau PUT trong client để không báo thành công giả |
| 2 | KC 26.8.0 thật, realm cũ, PUT username bằng service account | 400 `error-user-attribute-read-only` | Realm tắt "Edit username" chặn cả Admin API |
| 3 | Bật `editUsernameAllowed` qua Admin API | Service account đổi được (204, đọc lại đúng) | Nhưng user profile mặc định cho `user` sửa username ⇒ phụ huynh tự đổi được ở Account Console, lệch `user_accounts.username` |
| 4 | Thêm user profile `username.permissions.edit = ["admin"]` | Service account 204; người dùng `POST /realms/carenest/account/` đổi username ⇒ 400 read-only; đăng nhập bằng username mới được | Cách chọn |
| 5 | Dựng lại KC 26.8.0 import realm json đã sửa (không chỉnh tay) | Service account 204, trùng username 409 `User exists with same username`, người dùng tự đổi 400 | File import tự áp dụng đúng |

## Root cause
Keycloak 26 dùng declarative user profile: thuộc tính `username` chỉ sửa được khi realm bật "Edit username" (`editUsernameAllowed`), kể cả ngữ cảnh Admin API. Quyền ai được sửa (admin/user) nằm ở `permissions.edit` của thuộc tính trong user profile.

## Fix
`docker/keycloak/import/carenest-realm.json`: `"editUsernameAllowed": true`, thuộc tính `username` trong `kc.user.profile.config` đổi `"edit":["admin","user"]` ⇒ `"edit":["admin"]`. Realm đã có (dev cũ, VPS) không nhập lại file ⇒ chỉnh tay theo `docs/system/KEYCLOAK.md` mục 7. BE: realm chưa chỉnh ⇒ `USERNAME_CHANGE_NOT_ALLOWED` (1140) thay cho câu chung `IDENTITY_DATA_REJECTED`; không đổi DB, không SMS.

## Phòng tránh
- Đổi thuộc tính user trên Keycloak: thử trên KC thật (container `quay.io/keycloak/keycloak:26.8.0` + realm json), không suy từ tài liệu.
- Mỗi lần nới quyền sửa thuộc tính ở realm, khóa lại phía người dùng bằng `permissions.edit` của user profile.
