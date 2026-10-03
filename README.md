# CareNest Backend

REST API dùng chung cho CareNest web (`CareNest_FE`) và mobile (`CareNest_APP`).

- Java 21 (LTS), Spring Boot 4.1.1, Maven (dùng `mvnw`)
- PostgreSQL 17, Spring Data JPA, Flyway
- springdoc-openapi (Swagger UI)

Quy tắc code: [`docs/backend-coding-guide.md`](docs/backend-coding-guide.md) — đọc trước khi code.

## Chạy

### Cách 1 — Docker Compose (API + PostgreSQL)

```bash
cp .env.example .env      # điền POSTGRES_PASSWORD
docker compose up --build
```

### Cách 2 — chạy API trên máy, PostgreSQL trong Docker

```bash
docker compose up -d postgres
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Biến môi trường (xem `.env.example`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `API_PREFIX`,
`JWT_SECRET` (bắt buộc, ≥ 64 ký tự), `MAIL_USERNAME`, `MAIL_PASSWORD`, `STORAGE_*`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.

Chạy trên máy không cần export biến: tạo `src/main/resources/application-local.yml` (đã gitignore) chứa giá trị
riêng của máy, ví dụ:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/carenest
    username: carenest
    password: carenest
  mail:
    username: your@gmail.com
    password: your-app-password
jwt:
  secret-key: <chuỗi ngẫu nhiên ≥ 64 ký tự>
```

Ảnh lưu trên object storage S3-compatible: `docker compose up -d s3` (SeaweedFS, cổng 9000, key trong `docker/s3.json`).

### Địa chỉ

| | URL |
| --- | --- |
| API | `http://localhost:8080{API_PREFIX}/...` (mặc định `/api`) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Health | `http://localhost:8080/actuator/health` |

## Test

```bash
./mvnw test
```

`CareNestApplicationTests` dùng Testcontainers (PostgreSQL) và tự skip khi Docker không chạy.

## Response chuẩn

Mọi API trả `{ "code": <HTTP status>, "desc": "...", "data": ... }`. Dùng `ResponseJson` + `ApiCode` trong controller, ném `GlobalException` trong service. Chi tiết: guide mục 8 và 11.

> Các API auth/quản lý tài khoản/trẻ/ảnh (branch `feature/auth-user-management`) tạm dùng format
> `{ "code": <mã nghiệp vụ>, "message": "...", "data": ... }` qua `AppExceptionHandler`. Cần nhóm chốt để chuyển sang
> `ResponseJson`/`ApiCode` — xem [`docs/api/API.md`](docs/api/API.md#ghi-chú-cho-nhóm).

## Tài liệu API

[`docs/api/API.md`](docs/api/API.md): toàn bộ API, phân quyền, request/response, mã lỗi, luồng nghiệp vụ.
Postman: `postman/CareNest.postman_environment.json` + `postman/CareNest_Teacher_CRUD.postman_collection.json`
(chạy tay) và `postman/CareNest.postman_collection.json` (bộ test case chạy bằng Runner); danh sách test case trong
`postman/CareNest_TestCases.xlsx`.

## Trạng thái

- Đã có: cấu trúc package, response/exception chuẩn, prefix API cấu hình được, OpenAPI, Docker.
- Đã có (branch `feature/auth-user-management`): đăng nhập JWT (email cho nhân sự, SĐT cho phụ huynh), refresh token
  rotation, quên mật khẩu qua OTP email, 6 role (Hiệu trưởng, Phó HT, Tổ trưởng, Giáo viên, Nhân viên, Phụ huynh),
  quản lý tài khoản (tạo/sửa/đổi role/khoá/xoá tài khoản tạo nhầm), quản lý trẻ, người đón trẻ có duyệt, lưu ảnh S3.
- Chưa có: lớp học/tổ chuyên môn, trạng thái học của trẻ, migration Flyway cho các bảng trên (đang dùng
  `ddl-auto: update`), OTP qua SMS.
