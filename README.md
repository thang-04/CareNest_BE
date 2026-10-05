# CareNest Backend

REST API dùng chung cho CareNest web (`CareNest_FE`) và mobile (`CareNest_APP`).

- Java 21 (LTS), Spring Boot 4.1.1, Maven (dùng `mvnw`)
- PostgreSQL 17, Spring Data JPA, Flyway
- springdoc-openapi (Swagger UI)
- Xác thực: Keycloak 26 + Spring Security OAuth2 Resource Server (ADR-0011) — hướng dẫn [`docs/system/KEYCLOAK.md`](docs/system/KEYCLOAK.md)

Quy tắc code: [`docs/backend-coding-guide.md`](docs/backend-coding-guide.md) — đọc trước khi code.

Tài liệu nghiệp vụ, kiến trúc, quyết định và engineering memory: [`docs/INDEX.md`](docs/INDEX.md). Làm việc với AI agent (Codex/Claude): `AGENTS.md` → `.ai/ROUTER.md`.

## Chạy

Bắt buộc có `.env` (compose báo lỗi nếu thiếu mật khẩu Keycloak):

```bash
cp .env.example .env      # đổi các giá trị change-me
```

### Cách 1 — Docker Compose (API + PostgreSQL + Keycloak + Mailpit)

```bash
docker compose up --build
```

### Cách 2 — chạy API trên máy, hạ tầng trong Docker

```bash
docker compose up -d postgres keycloak mailpit
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Biến môi trường (xem `.env.example`): DB (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), `SERVER_PORT`, `API_PREFIX`, xác thực (`KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_AUDIENCE`, `CARENEST_CORS_ALLOWED_ORIGINS`), Keycloak compose (`KC_BOOTSTRAP_ADMIN_*`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_CLIENT_SECRET`, ...). Volume postgres tạo trước khi có Keycloak ⇒ tạo DB `keycloak` bằng tay theo `docs/system/KEYCLOAK.md` mục 2.

### Địa chỉ

| | URL |
| --- | --- |
| API | `http://localhost:8080{API_PREFIX}/...` (mặc định `/api`) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Health | `http://localhost:8080/actuator/health` |
| Keycloak Admin Console | `http://localhost:8180/admin` (realm `carenest`) |
| Mailpit (email dev) | `http://localhost:8025` |

Mọi API dưới `{API_PREFIX}` cần `Authorization: Bearer <access token Keycloak>`; cách lấy token dev và dùng Swagger *Authorize*: `docs/system/KEYCLOAK.md` mục 5.

## Test

```bash
./mvnw test
```

`CareNestApplicationTests` dùng Testcontainers (PostgreSQL) và tự skip khi Docker không chạy.

## Response chuẩn

Mọi API trả `{ "code": <HTTP status>, "desc": "...", "data": ... }`. Dùng `ResponseJson` + `ApiCode` trong controller, ném `GlobalException` trong service. Chi tiết: guide mục 8 và 11.

## Trạng thái

- Đã có: cấu trúc package, response/exception chuẩn, prefix API cấu hình được, OpenAPI, Docker, xác thực JWT Keycloak (401/403, role).
- Đang làm: CurrentUser, phân quyền theo scope campus/lớp/trẻ, cấp tài khoản (`docs/plans/active/2026-10-04-keycloak-auth.md`).
- Chưa có: schema/migration nghiệp vụ.
