# Contract — kích hoạt tài khoản phụ huynh bằng mã

## POST `{API_PREFIX}/parent-activation` (công khai, không Bearer, không cookie)
Body: `{ "phone": "0912345678", "code": "123456", "newPassword": "...", "confirmPassword": "..." }` — `code` đúng 6 chữ số; mật khẩu 8–128 ký tự.
- 200 `data`: `{ "username": "0912345678" }` — đăng nhập App bằng tên này và mật khẩu mới.
- 400 `1006 OTP_INVALID_OR_EXPIRED`: SĐT không có mã chờ, mã sai, hết hạn, hết lượt, đã dùng, tài khoản bị khóa (một lỗi chung).
- 400 `9002 MALFORMED_REQUEST`: hai mật khẩu không khớp. 400 `9001 INVALID_REQUEST_DATA`: thiếu/sai định dạng trường.
- 400 `1009 PASSWORD_POLICY_VIOLATION`: Keycloak từ chối mật khẩu (mã vẫn còn, không tính lần sai).
- 503 `IDENTITY_PROVIDER_UNAVAILABLE`: Keycloak lỗi (mã vẫn còn).

## POST `{API_PREFIX}/parent-accounts/{userId}/activation-code` (Bearer, `PARENT_ACCOUNT_MANAGE`)
- 200 `data`: `{ "userId", "username", "smsStatus": "SENT|FAILED", "expiresAt", "activationCode" }` — `activationCode` chỉ có khi không gửi được SMS thật (cổng giả hoặc FAILED), để in phiếu; còn lại `null`.
- 409 `1144 ACTIVATION_NOT_PENDING`: tài khoản đã kích hoạt hoặc tạo bằng mật khẩu tạm. 429 `1007 OTP_RESEND_TOO_SOON`: chưa đủ 60 giây.
- 403 `ACCOUNT_MANAGE_DENIED` ngoài phạm vi; 400 `CANNOT_MANAGE_OWN_ACCOUNT`, `NOT_PARENT_ACCOUNT`; 404 `ACCOUNT_NOT_FOUND`.

## Đổi hành vi khi `carenest.sms.provider` là nhà cung cấp thật
`POST /children/enrollments` và `POST /children/{childId}/guardians/activate`: tài khoản mới tạo không mật khẩu, SMS chứa mã. Response không đổi shape (`smsStatus` như cũ). Cổng giả: không đổi gì.

Consumer: APP màn "Kích hoạt tài khoản" (mới); FE màn Tài khoản phụ huynh: nút "Gửi lại mã", phiếu in mã.
