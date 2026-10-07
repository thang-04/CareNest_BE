# Troubleshooting

Lỗi môi trường, build, chạy local, deploy. Mỗi mục ghi: chuỗi lỗi nguyên văn → nguyên nhân → cách xử lý → cách đã thử không hiệu quả. Có incident thì link ID. Cấu hình: `README.md`, mục Cấu hình.

Keycloak/xác thực: bảng lỗi đầy đủ ở `docs/system/KEYCLOAK.md` mục 8.

### `invalid_grant` — `Account is not fully set up` (Keycloak)
- Nguyên nhân: user còn required action (mật khẩu tạm) hoặc thiếu field bắt buộc của user profile.
- Xử lý: đăng nhập trình duyệt một lần; kiểm *Realm settings → User profile* (`email`, `lastName` không bắt buộc).
- Đã thử không hiệu quả: kiểm `requiredActions` (rỗng).
- Incident: ENV-261004-keycloak-user-profile-account-not-fully-set-up

### `make mountpoint ... read-only file system` khi `docker compose up keycloak`
- Nguyên nhân: mount một file vào bên trong thư mục đã bind mount `:ro` (Docker không tạo được mountpoint con).
- Xử lý: mount từng file vào thư mục đích (như `compose.yaml` hiện tại), không mount cả thư mục rồi lồng file.

### `docker exec ... psql -f /tmp/x.sql` báo `C:/Users/.../Temp/x.sql: No such file` (Git Bash trên Windows)
- Nguyên nhân: Git Bash tự đổi đường dẫn dạng `/tmp/...` thành đường dẫn Windows.
- Xử lý: đưa file qua stdin: `docker exec -i <container> psql -U carenest -d carenest < file.sql`, hoặc đặt `MSYS_NO_PATHCONV=1`.

### `FATAL: password authentication failed for user "carenest"`
- Nguyên nhân:
  - Spring không đọc `.env`. Postgres (compose) được tạo với `POSTGRES_PASSWORD` lấy từ `.env`, còn app chạy bằng `mvnw` dùng mặc định `DB_PASSWORD=carenest`. <!-- carenest:allow-secret: mặc định dev trong application.yml -->
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
