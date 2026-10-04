# CareNest Backend

REST API dùng chung cho CareNest web (`CareNest_FE`) và mobile (`CareNest_APP`).

- Java 21 (LTS), Spring Boot 4.1.1, Maven (dùng `mvnw`)
- PostgreSQL 17, Spring Data JPA, Flyway
- springdoc-openapi (Swagger UI)

Quy tắc code: [`docs/backend-coding-guide.md`](docs/backend-coding-guide.md) — đọc trước khi code.

Tài liệu nghiệp vụ, kiến trúc, quyết định và engineering memory: [`docs/INDEX.md`](docs/INDEX.md). Làm việc với AI agent (Codex/Claude): `AGENTS.md` → `.ai/ROUTER.md`. Giải thích cấu trúc tài liệu AI cho người đọc: [`docs/README_AI.md`](docs/README_AI.md).

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

### Cấu hình

Không dùng Spring profile. Mọi cấu hình đi qua biến môi trường; giá trị mặc định nằm trong `application.yml`. **Spring không đọc file `.env`**; `.env` chỉ dùng cho `docker compose`.

| Biến | Dùng bởi | Mặc định | Ghi chú |
| --- | --- | --- | --- |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | compose (postgres + api) | `carenest` | Mật khẩu chỉ có hiệu lực lúc volume `postgres-data` được tạo lần đầu |
| `POSTGRES_PORT` | compose | `5432` | Port trên máy host; đổi biến này không tự đổi `DB_URL` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | app (cách 2) | `jdbc:postgresql://localhost:5432/carenest`, `carenest`, `carenest` | Cách 2: phải export hoặc khai báo trong IDE, giá trị khớp với `POSTGRES_*` |
| `SERVER_PORT` | compose: port host · cách 2: port app | `8080` | Cùng tên nhưng hai nghĩa khác nhau |
| `API_PREFIX` | app | `/api` | Map vào `carenest.api.prefix` (`WebMvcConfig`) |

Khác:
- `ddl-auto: validate` + Flyway (`db/migration`, hiện trống).
- Actuator chỉ mở `health`.
- Upload tối đa 5MB/file, 10MB/request.
- Chưa có auth.
- Hook Claude (`.claude/hooks/`) cần Node ≥ 18.

Lỗi môi trường đã gặp: `docs/knowledge/TROUBLESHOOTING.md`.

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
