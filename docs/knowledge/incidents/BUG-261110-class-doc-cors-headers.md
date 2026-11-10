---
id: BUG-261110-class-doc-cors-headers
type: bug
modules: [education-plan, security]
rules: []
status: fixed
date: 2026-11-10
keywords: [CORS, preflight, X-Class-Doc-Version, status 0]
---

# BUG-261110-class-doc-cors-headers

- **Module:** education-plan (tài liệu lớp) · `config/SecurityConfig.java`
- **Triệu chứng:** Web bấm Lưu ở "Chủ đề / Chủ đề nhánh" ⇒ toast "Không thể kết nối máy chủ" (status 0). User mô tả là "lỗi do máy chủ".
- **Root cause:** `PUT /education-plans/class-docs/{kind}/{docKey}` cần header `X-Class-Doc-Version` (và `X-Dossier-Version`), nhưng CORS `allowedHeaders` chỉ có `Authorization`, `Content-Type`, `X-Edit-Session`, `X-Plan-Revision`, `Idempotency-Key`, `Range`. Preflight trả `Access-Control-Allow-Headers: authorization, content-type` ⇒ trình duyệt hủy request.
- **Đã thử:** gọi API bằng Node (không có CORS) với nhiều biến thể payload (tên rỗng, 2 tuần, đổi tên nhánh kế thừa, weekNotes null) ⇒ đều 200, nên lỗi không nằm ở service. Tái hiện bằng Playwright headless trên Web ⇒ thấy status 0, `curl -X OPTIONS` xác nhận header bị thiếu.
- **Sửa:** thêm hai header vào `allowedHeaders`; test `SecurityConfigTest#shouldAllowClassDocVersionHeadersInPreflight`.
- **Bài học:** thêm header tùy biến cho API Web ⇒ thêm cả vào CORS và test preflight; lỗi status 0 khi curl vẫn chạy ⇒ nghĩ tới CORS trước.
- **Phát hiện kèm (chưa sửa):** BE nhận lưu nhánh dài 4 tuần dù cấu hình tối đa 2 tuần (Web chặn, server chưa chặn) — cùng họ với BUG-261108-duration-feedback-late.
