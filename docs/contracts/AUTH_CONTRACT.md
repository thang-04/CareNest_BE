# Auth Contract

> **Status: CHƯA CÓ NỘI DUNG — không dùng làm nguồn.** Cơ chế xác thực chưa chốt. Agent không tự chọn JWT/session; hỏi hoặc đề xuất ADR.

Đã biết: RBAC + access scope (`docs/architecture/SECURITY.md`, ADR-0003); client Web + Mobile cùng dùng.

Điền khi chốt:

- [ ] Cơ chế: session cookie / JWT access + refresh
- [ ] Endpoint: login, refresh, logout, me (thông tin user + role + scope tóm tắt)
- [ ] Thời hạn token, thu hồi
- [ ] Đăng nhập phụ huynh (tài khoản cấp bởi trường? SĐT?)
- [ ] Lưu token phía Web/Mobile (ghi ở FE/APP `AUTH_FLOW.md`)
