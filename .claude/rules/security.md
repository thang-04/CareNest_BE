---
paths:
  - "src/main/java/com/carenest/security/**"
  - "src/main/resources/application*.yml"
  - ".env.example"
---

# Security — CareNest_BE

Nguồn chuẩn: guide mục 2 (Auth), 8.5, 9, 10; `docs/architecture/SECURITY.md`.

- Auth **chưa chốt** — không tự thêm Spring Security/JWT/thư viện auth (hỏi nhóm).
- Quyền = role + scope campus/class/child, kiểm tra ở service/security, không chỉ FE hay role. Truy cập theo id ⇒ xác minh thuộc scope.
- 401/403 từ filter phải ghi `ResponseJson` qua entry point/access denied handler riêng (guide 8.5).
- Nhà bếp không xem hồ sơ sức khỏe/đánh giá; Admin không mặc định xem dữ liệu trẻ.
- Secret qua biến môi trường; `.env.example` chỉ tên biến; không commit `.env`.
- Không log password, token, refresh token, secret, dữ liệu sức khỏe/cá nhân trẻ.
- Có test cho trường hợp bị từ chối (lớp khác, cơ sở khác, phụ huynh không liên kết).
