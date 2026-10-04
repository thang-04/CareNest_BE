# Troubleshooting

Lỗi môi trường, build, chạy local, deploy. Mỗi mục ghi: chuỗi lỗi nguyên văn → nguyên nhân → cách xử lý → cách đã thử không hiệu quả. Có incident thì link ID. Cấu hình: `README.md`, mục Cấu hình.

### `FATAL: password authentication failed for user "carenest"`
- Nguyên nhân:
  - Spring không đọc `.env`. Postgres (compose) được tạo với `POSTGRES_PASSWORD` lấy từ `.env`, còn app chạy bằng `mvnw` dùng mặc định `DB_PASSWORD=carenest`.
  - Hoặc volume `postgres-data` đã được tạo trước đó với mật khẩu khác.
- Xử lý:
  - Khai báo `DB_PASSWORD` (env hoặc IDE run config) khớp với `POSTGRES_PASSWORD`.
  - Hoặc xóa volume nếu dữ liệu local không cần giữ: `docker compose down -v`.
- Nguồn: suy ra từ phân tích cấu hình (`.env.example`, `compose.yaml`, `application.yml`); chưa có incident.

## Format

```markdown
### <chuỗi lỗi hoặc triệu chứng ngắn>
- Nguyên nhân / Xử lý (lệnh cụ thể) / Đã thử không hiệu quả / Incident: ENV-YYMMDD-slug
```
