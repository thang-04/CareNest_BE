# Security Design

## Threat model ngắn

Dữ liệu cần bảo vệ: thông tin trẻ em, sức khỏe, quan hệ phụ huynh, tài khoản. Rủi ro chính: lộ dữ liệu ngoài scope (campus/lớp/trẻ khác), lộ dữ liệu qua AI provider/log, chiếm tài khoản.

## Authentication

Chưa chốt cơ chế (session vs JWT, refresh token, đăng nhập phụ huynh). Contract: `docs/contracts/AUTH_CONTRACT.md` (SKELETON). Khi chốt ⇒ ADR + cập nhật contract + FE/APP AUTH_FLOW.

Yêu cầu tối thiểu (ACCEPTED): mật khẩu băm bằng thuật toán chuẩn (BCrypt/Argon2 qua Spring Security), HTTPS, không log token/mật khẩu, khóa tạm khi đăng nhập sai lặp (ngưỡng PROPOSED).

## Authorization (ADR-0003)

- Permission theo role (`docs/business/USER_ROLES.md`) + access scope theo assignment/guardian link.
- Kiểm tra tại service; `@PreAuthorize` ở controller chỉ là lớp đầu, **không** thay lọc scope trong query.
- Truy cập theo ID (vd. `/children/{id}`) phải kiểm tra ID thuộc scope — chống IDOR.
- Parent: default-deny với dữ liệu chưa được công bố (ADR-0010).
- System Admin không mặc định có quyền đọc dữ liệu trẻ (AUTH-06, PENDING P-16).

## Dữ liệu trẻ & AI (ADR-0007)

- Data minimization: AI chỉ nhận dữ liệu cần cho task, bí danh hóa, không tên/ngày sinh/ảnh.
- Không log prompt/response chứa dữ liệu trẻ.
- Provider external ⇒ cần xem điều khoản lưu dữ liệu của provider trước khi bật (ghi vào ADR-0007 khi chốt).

## Secret & cấu hình

- Secret qua biến môi trường/secret store; không commit `.env`, key, credential.
- Test/fixture dùng dữ liệu giả.

## Audit

Hành động nhạy cảm ghi AuditEvent (`docs/modules/audit.md`).
