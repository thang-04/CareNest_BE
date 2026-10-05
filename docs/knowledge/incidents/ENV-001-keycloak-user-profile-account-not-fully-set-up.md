---
id: ENV-001
type: env
modules: [identity-access]
rules: [AUTH-04]
status: fixed
date: 2026-10-04
keywords: [keycloak, user profile, Account is not fully set up, invalid_grant, lastName, email, VERIFY_PROFILE]
similar_to: []
---

# ENV-001 — Keycloak 26 chặn user thiếu email/lastName: "Account is not fully set up"

## Symptom
User tạo qua Admin API chỉ có `username` + `firstName` (họ tên đầy đủ) + mật khẩu không tạm, không có required action nào, vẫn không lấy được token:
```text
{"error":"invalid_grant","error_description":"Account is not fully set up"}
```

## Điều kiện tái hiện
Realm import mặc định (Keycloak 26.8.0, declarative user profile luôn bật). Tạo user không có `email` hoặc `lastName`, gán role, password grant qua `carenest-dev`.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Kiểm `requiredActions` của user | Rỗng | Lỗi không đến từ required action gán sẵn |
| 2 | Đọc `GET /admin/realms/carenest/users/profile` | `email`, `firstName`, `lastName` đều `required: {roles: [user]}` | User profile mặc định đòi đủ 3 field ⇒ Keycloak bắt verify profile khi đăng nhập |

## Root cause
Keycloak 26 bật declarative user profile; cấu hình mặc định bắt buộc `email` và `lastName` cho user thường. User thiếu field bắt buộc bị coi là chưa hoàn tất hồ sơ nên password grant bị từ chối. CareNest lưu họ tên đầy đủ ở `firstName` và phụ huynh có thể không có email.

## Fix
`docker/keycloak/import/carenest-realm.json` → `components."org.keycloak.userprofile.UserProfileProvider"`: bỏ `required` của `email`, `lastName`; giữ `firstName` bắt buộc; `email/firstName/lastName` chỉ admin sửa (tránh lệch với `user_accounts`). Đã kiểm lại bằng import mới: user không email/lastName, tên tiếng Việt lấy token thành công.

## Regression test
Kiểm tay theo `docs/system/KEYCLOAK.md` mục 3 (User profile) và mục 5 (lấy token). Không có test tự động (cần Keycloak thật).

## Ảnh hưởng
Cấp tài khoản qua API (phase provisioning) phải gửi `firstName` = họ tên đầy đủ; `email` tùy chọn.

## Lesson
Nâng cấp Keycloak hoặc đổi realm ⇒ kiểm lại user profile; lỗi `Account is not fully set up` không phải lúc nào cũng do required action.
