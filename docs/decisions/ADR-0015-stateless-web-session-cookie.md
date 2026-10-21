# ADR-0015 — Phiên Web nằm trong cookie mã hóa, Keycloak giữ phiên

- Status: ACCEPTED
- Date: 2026-10-13
- Liên quan: ADR-0013 (thay phần "encrypted server-side token storage"), AUTH-07; plan `docs/plans/active/2026-10-11-react-auth-keycloak.md` Phase 4

## Bối cảnh
ADR-0013 lưu access/refresh token mã hóa trong bảng `web_auth_sessions`, cookie chỉ chứa handle. Bảng này lặp lại việc Keycloak đã làm: Keycloak đã giữ phiên, áp idle 30 phút / trần 10 giờ (`ssoSessionIdleTimeout`, `ssoSessionMaxLifespan`) và thu hồi phiên (`logoutUser` khi khóa tài khoản). Team muốn bớt dữ liệu đăng nhập lưu ở DB. Refresh token không thể để JavaScript giữ: client gateway là confidential (refresh cần `client_secret`), và XSS đọc được `localStorage`. BE chạy 1 instance Docker.

## Quyết định
1. Cookie `CARENEST_SESSION` (HttpOnly, SameSite=Lax, path `{API_PREFIX}/auth`) chứa `{loginAt, remember, refreshToken}` mã hóa AES-GCM bằng khóa gateway hiện có, AAD riêng cho cookie. Không còn bảng phiên (V5 xóa `web_auth_sessions`).
2. Mỗi refresh gọi Keycloak, trả access token và cookie mới (refresh token xoay vòng). Cookie hỏng/sai khóa/quá 10 giờ từ login ⇒ 401 và xóa cookie; Keycloak tạm lỗi ⇒ 503, giữ cookie.
3. Refresh đồng thời cùng cookie gộp trong bộ nhớ BE (`AuthRefreshCoalescer`): một lần gọi Keycloak, kết quả giữ 30 giây cho request đến sau.
4. Giữ 2 bảng recovery trong DB (V6 đổi tên `web_auth_challenges` ⇒ `password_tokens`, `web_auth_rate_limits` ⇒ `otp_send_limits`): OTP decoy cho email không tồn tại và bucket rate theo email không gắn được vào `user_accounts`; bộ đếm cần sống qua restart.
5. Access token vẫn chỉ trong bộ nhớ React; API, CSRF, CORS và kiểm tra ACTIVE/provisioning không đổi.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Giữ 3 bảng, chỉ bỏ cột thừa | Ít thay đổi | Vẫn nhân đôi trạng thái phiên của Keycloak |
| **Cookie mã hóa + 2 bảng recovery** (chọn) | Bỏ bảng phiên; Keycloak là nguồn duy nhất | Gộp refresh chỉ đúng 1 instance; cookie ~1,5KB |
| Thêm challenge/rate vào bộ nhớ (0 bảng) | Không bảng auth | Restart mất OTP đang chờ, bộ đếm rate về 0 |
| Refresh token trong `localStorage` | Không cần BE giữ gì | Lộ qua XSS; confidential client không refresh được từ browser |
| Gộp vào `user_accounts` | Ít bảng | Phá chống dò email (decoy không có account); secret trên entity đọc mọi request |
| Quay về PKCE redirect (ADR-0011) | Không bảng, không gateway | Trái lựa chọn không redirect của ADR-0013 |

## Hệ quả
- Deploy V5 đăng xuất mọi người dùng Web một lần; đổi khóa mã hóa gateway cũng đăng xuất toàn bộ.
- Chạy nhiều instance BE cần sticky session hoặc store chung cho việc gộp refresh; nếu không, hai tab refresh cùng lúc có thể bị Keycloak từ chối.
- Không còn danh sách phiên theo user ở DB; đăng xuất mọi thiết bị dùng Keycloak `logoutUser`.
- Body `{force}` của `/auth/refresh` vẫn nhận nhưng không còn tác dụng.

## Còn chờ
Không.
