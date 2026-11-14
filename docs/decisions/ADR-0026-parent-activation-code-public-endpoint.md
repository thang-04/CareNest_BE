# ADR-0026 — Mã kích hoạt tài khoản phụ huynh qua SMS và endpoint kích hoạt công khai

- Status: ACCEPTED
- Date: 2026-11-11
- Liên quan: AUTH-18, AUTH-21, ADR-0011, ADR-0013; plan `docs/plans/active/2026-11-10-twilio-sms-otp.md`; spec `specs/018-parent-activation-code/`

## Bối cảnh
AUTH-18 (user chốt 30/10, 10/11): không gửi mật khẩu qua SMS; khi có nhà cung cấp SMS, gửi mã kích hoạt 6 số hạn 72 giờ, sai 5 lần hủy; phụ huynh nhập SĐT + mã trên App rồi tự đặt mật khẩu. Phụ huynh chưa có mật khẩu nên chưa đăng nhập được — bước kích hoạt phải là endpoint không cần token. Gateway Web `/auth/**` cấm phụ huynh (AUTH-21) và dùng cookie/CSRF cho trình duyệt.

## Quyết định
1. Bật nhà cung cấp SMS thật (`carenest.sms.provider` khác `fake`) ⇒ tài khoản phụ huynh mới tạo **không có mật khẩu**; BE sinh mã, lưu hash trong bảng riêng `parent_activation_codes` (một dòng/tài khoản), gửi mã qua `SmsGateway`. Cổng giả ⇒ giữ mật khẩu tạm như hiện tại.
2. Endpoint công khai `POST {API_PREFIX}/parent-activation` (`permitAll`, không cookie, không CSRF) nhận SĐT + mã + mật khẩu mới: kiểm mã trong transaction có khóa dòng, đếm lần sai; đúng ⇒ BE đặt mật khẩu không tạm qua Keycloak Admin API, rồi đánh dấu đã kích hoạt.
3. Mọi trường hợp mã sai/hết hạn/hết lượt/SĐT không có mã trả cùng một lỗi `OTP_INVALID_OR_EXPIRED` (không lộ SĐT nào có tài khoản).
4. Gửi lại mã chỉ HT/PHT có `PARENT_ACCOUNT_MANAGE` (`POST /parent-accounts/{userId}/activation-code`), cách nhau 60 giây; mã cũ mất hiệu lực. Không gửi được SMS thật ⇒ response trả mã để in phiếu.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Endpoint công khai riêng (chọn) | Không đụng gateway Web; APP gọi trực tiếp | Thêm một đường không xác thực, phải tự chống dò mã |
| Qua gateway `/auth/**` | Có sẵn OTP/quota | Gateway chỉ cho Web, cookie/CSRF; trái AUTH-21 |
| Twilio Verify | Twilio lo sinh/kiểm mã | Khóa vào Twilio, hạn mã tối đa ngắn hơn 72 giờ |
| Dùng lại `password_tokens` | Ít bảng | Ràng buộc hạn ngắn và job dọn của gateway; nghĩa cột khác |

## Hệ quả
- Chống dò: 6 số, tối đa 5 lần sai mỗi mã (khóa dòng khi đếm), hạn 72 giờ; mã chỉ lưu hash SHA-256 gắn userId. Người đọc được DB vẫn thử hết 10^6 mã offline được — chấp nhận vì mã ngắn hạn, quyền đọc DB đã là sự cố lớn hơn.
- Chưa có giới hạn theo IP cho endpoint công khai; kẻ có danh sách SĐT thử tối đa 5 lần mỗi mã (xác suất trúng 5/10^6 mỗi tài khoản).
- Đặt mật khẩu và đánh dấu đã kích hoạt không cùng transaction: Keycloak lỗi ⇒ mã còn dùng lại được; DB lỗi sau khi đặt ⇒ mã vẫn dùng được thêm một lần (cùng chủ SĐT), không mở rộng quyền.
- APP cần màn "Kích hoạt tài khoản"; FE cần nút gửi lại mã/phiếu in mã (plan client riêng).

## Còn chờ
- Giới hạn theo IP/thiết bị khi lên production (cần hạ tầng, chưa chốt).
- Nhà cung cấp SMS có brandname cho production (D-2).
