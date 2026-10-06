# CareNest Backend

Backend REST API của **CareNest**, hệ thống quản lý vận hành và hồ sơ trẻ cho Trường Mầm non Thượng Hồng (Hải Phòng): 1 trường, 2 điểm trường, 24 lớp, ~506 trẻ, ~60 nhân sự. Đồ án SEP490.

Repo này là **nguồn chuẩn (source of truth)** của toàn dự án: nghiệp vụ, domain, API contract, database và engineering memory. Hai client chỉ gọi API và không chứa business rule.

| Repo | Vai trò | Người dùng chính |
| --- | --- | --- |
| `CareNest_BE` (repo này) | REST API, business rule, phân quyền, database | — |
| `CareNest_FE` | Web | System Admin, Hiệu trưởng, Hiệu phó, Giáo viên |
| `CareNest_APP` | Mobile | Phụ huynh, Giáo viên, Bếp |

> **Trạng thái:** mới có nền móng (scaffold). Chưa có authentication/authorization và chưa có API nghiệp vụ. Xem [Trạng thái và lộ trình](#trạng-thái-và-lộ-trình).

## CareNest giải quyết gì

Trường đang dùng song song PMS, GoKids, Zalo, sổ giấy và hệ thống ngành, nên dữ liệu bị nhập lặp, tổng hợp tay và khó tra lịch sử. CareNest gom các workflow đó vào một hồ sơ có cấu trúc, lấy trẻ làm trung tâm:

- **Vận hành:** điểm danh, phụ huynh báo nghỉ, chốt số suất ăn gửi bếp, thực đơn và định lượng, sự cố cơ sở vật chất, báo cáo theo lớp / điểm trường / toàn trường.
- **Hồ sơ trẻ:** sức khỏe định kỳ, quan sát và đánh giá hằng ngày, hồ sơ phát triển, chia sẻ thông tin được phép cho phụ huynh.
- **AI chỉ hỗ trợ:** gợi ý thực đơn, diễn giải xu hướng sức khỏe, nháp nhận xét. AI tạo bản nháp, con người duyệt; hệ thống vẫn chạy đủ khi tắt AI.

Ngoài phạm vi V1: multi-school / multi-tenant, chat thay Zalo, chẩn đoán y tế hoặc tâm lý, kế toán / payroll, quản lý vòng đời tài sản, soạn và duyệt giáo án. Scope đầy đủ: [`docs/context/PROJECT_CONTEXT.md`](docs/context/PROJECT_CONTEXT.md).

## Tech stack

| Thành phần | Công nghệ |
| --- | --- |
| Ngôn ngữ | Java 21 (LTS) |
| Framework | Spring Boot 4.1.1: Web MVC, Validation, Data JPA, Actuator |
| Database | PostgreSQL 17, Flyway |
| API docs | springdoc-openapi 3.1.1 (Swagger UI) |
| Build | Maven Wrapper (`mvnw`), Lombok |
| Test | JUnit, Spring Boot Test, Testcontainers (PostgreSQL) |
| Môi trường dev | Docker, Docker Compose |

## Kiến trúc

**Layered monolith, ranh giới theo feature** ([ADR-0001](docs/decisions/ADR-0001-modular-monolith.md)): một ứng dụng Spring Boot + một PostgreSQL, đủ cho quy mô một trường. Không microservice, không message broker.

```text
Web (CareNest_FE) ─────┐
                       ├─ HTTPS/JSON ─► CareNest_BE (Spring Boot) ─► PostgreSQL
Mobile (CareNest_APP) ─┘                 └─► AI provider qua adapter (chưa chốt)
```

- Luồng request: `controller → service → repository`. Entity không trả qua API; mapper chuyển Entity ↔ DTO.
- Mọi business rule và kiểm tra quyền nằm ở BE. Client chỉ ẩn/hiện UI theo dữ liệu BE trả về.
- Dữ liệu phân theo điểm trường (`campus_id`); quyền = Role × Permission ∩ Scope (trường / điểm trường / lớp / trẻ), xem [ADR-0002](docs/decisions/ADR-0002-campus-data-scoping.md), [ADR-0003](docs/decisions/ADR-0003-access-scope-model.md).

```text
src/main/java/com/carenest/
├── controller/<feature>/   REST endpoint, @Valid, trả ResponseJson
├── service/<feature>/      nghiệp vụ, @Transactional, kiểm tra quyền theo scope
├── repository/<feature>/   Spring Data JPA
├── entity/<feature>/       JPA entity
├── dto/<feature>/          request/response DTO (dto/common: PageResponse, FieldErrorResponse)
├── mapper/<feature>/       Entity ↔ DTO
├── integration/            adapter dịch vụ ngoài: AI, storage, notification
├── security/               xác thực, phân quyền (chưa triển khai)
├── config/                 WebMvcConfig (prefix API), OpenApiConfig
├── exception/              GlobalException, GlobalExceptionHandler
├── utils/                  ApiCode, ResponseJson
└── common/
```

Module nghiệp vụ, mỗi module là một feature sub-package xuyên các lớp: identity-access, school-structure, child, attendance, nutrition, health, learning-observation, facility-issue, notification, audit, ai-assistance, reporting. Mỗi module có card riêng trong [`docs/modules/`](docs/modules/); chiều phụ thuộc giữa module ở [`docs/system/MODULE_MAP.md`](docs/system/MODULE_MAP.md).

## Chạy local

### Yêu cầu

- Docker và Docker Compose
- JDK 21: cần khi chạy API ngoài Docker (cách 2) và khi chạy test
- Node.js ≥ 18: chỉ cần nếu dùng hook Claude Code trong `.claude/hooks/`

Không cần cài Maven, repo đã có wrapper `./mvnw` (Windows: `mvnw.cmd`).

### Chuẩn bị

```bash
cp .env.example .env    # đổi POSTGRES_PASSWORD; cách 2 cần thêm DB_PASSWORD khớp
```

`.env` đã có trong `.gitignore`, không commit file này.

### Cách 1: Docker Compose (API + PostgreSQL)

```bash
docker compose up --build
```

### Cách 2: API chạy trên máy, PostgreSQL trong Docker

Dùng khi cần debug trong IDE.

```bash
docker compose up -d postgres
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

**Spring không đọc file `.env`.** Biến `DB_*` phải export trong shell hoặc khai báo trong run config của IDE, và `DB_PASSWORD` phải khớp `POSTGRES_PASSWORD`. Ví dụ: `export DB_PASSWORD=...` (bash) hoặc `$env:DB_PASSWORD="..."` (PowerShell).

### Địa chỉ

| | URL |
| --- | --- |
| API | `http://localhost:8080{API_PREFIX}/...` (mặc định `/api`) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Health | `http://localhost:8080/actuator/health` |

## Cấu hình

Không dùng Spring profile. Mọi cấu hình đi qua biến môi trường, giá trị mặc định nằm trong [`application.yml`](src/main/resources/application.yml). File `.env` chỉ dùng cho `docker compose`.

| Biến | Dùng bởi | Mặc định | Ghi chú |
| --- | --- | --- | --- |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | compose (postgres + api) | `carenest` | Mật khẩu chỉ có hiệu lực lúc volume `postgres-data` được tạo lần đầu |
| `POSTGRES_PORT` | compose | `5432` | Port trên máy host; đổi biến này không tự đổi `DB_URL` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | app (cách 2) | `jdbc:postgresql://localhost:5432/carenest`, `carenest`, `carenest` | Phải khớp với `POSTGRES_*` |
| `SERVER_PORT` | compose: port host · cách 2: port app | `8080` | Cùng tên nhưng hai nghĩa khác nhau |
| `API_PREFIX` | app | `/api` | Map vào `carenest.api.prefix` (`WebMvcConfig`) |

Cấu hình cố định khác:

- `ddl-auto: validate`: schema chỉ đổi qua Flyway migration trong `src/main/resources/db/migration` (hiện trống). Migration đã commit thì không sửa, tạo migration mới.
- Actuator chỉ mở endpoint `health`.
- Upload tối đa 5MB/file, 10MB/request.

## Test

```bash
./mvnw test
```

- `CareNestApplicationTests` khởi động toàn bộ context với PostgreSQL thật qua Testcontainers; tự skip khi Docker không chạy.
- `ResponseContractTest` khóa format response `{code, desc, data}` cho thành công, validation, 404/405/415/500.

Chiến lược test và các kịch bản bắt buộc: [`docs/quality/TEST_STRATEGY.md`](docs/quality/TEST_STRATEGY.md).

## Quy ước API

- Prefix cấu hình qua `API_PREFIX` (mặc định `/api`), tự gắn cho mọi controller trong `com.carenest.controller`. Controller không hardcode prefix; Swagger và Actuator không bị gắn prefix.
- Resource là danh từ số nhiều, `kebab-case`: `/children`, `/attendance-records`. Thao tác xác nhận/duyệt dùng endpoint hành động: `POST /meal-counts/{id}/confirm`.
- Danh sách phải phân trang (`PageResponse`); ngày giờ theo ISO-8601.
- Mọi response, thành công hay lỗi, có cùng một dạng:

```json
{ "code": 200, "desc": "Get child profile success", "data": { "id": 123, "name": "Example" } }
```

`code` trùng HTTP status (enum `ApiCode`); `data` luôn có mặt, là `null` khi không có dữ liệu. Controller trả `ResponseJson`, service ném `GlobalException`, `GlobalExceptionHandler` chuyển lỗi về đúng format. Chi tiết: mục 7, 8, 11 của [coding guide](docs/backend-coding-guide.md) và [`docs/contracts/ERROR_CONTRACT.md`](docs/contracts/ERROR_CONTRACT.md).

## Cấu trúc repo

```text
CareNest_BE/
├── src/                 source và test
├── docs/                nghiệp vụ, kiến trúc, contract, database, engineering memory
├── .ai/                 router, profile, workflow cho AI agent (dùng chung Claude Code và Codex)
├── .claude/, .agents/   skill, rule, hook: điểm vào cho Claude Code và Codex
├── AGENTS.md, CLAUDE.md hướng dẫn cho coding agent
├── Dockerfile           build multi-stage, runtime chạy bằng user không phải root
├── compose.yaml         PostgreSQL + API cho dev/demo
└── .env.example         mẫu biến môi trường
```

## Tài liệu

| Cần | Đọc |
| --- | --- |
| Quy tắc code (**đọc trước khi code**) | [`docs/backend-coding-guide.md`](docs/backend-coding-guide.md) |
| Mục lục toàn bộ tài liệu | [`docs/INDEX.md`](docs/INDEX.md) |
| Bối cảnh, scope V1, exclusions | [`docs/context/PROJECT_CONTEXT.md`](docs/context/PROJECT_CONTEXT.md) |
| Đã làm / chưa làm | [`docs/context/CURRENT_STATE.md`](docs/context/CURRENT_STATE.md) |
| Thuật ngữ tiếng Việt ↔ code | [`docs/context/GLOSSARY.md`](docs/context/GLOSSARY.md) |
| Business rule (có ID, status, nguồn) | [`docs/business/BUSINESS_RULES.md`](docs/business/BUSINESS_RULES.md) |
| Role và phân quyền | [`docs/business/USER_ROLES.md`](docs/business/USER_ROLES.md) |
| Nghiệp vụ theo module | [`docs/modules/`](docs/modules/) |
| Kiến trúc hệ thống | [`docs/system/SYSTEM_ARCHITECTURE.md`](docs/system/SYSTEM_ARCHITECTURE.md) |
| Quyết định kiến trúc (ADR) | [`docs/decisions/`](docs/decisions/) |
| Lỗi đã gặp | [`docs/knowledge/ISSUE_INDEX.md`](docs/knowledge/ISSUE_INDEX.md), [`docs/knowledge/TROUBLESHOOTING.md`](docs/knowledge/TROUBLESHOOTING.md) |
| Tiêu chí hoàn thành | [`docs/quality/DEFINITION_OF_DONE.md`](docs/quality/DEFINITION_OF_DONE.md) |

## Quy trình đóng góp

- **Branch:** `feature/<KEY>-<mo-ta>`, `fix/<KEY>-<mo-ta>`, `chore/<mo-ta>`; `KEY` là mã Jira, ví dụ `CN-123`.
- **Commit:** Conventional Commits bằng tiếng Anh, `<type>(<scope>): <subject>` với `type` thuộc `feat|fix|refactor|test|docs|chore|build|ci`; subject ≤ 72 ký tự. Mỗi commit một thay đổi logic, gắn đúng một mã Jira ở footer:

  ```text
  feat(response): add PageResponse for paginated APIs

  - Avoid exposing Spring Page structure to clients

  Refs: CN-123
  ```

- **Trao đổi với team trước khi:** thêm/nâng dependency hoặc đổi version (`pom.xml`, `Dockerfile`, `compose.yaml`); đổi API contract (endpoint, DTO, `ResponseJson`, `ApiCode`, prefix) kèm danh sách tác động FE/APP; thêm hạ tầng mới chưa chốt.
- **Trước khi mở PR:** `./mvnw test` pass, đối chiếu Checklist mục 17 của coding guide và [Definition of Done](docs/quality/DEFINITION_OF_DONE.md).
- Gặp lỗi không hiển nhiên thì ghi lại vào `docs/knowledge/`, kể cả các cách đã thử không hiệu quả.
- Không ghi tên công cụ AI, `Co-Authored-By` của AI trong commit, PR hay comment code.

## Làm việc với AI agent

Repo có bộ tài liệu riêng cho Claude Code và Codex. Agent bắt đầu từ [`AGENTS.md`](AGENTS.md) (Claude nạp qua `CLAUDE.md`), sau đó [`.ai/ROUTER.md`](.ai/ROUTER.md) chọn workflow và module card cần đọc. Người muốn hiểu cách bộ tài liệu này được tổ chức: [`docs/README_AI.md`](docs/README_AI.md).

## Trạng thái và lộ trình

Theo [`docs/context/CURRENT_STATE.md`](docs/context/CURRENT_STATE.md):

- **Đã có:** cấu trúc package, response/exception chuẩn, prefix API cấu hình được, OpenAPI, Docker Compose, tài liệu nghiệp vụ và kiến trúc bản đầu.
- **Chưa có:** authentication/authorization (team đang chốt phương án), schema/migration nghiệp vụ, OpenAPI export, hạ tầng deploy.

Thứ tự triển khai:

1. Nền móng: **xong**.
2. Đăng nhập và phạm vi: user/role, phân công điểm trường/lớp, liên kết phụ huynh–trẻ: **kế tiếp**.
3. Điểm danh → BGH xác nhận số suất → bếp.
4. Hồ sơ trẻ: quan sát, sức khỏe, chia sẻ phụ huynh, audit, lịch sử.
5. Mở rộng: sự cố cơ sở vật chất, thông báo, thực đơn; AI sau khi dữ liệu nền sẵn sàng.

## Xử lý sự cố

Lỗi hay gặp nhất khi chạy cách 2:

```text
FATAL: password authentication failed for user "carenest"
```

Nguyên nhân: app không đọc `.env` nên dùng `DB_PASSWORD` mặc định, hoặc volume `postgres-data` đã được tạo trước đó với mật khẩu khác. Cách xử lý: khai báo `DB_PASSWORD` khớp `POSTGRES_PASSWORD`, hoặc xóa volume nếu không cần giữ dữ liệu local (`docker compose down -v`). Các lỗi môi trường khác: [`docs/knowledge/TROUBLESHOOTING.md`](docs/knowledge/TROUBLESHOOTING.md).
