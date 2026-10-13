---
id: ENV-261013-keycloak-dev-users-missing
type: env
modules: [identity-access]
rules: []
status: workaround
date: 2026-10-13
keywords: [keycloak, user_not_found, invalid_grant, dev user, teacher.a, import skipped, IGNORE_EXISTING, partialImport, sai mật khẩu]
similar_to: [ENV-261004-keycloak-user-profile-account-not-fully-set-up]
---

# ENV-261013-keycloak-dev-users-missing — Dev user báo sai mật khẩu vì không tồn tại trong Keycloak

## Symptom
Đăng nhập Web gateway bằng `teacher.a` + `KEYCLOAK_DEV_USER_PASSWORD` nhận 401 "Email hoặc mật khẩu không đúng". Log:
```text
keycloak: type="LOGIN_ERROR", clientId="carenest-auth-gateway", error="user_not_found", grant_type="password", username="teacher.a"
api:      [authGatewayToken]|keycloakStatus=400|oauthErrorCode=invalid_grant
```

## Điều kiện tái hiện
Volume PostgreSQL có DB `keycloak` với realm `carenest` được tạo trước khi compose mount `docker/keycloak/dev/carenest-users-0.json`. Khởi động lại Keycloak (`--import-realm`): log `Realm 'carenest' already exists. Import skipped`.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Nghi lỗi code Phase 4 (cookie stateless, đổi env) | API chỉ map `invalid_grant` của password grant thành 401 | Lỗi đến từ Keycloak, không phải gateway |
| 2 | Đọc event Keycloak | `user_not_found` cho `teacher.a`, `principal.dev`, `admin` | Không phải sai mật khẩu hay brute-force lock |
| 3 | Liệt kê `user_entity` của realm trong DB `keycloak` | Chỉ có 1 user tạo tay; DB app có đủ 8 tài khoản seed | Realm và seed app lệch nhau |

## Root cause
`--import-realm` dùng chiến lược `IGNORE_EXISTING`: realm đã có thì bỏ qua cả file realm lẫn file user. Realm local được tạo trước khi file user dev được mount, nên user dev chưa bao giờ vào Keycloak. API trả chung "sai mật khẩu" cho `invalid_grant` để không lộ user có tồn tại hay không.

## Fix / workaround
Giữ realm, partial import file user dev với `ifResourceExists=SKIP` qua Admin REST `POST /admin/realms/carenest/partialImport`, thay placeholder `${KEYCLOAK_DEV_USER_PASSWORD}` trước khi gửi. Id user cố định trong file khớp `keycloak_user_id` của seed `db/dev`. Cách nặng hơn: xóa DB `keycloak` để import lại — mất user/client tạo tay và cấu hình gateway đã bootstrap.

## Phòng ngừa
Khi đổi file import, nhớ import chỉ chạy lúc realm chưa tồn tại. Kiểm bằng log `Import skipped` và event `user_not_found`.

## Đã áp dụng
- 2026-10-14 local: chạy `kcadm.sh create partialImport -r carenest -f <file đã thay placeholder> -s ifResourceExists=SKIP` trong container Keycloak (dùng env container, không in secret) ⇒ `added: 8`; id user khớp `user_accounts.keycloak_user_id` seed.
