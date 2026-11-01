---
id: ENV-261013-docker-desktop-init-timeout
type: env
modules: [identity-access]
rules: []
status: open
date: 2026-10-13
keywords: [Docker Desktop, starting, init control API, context deadline exceeded, localhost:8080]
similar_to: []
---

# ENV-261013-docker-desktop-init-timeout — Local auth stack không thể khởi động

## Symptom
FE Vite nghe cổng 5173; API 8080, Keycloak 8180 và PostgreSQL 5432 không nghe. `docker desktop status` báo `starting`. Log backend của Docker Desktop lặp `init GET /ping ... context deadline exceeded` và ghi vẫn chờ init control API sau hơn 1 giờ. `docker start carenest_be-postgres-1` và `docker pull postgres:17-alpine` không trả kết quả.

## Điều kiện tái hiện
Docker context `desktop-linux`; bốn container Compose CareNest hiện `exited`. Volume `carenest_be_postgres-data` có từ 2026-10-04. Không chạy API/Flyway trên volume khi chưa đọc được migration history.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Kiểm tra cổng, `docker version`, `docker desktop status` | Server 29.6.1 trả version; engine vẫn `starting`, không có cổng API/DB/KC | `docker version` không chứng minh engine sẵn sàng tạo/start container |
| 2 | `docker container inspect`, `docker start` chỉ Postgres cũ | Container vẫn `exited`, `docker start` treo rồi được hủy bằng Ctrl-C | Chưa có migration hoặc dữ liệu bị thay đổi bởi API |
| 3 | `wsl -d docker-desktop -e /bin/true` | Command thành công nhưng engine không lên | Khởi động WSL một lần không sửa init control API |
| 4 | Kiểm tra image và `docker pull postgres:17-alpine` | Tag pin không có; pull treo, đã hủy CLI | Không retry Compose đè lên volume khi image/engine chưa sẵn |
| 5 | `docker desktop restart --timeout 120` | Thất bại: `Failed to stop Docker Desktop`, nhiều frontend/backend/build process còn chạy, `context deadline exceeded` | Không ép dừng Docker khi chưa biết tác động tới project khác |
| 6 | Dẫn xuất opt-in realm và tạo khóa local riêng trong `target/auth-local.env`; Compose config `--quiet` | Pass; file secret nằm trong `target/` bị Git ignore, `.env` hiện có không bị ghi đè | Cấu hình và source đã sẵn, nhưng không chứng minh runtime chạy được |

## Root cause
Đã xác định blocker trực tiếp: Docker Desktop Linux engine không qua bước init control API; nguyên nhân sâu của timeout chưa xác minh. Keycloak gateway còn thiếu các biến opt-in trong `.env` hiện có; đó là blocker tiếp theo sau khi Docker phục hồi.

## Fix
Chưa khắc phục. Graceful Docker Desktop restart thất bại; việc force stop có thể ảnh hưởng container ngoài CareNest và đang chờ user quyết định. Khi engine `running`, đọc Flyway history của PostgreSQL hiện có trước khi khởi động API; dùng overlay/preview để bật gateway theo `docker/keycloak/auth/README.md`, giữ volume và `.env` của user.

## Regression test
`docker desktop status` = running; `docker start carenest_be-postgres-1` trả về; `pg_isready` thành công; chỉ sau đó kiểm history, `/actuator/health`, realm preview và luồng login.

## Ảnh hưởng
Local Web auth không thể kết nối. FE source và isolated E2E trước đó không chứng minh stack hiện tại đang chạy. Không có dấu hiệu cần sửa dữ liệu tài khoản.
