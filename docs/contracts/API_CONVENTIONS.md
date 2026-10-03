# API Conventions

> Status: **ACCEPTED.** Nguồn chuẩn: `docs/backend-coding-guide.md` mục 7–8. Tóm tắt cho FE/APP và agent; mâu thuẫn ⇒ guide thắng.

## URL

- Prefix cấu hình được `carenest.api.prefix` (env `API_PREFIX`, mặc định `/api`), gắn tự động cho controller trong `com.carenest.controller..` (`config/WebMvcConfig.java`). Controller chỉ khai báo path resource: `@RequestMapping("/children")`.
- Resource danh từ số nhiều, `kebab-case`: `/children`, `/attendance-records`, `/meal-counts`.
- Xác nhận/duyệt: endpoint hành động: `POST /meal-counts/{id}/confirm`, `POST /meal-plans/{id}/approve`.
- Danh sách phải phân trang + lọc theo ngày/lớp khi phù hợp: `?page=0&size=20&date=2026-10-03&classId=5`.

## Dữ liệu

- JSON; ngày/giờ ISO-8601. Request/response là DTO, không trả entity; không trả field ngoài quyền người gọi (DTO phụ huynh riêng — ADR-0010).
- Upload ảnh: giới hạn kích thước (multipart 5MB/file theo `application.yml`) + kiểm tra content type và phần mở rộng.
- Nhập điểm danh lặp không tạo bản ghi trùng (upsert + unique) — guide 13.1.

## Response

`{code, desc, data}` — `ERROR_CONTRACT.md`.

## OpenAPI

springdoc, Swagger UI `/swagger-ui/index.html`. OpenAPI cập nhật cùng code; export (nếu cần) để ở `docs/api/`. FE/APP thống nhất schema trước khi tích hợp.
