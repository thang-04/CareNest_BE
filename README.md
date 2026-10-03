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

Biến môi trường (xem `.env.example`): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `API_PREFIX`.

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

## Trạng thái

- Đã có: cấu trúc package, response/exception chuẩn, prefix API cấu hình được, OpenAPI, Docker.
- Chưa có: authentication/authorization (team đang chốt phương án), schema/migration nghiệp vụ.
