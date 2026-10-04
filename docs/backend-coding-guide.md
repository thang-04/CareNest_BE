# CareNest Backend — Coding Guide

> **Nguồn:** `CareNest_Backend_Architecture_v2.docx` (thiết kế kiến trúc Backend CareNest), cộng hai quyết định nhóm đã chốt: prefix API cấu hình được, `code` trong response trùng HTTP status.
> **Trạng thái:** quyết định v1. Đã có skeleton nền móng (cấu trúc package, response/exception chuẩn, prefix API, OpenAPI, Docker). Chưa có auth và nghiệp vụ. Phần đã implement thì **file nguồn là chuẩn**; code trong guide chỉ là ví dụ cách dùng (tên như `ChildService`, `AccessScopeService` là minh họa).
> **Áp dụng cho:** mọi thành viên và mọi AI agent viết/review code trong `CareNest_BE`. Khi source thực tế khác guide: báo xung đột, thống nhất, rồi cập nhật guide cùng commit với code.

Quy ước đọc: **PHẢI** = bắt buộc; **KHÔNG ĐƯỢC** = cấm; **NÊN** = mặc định, được lệch nếu có lý do ghi rõ trong PR.

Mục lục: [1. Tổng quan](#1-tổng-quan-và-phạm-vi-v1) · [2. Stack](#2-tech-stack) · [3. Cấu trúc thư mục](#3-cấu-trúc-thư-mục) · [4. Luồng phụ thuộc](#4-luồng-request-và-quy-tắc-phụ-thuộc) · [5. Trách nhiệm lớp](#5-trách-nhiệm-từng-lớp) · [6. Naming & comment](#6-quy-ước-đặt-tên-và-comment) · [7. API](#7-quy-ước-api) · [8. Response chuẩn](#8-response-chuẩn) · [9. Phân quyền](#9-phân-quyền) · [10. Logging](#10-logging) · [11. Exception](#11-xử-lý-exception) · [12. Dữ liệu & migration](#12-dữ-liệu-và-migration) · [13. Quy tắc nghiệp vụ](#13-quy-tắc-nghiệp-vụ-phải-giữ-trong-code) · [14. Test](#14-test-và-vận-hành) · [15. Thứ tự triển khai](#15-thứ-tự-triển-khai) · [16. Khi guide chưa đủ](#16-khi-guide-chưa-đủ) · [17. Checklist](#17-checklist-trước-khi-hoàn-thành)

---

## 1. Tổng quan và phạm vi v1

- Backend là **một ứng dụng Spring Boot (layered monolith)**, code tổ chức theo 3 lớp `controller → service → repository`. KHÔNG áp dụng DDD tactical patterns (aggregate, domain/entity riêng, ...) trong v1.
- REST API dùng chung cho web (`CareNest_FE`) và mobile (`CareNest_APP`); BE sở hữu business logic, dữ liệu, quyền truy cập và hợp đồng API.
- Phạm vi tổ chức: **một trường, hai cơ sở**. Record và truy vấn PHẢI có campus/class scope khi phù hợp. KHÔNG thiết kế thành SaaS nhiều trường.

| Trong v1 | Không thuộc v1 |
| --- | --- |
| Điểm danh, tổng hợp suất ăn; hồ sơ sức khỏe và quan sát trẻ; cổng thông tin phụ huynh; báo cáo cơ sở vật chất; thông báo; AI hỗ trợ có người duyệt. | Thay hệ thống ngành, chat Zalo; soạn và duyệt giáo án (GoKids giữ); kho/NCC (OPEN — ADR-0009); tích hợp trực tiếp CSDL Bộ/Sở; chẩn đoán y tế; quản lý tài sản toàn diện; SaaS nhiều trường. |

## 2. Tech stack

| Lớp | Lựa chọn |
| --- | --- |
| Backend | **Java 21 (LTS)** + **Spring Boot 4.1.1** (Spring Framework 7, Jakarta EE 11, Jackson 3), Spring MVC/REST; Maven qua `mvnw` |
| Database | PostgreSQL 17 + Spring Data JPA (Hibernate 7); Flyway quản lý migration; pgvector tùy chọn (chỉ khi làm semantic search/RAG) |
| API docs | springdoc-openapi 3.1.x — Swagger UI tại `/swagger-ui/index.html` |
| Auth | **Chưa triển khai — team đang chốt phương án.** Định hướng trong docx: Spring Security + JWT access token + refresh/session policy; kiểm tra role + campus/class; không dùng Keycloak trong v1. KHÔNG tự thêm Spring Security/JWT khi chưa có quyết định |
| Ảnh/tệp | Object storage S3-compatible (MinIO khi self-host) qua `StorageService` |
| AI | `AiClient` adapter trong backend; luồng nghiệp vụ PHẢI chạy được khi AI unavailable |
| Test / vận hành | JUnit, Mockito, Testcontainers; Docker Compose (API + PostgreSQL); Actuator; structured logs |

- KHÔNG thêm Redis, Keycloak, message broker hay dịch vụ hạ tầng mới khi chưa có use case đo được và chưa được nhóm đồng ý.
- Logging dùng SLF4J qua Lombok `@Slf4j`.
- Boot 4 dùng starter dạng module (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, test: `spring-boot-starter-webmvc-test`). Jackson 3: `ObjectMapper` ở package `tools.jackson.databind`; annotation vẫn `com.fasterxml.jackson.annotation.*`.
- Version cố định trong `pom.xml`; đổi version PHẢI chạy lại `mvnw test`.

## 3. Cấu trúc thư mục

```text
CareNest_BE/
├── README.md                 # Cách chạy, cấu hình, test
├── pom.xml
├── Dockerfile
├── compose.yaml              # API + PostgreSQL cho dev/demo
├── .env.example              # Tên biến môi trường, KHÔNG chứa secret
├── docs/
│   ├── INDEX.md              # Bản đồ tài liệu (nghiệp vụ, kiến trúc, ADR ở decisions/, memory ở knowledge/)
│   └── api/                  # OpenAPI export / ví dụ request
└── src/
    ├── main/
    │   ├── java/.../carenest/
    │   │   ├── controller/   # REST endpoints và validation
    │   │   ├── service/      # Nghiệp vụ và transaction
    │   │   ├── repository/   # Spring Data JPA
    │   │   ├── entity/       # JPA entities; KHÔNG trả trực tiếp qua API
    │   │   ├── dto/          # Request và response DTO
    │   │   ├── mapper/       # Entity <-> DTO
    │   │   ├── integration/  # Adapter AI, storage, notification
    │   │   ├── security/     # Xác thực và kiểm tra quyền
    │   │   ├── config/       # Cấu hình Spring, OpenAPI, WebMvc
    │   │   ├── exception/    # GlobalException, GlobalExceptionHandler
    │   │   ├── utils/        # ApiCode, ResponseJson; hàm stateless
    │   │   └── common/       # Thành phần dùng chung khác
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/ # Flyway SQL
    └── test/java/.../carenest/
```

- Mỗi package lớp NÊN chia tiếp theo chức năng: `controller/attendance/`, `service/attendance/`, `dto/attendance/`, ... (attendance, child, meals, facilities, ...).
- KHÔNG tạo package `domain/` hay tách module theo DDD.

## 4. Luồng request và quy tắc phụ thuộc

```text
HTTP Request → Controller → Service → Repository → PostgreSQL
                               └──→ integration/ (AI, storage, notification)
```

- Controller PHẢI chỉ gọi service. Controller KHÔNG ĐƯỢC inject/gọi repository.
- Service gọi repository và integration adapter. Service có thể gọi service khác khi cần tái sử dụng nghiệp vụ; tránh vòng phụ thuộc.
- Repository chỉ truy cập dữ liệu, KHÔNG chứa nghiệp vụ.
- Module nghiệp vụ KHÔNG ĐƯỢC import trực tiếp SDK nhà cung cấp (S3, AI, push/email); chỉ gọi qua interface trong `integration/`.

## 5. Trách nhiệm từng lớp

| Package | PHẢI | KHÔNG ĐƯỢC |
| --- | --- | --- |
| `controller/` | Nhận request, `@Valid` dữ liệu đầu vào, lấy user hiện tại, gọi service, trả `ResponseJson` | Chứa logic/tính toán nghiệp vụ; gọi repository; trả entity |
| `service/` | Xử lý nghiệp vụ, `@Transactional`, kiểm tra quyền theo scope, ném `GlobalException(ApiCode, desc)` khi lỗi nghiệp vụ, trả DTO hoặc dữ liệu nghiệp vụ | Dùng `HttpStatus`, `ResponseEntity`, `ResponseJson`; trả chuỗi lỗi để controller xử lý |
| `repository/` | Spring Data JPA, query | Nghiệp vụ |
| `entity/` | Ánh xạ bảng | Bị trả qua API; dùng làm request body |
| `dto/`, `mapper/` | DTO request/response; mapper chuyển Entity ↔ DTO | Chứa nghiệp vụ |
| `utils/` | `ApiCode`, `ResponseJson`, `CommonUtils`; hàm stateless dùng chung | Logic riêng của một nghiệp vụ |
| `integration/` | Interface + adapter cho AI, storage, notification | Quy tắc nghiệp vụ |

Ví dụ một luồng đầy đủ (mẫu):

```java
// controller/child/ChildController.java
@Slf4j
@RestController
@RequestMapping("/children")              // KHÔNG ghi prefix API ở đây — xem mục 7
@RequiredArgsConstructor
public class ChildController {

    private final ChildService childService;

    @GetMapping("/{childId}")
    public ResponseEntity<ResponseJson<ChildProfileResponse>> getChildProfile(
            @PathVariable Long childId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        ChildProfileResponse response = childService.getChildProfile(currentUser, childId);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child profile success", response);
    }
}
```

```java
// service/child/ChildService.java
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildService {

    private final ChildRepository childRepository;
    private final AccessScopeService accessScopeService;
    private final ChildMapper childMapper;

    @Transactional(readOnly = true)
    public ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId) {
        String prefix = "[getChildProfile]|childId=" + childId;
        log.info("{}|START", prefix);

        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Child not found"));
        accessScopeService.assertCanViewChild(currentUser, child);   // kiểm tra quyền ở server — mục 9

        ChildProfileResponse response = childMapper.toProfileResponse(child);
        log.info("{}|END", prefix);
        return response;
    }
}
```

`CurrentUser`, `AccessScopeService` là tên minh họa; dùng đúng tên đã có trong source khi source được tạo.

## 6. Quy ước đặt tên và comment

### 6.1. Đặt tên

- Class: `PascalCase` — `ChildController`, `AttendanceService`, `MealCountRepository`, `ChildProfileResponse`, `CreateAttendanceRecordRequest`.
- Biến, method: `camelCase`. Hằng số: `UPPER_SNAKE_CASE`.
- Method PHẢI nêu rõ nghiệp vụ: `getChildProfile`, `createAttendanceRecord`, `approveMealPlan`, `confirmMealCount`. KHÔNG dùng `doIt`, `process`, `getData`, `handle`.
- Hậu tố theo lớp: `XxxController`, `XxxService`, `XxxRepository`, `XxxMapper`, `XxxRequest`, `XxxResponse`.
- Bảng/cột DB: `snake_case`. Migration Flyway: `V{n}__{mo_ta_ngan}.sql` (vd. `V3__create_attendance_record.sql`).

### 6.2. Comment trong code

- Chỉ comment ngắn gọn (1 dòng, tối đa 2–3 dòng) ở flow có logic chính hoặc không hiển nhiên; nói *tại sao / quy tắc gì*, không kể lại code làm gì.
- Nơi nên có comment: quy tắc nghiệp vụ (mục 13), kiểm tra quyền theo phạm vi, xử lý idempotent, chỗ cố ý làm khác thông thường.
- Không comment code tự giải thích (getter/setter, DTO, mapper, controller gọi service, CRUD đơn giản); không viết Javadoc tràn lan.
- Viết tiếng Việt, giữ identifier/thuật ngữ tiếng Anh.
- KHÔNG để code bị comment-out, comment kiểu nhật ký, TODO mơ hồ (cần thì `// TODO(<người/issue>): <việc cụ thể>`), thông tin AI, dữ liệu thật hoặc secret. Sửa code thì sửa/xóa comment liên quan.

```java
// Suất ăn chỉ tính trẻ có mặt và có đăng ký ăn, không tính theo sĩ số lớp
long mealCount = records.stream().filter(r -> r.isPresent() && r.isMealRegistered()).count();
```

## 7. Quy ước API

### 7.1. Prefix cấu hình được

Prefix API (vd. `/api`, `/api/v1`) là cấu hình, KHÔNG hardcode trong controller.

```yaml
# application.yml
carenest:
  api:
    prefix: /api        # nhóm chọn giá trị; đổi ở đây là đổi toàn bộ API
```

Đã implement ở `src/main/java/com/carenest/config/WebMvcConfig.java`: prefix gắn cho mọi controller trong package `com.carenest.controller` (kể cả sub-package); springdoc và actuator không bị ảnh hưởng. Đổi qua biến môi trường `API_PREFIX`.

- Controller PHẢI nằm trong `com.carenest.controller..` và chỉ khai báo path resource: `@RequestMapping("/children")`.
- Cấu hình khác cần prefix (vd. security matcher sau này) PHẢI đọc cùng property `carenest.api.prefix`, KHÔNG lặp chuỗi prefix.

### 7.2. Đặt tên endpoint

- Resource là danh từ số nhiều, `kebab-case`: `/children`, `/attendance-records`, `/meal-counts`.
- HTTP method thể hiện hành động: `GET` đọc, `POST` tạo, `PUT`/`PATCH` sửa, `DELETE` xóa.
- Thao tác xác nhận/duyệt nghiệp vụ dùng endpoint hành động rõ ràng: `POST /meal-counts/{id}/confirm`, `POST /meal-plans/{id}/approve`.
- Danh sách PHẢI phân trang và hỗ trợ lọc theo ngày/lớp khi phù hợp (`?page=0&size=20&date=2026-10-03&classId=5`).
- Upload ảnh PHẢI giới hạn kích thước và kiểm tra loại tệp (content type + phần mở rộng).
- Thời gian/ngày trả về theo ISO-8601.
- OpenAPI PHẢI cập nhật cùng code; FE/APP thống nhất schema trước khi tích hợp.

## 8. Response chuẩn

**Mọi** response của API — thành công hay lỗi, kể cả lỗi từ Spring Security và lỗi framework — PHẢI có đúng một dạng:

```json
{ "code": 200, "desc": "Get child profile success", "data": { "id": 123, "name": "Example" } }
```

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `code` | integer | Mã từ `ApiCode`, **trùng HTTP status** của response |
| `desc` | string | Thông báo ngắn cho kết quả/lỗi, hiển thị được cho người dùng |
| `data` | object / array / null | Dữ liệu trả về; luôn có mặt, `null` khi không có dữ liệu |

### 8.1. Quy tắc

- HTTP status thật của response PHẢI bằng `code`. KHÔNG ĐƯỢC trả HTTP 200 kèm `code` lỗi.
- `code` chỉ lấy từ `ApiCode`. Thêm giá trị mới vào `ApiCode` chỉ khi cần một HTTP status chưa có; KHÔNG tự đặt mã số riêng.
- Các lỗi cùng status phân biệt bằng `desc`.
- Lỗi validation: `code = 400`, `data` = danh sách lỗi theo trường.
- Danh sách phân trang: `data` = `PageResponse` (`items`, `page`, `size`, `totalElements`, `totalPages`), không trả trực tiếp `Page` của Spring.
- Service KHÔNG tự đóng gói `ResponseJson`; chỉ controller, `GlobalExceptionHandler` và security handler tạo response.
- KHÔNG trả stack trace, tên class, câu SQL hay thông tin nội bộ cho client.

### 8.2. Ví dụ

```json
// 200 — không có dữ liệu
{ "code": 200, "desc": "Update success", "data": null }

// 404
{ "code": 404, "desc": "Child not found", "data": null }

// 400 — validation
{ "code": 400, "desc": "Invalid request data",
  "data": [ { "field": "classId", "message": "must not be null" },
            { "field": "date", "message": "must be a date in the present or past" } ] }

// 403 — ngoài phạm vi lớp được phân công
{ "code": 403, "desc": "You do not have access to this class", "data": null }
```

### 8.3. File đã implement

File nguồn là chuẩn; guide không chép lại code.

| File | Vai trò |
| --- | --- |
| `src/main/java/com/carenest/utils/ApiCode.java` | Enum mã; `getCode()` = HTTP status. Danh sách + dùng theo tình huống nghiệp vụ: `docs/contracts/ERROR_CONTRACT.md` |
| `src/main/java/com/carenest/utils/ResponseJson.java` | `toJsonWithData(ApiCode, desc, data)`, `toJson(ApiCode, desc)` trả `ResponseEntity` với HTTP status từ `ApiCode`; `of(...)` chỉ tạo body (dùng khi tự ghi response, vd. filter). `@JsonInclude(ALWAYS)` giữ `data: null` |
| `src/main/java/com/carenest/dto/common/PageResponse.java` | `PageResponse.from(page)` hoặc `PageResponse.from(page, mapper::toResponse)` |
| `src/test/java/com/carenest/exception/ResponseContractTest.java` | Test hợp đồng response; chạy lại khi sửa các file trên |

### 8.4. Dùng trong controller

```java
@GetMapping("/{childId}")
public ResponseEntity<ResponseJson<ChildProfileResponse>> getChildProfile(@PathVariable Long childId) {
    return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child profile success",
            childService.getChildProfile(childId));
}

@PostMapping
public ResponseEntity<ResponseJson<AttendanceRecordResponse>> createAttendanceRecord(
        @Valid @RequestBody CreateAttendanceRecordRequest request) {
    return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create attendance record success",
            attendanceService.createAttendanceRecord(request));
}

@GetMapping
public ResponseEntity<ResponseJson<PageResponse<ChildSummaryResponse>>> getChildren(Pageable pageable) {
    return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get children success",
            childService.getChildren(pageable));      // service trả PageResponse
}

return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Update success");   // không có data
```

Lỗi PHẢI ném exception (mục 11) để handler trả thống nhất. Chỉ trả lỗi thủ công bằng `ResponseJson.toJson(ApiCode.X, ...)` khi có lý do ghi rõ.

### 8.5. 401/403 từ Spring Security

**Chưa triển khai** (auth chưa chốt). Lỗi ở filter xảy ra trước controller nên `@RestControllerAdvice` không bắt được ⇒ PHẢI có `AuthenticationEntryPoint` + `AccessDeniedHandler` ghi đúng `ResponseJson`; filter JWT tự viết KHÔNG tự ghi response lỗi dạng khác. Code mẫu: `docs/architecture/SECURITY.md` mục "Response 401/403" — chỉ đọc khi làm auth.

## 9. Phân quyền

- Mọi endpoint nhận `campusId`/`classId`/`childId` PHẢI kiểm tra quyền ở server, đối chiếu với assignment của user. KHÔNG tin `campusId` do client gửi khi chưa đối chiếu.
- Kiểm tra quyền thuộc service/security, không chỉ ở FE và không chỉ dựa vào role.
- Dữ liệu sức khỏe và quan sát trẻ có quyền truy cập hẹp hơn thông báo thông thường.

| Vai trò | Quyền chính |
| --- | --- |
| Giáo viên | Xem và ghi dữ liệu trẻ trong lớp được phân công; không xem lớp/cơ sở khác |
| BGH | Xem dữ liệu theo cơ sở phụ trách; hiệu trưởng xem tổng quan cấp trường theo phân công |
| Phụ huynh | Chỉ xem bản ghi đã chia sẻ của trẻ được liên kết và xác thực với tài khoản |
| Nhà bếp | Chỉ xem thực đơn, số suất ăn và nội dung liên quan bếp; không xem hồ sơ sức khỏe/đánh giá trẻ |
| Quản trị viên | Quản lý tài khoản/cấu hình; không mặc định được xem dữ liệu trẻ |

- Truy cập ngoài phạm vi: ném `GlobalException(ApiCode.FORBIDDEN, ...)`.
- NÊN có test cho trường hợp bị từ chối (lớp khác, cơ sở khác, phụ huynh không liên kết).

## 10. Logging

- Dùng `@Slf4j`. Format tìm kiếm được: `[MethodName]|param=value|...`.
- Ghi điểm `START`/`END` cho method service quan trọng và thông tin đủ để truy vết (id, ngày, lớp).

```java
String prefix = "[confirmMealCount]|mealCountId=" + mealCountId + "|userId=" + currentUser.getId();
log.info("{}|START", prefix);
// xử lý nghiệp vụ
log.info("{}|END", prefix);
```

- KHÔNG ĐƯỢC log: password, token/JWT, refresh token, secret, thông tin sức khỏe, dị ứng, quan sát/đánh giá trẻ, họ tên/địa chỉ/SĐT, nội dung request gửi AI có dữ liệu trẻ. Log id thay vì dữ liệu cá nhân.
- Mức log: `info` cho luồng chính; `warn` cho lỗi nghiệp vụ dự kiến (`GlobalException`); `error` kèm stack trace cho lỗi không dự kiến.

## 11. Xử lý exception

- KHÔNG ĐƯỢC nuốt exception, KHÔNG có catch block rỗng.
- Bắt exception để thêm ngữ cảnh thì PHẢI giữ nguyên cause khi ném lại.
- Lỗi nghiệp vụ dùng `GlobalException` kèm `ApiCode` phù hợp.
- `GlobalExceptionHandler` là nơi duy nhất chuyển exception thành `ResponseJson` (trừ lỗi security ở mục 8.5).
- Khi thêm loại exception mới hoặc dùng thư viện ném exception mới, PHẢI kiểm tra nó được handler map đúng `ApiCode`, không rơi vào 500 ngoài ý muốn.

Đã implement — file nguồn là chuẩn, đọc trực tiếp khi cần:

| File | Vai trò |
| --- | --- |
| `src/main/java/com/carenest/exception/GlobalException.java` | Lỗi nghiệp vụ: `new GlobalException(ApiCode.X, "desc")` hoặc kèm `cause` |
| `src/main/java/com/carenest/exception/GlobalExceptionHandler.java` | Map exception → `ResponseJson`: `GlobalException` (warn; error nếu 5xx), validation → 400 + field errors, request sai → 400, không tìm thấy path → 404, sai method → 405, sai content type → 415, upload quá cỡ → 413, còn lại → 500 với desc chung |
| `src/main/java/com/carenest/dto/common/FieldErrorResponse.java` | Phần tử lỗi validation `{field, message}` |

- Handler `AccessDeniedException` (403 từ `@PreAuthorize`) chỉ thêm khi đã có Spring Security (xem mục 8.5).
- Thêm handler mới PHẢI kèm test trong `ResponseContractTest`.

```java
// service — lỗi nghiệp vụ
Child child = childRepository.findById(childId)
        .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Child not found"));

// bọc lỗi dịch vụ ngoài, giữ cause
try {
    return aiClient.suggestMenu(request);
} catch (AiTimeoutException ex) {
    throw new GlobalException(ApiCode.GATEWAY_TIMEOUT, "Menu suggestion timed out", ex);
}
```

## 12. Dữ liệu và migration

- PostgreSQL, schema quan hệ. Mọi thay đổi schema PHẢI qua Flyway SQL trong `src/main/resources/db/migration`.
- KHÔNG ĐƯỢC sửa migration đã chạy ở môi trường dùng chung; tạo migration mới để sửa.
- KHÔNG lưu binary (ảnh/tệp) trong bảng nghiệp vụ; lưu qua `StorageService`, bảng chỉ giữ metadata/key (`MediaAsset`).
- pgvector chỉ bật khi làm semantic search/RAG; migration bật extension tách riêng.
- Ràng buộc nghiệp vụ quan trọng NÊN có ràng buộc DB tương ứng (unique, foreign key, not null).

Entity và quan hệ: `docs/business/DOMAIN_MODEL.md` (tên chuẩn, vd. `Classroom`, `UserAccount`). Chốt theo use case trước khi tạo migration.

## 13. Quy tắc nghiệp vụ PHẢI giữ trong code

### 13.1. Điểm danh và báo ăn

- Giáo viên chỉ sửa điểm danh của lớp được phân công và ngày học đang mở.
- Một trẻ chỉ có **một** trạng thái điểm danh cho một ngày/lớp; gửi lặp KHÔNG tạo bản ghi trùng (upsert + unique constraint).
- Số suất ăn = trẻ **có mặt** và **có đăng ký ăn**; KHÔNG tính từ tổng sĩ số lớp.
- Thay đổi sau khi BGH xác nhận PHẢI tạo lịch sử và yêu cầu xác nhận lại.
- Bếp chỉ thấy số đã xác nhận; chưa xác nhận PHẢI trả trạng thái chờ, không trả như số cuối cùng.
- Trẻ đến muộn/nghỉ sau lần tổng hợp: đánh dấu số liệu đã thay đổi và thông báo vai trò cần xử lý.

### 13.2. Hồ sơ trẻ

- Bản ghi lưu theo hồ sơ trẻ, có lịch sử theo thời gian.
- Phụ huynh chỉ xem phần nhà trường cho phép chia sẻ, của trẻ đã liên kết.
- Dữ liệu sức khỏe chỉ hỗ trợ theo dõi, không phải chẩn đoán.

### 13.3. AI hỗ trợ thực đơn

- Dữ liệu thực phẩm/dinh dưỡng lấy từ bảng do nhóm nhập và kiểm duyệt; AI KHÔNG ĐƯỢC tự sinh giá trị dinh dưỡng.
- Backend PHẢI kiểm tra dị ứng, dữ liệu bắt buộc và ngân sách bằng logic xác định; KHÔNG chỉ dựa vào prompt.
- AI chỉ tạo phương án tham khảo; người có thẩm quyền duyệt/sửa/từ chối. AI KHÔNG ĐƯỢC tự quyết định thực đơn cuối.
- AI lỗi/timeout/sai định dạng: trả lỗi xử lý được (`SERVICE_UNAVAILABLE` / `GATEWAY_TIMEOUT`), giữ nguyên dữ liệu người dùng đã nhập.
- Ghi model/provider và phiên bản prompt theo cấu hình; KHÔNG ghi dữ liệu cá nhân trẻ trong request log.
- Gọi AI chỉ qua `AiClient` trong `integration/`.

## 14. Test và vận hành

- Service: unit test bằng JUnit + Mockito cho hành vi chính, lỗi nghiệp vụ và trường hợp bị từ chối quyền.
- Repository/migration/luồng tích hợp: Testcontainers với PostgreSQL thật.
- Controller NÊN có test kiểm tra dạng response `{code, desc, data}` và HTTP status cho thành công + lỗi.
- Test KHÔNG dùng dữ liệu thật của trẻ/phụ huynh; dùng dữ liệu giả.
- Docker Compose (API + PostgreSQL) cho dev/demo; CI chạy build + test.
- Secret qua biến môi trường; `.env.example` chỉ chứa tên biến.

## 15. Thứ tự triển khai

1. **Nền móng:** repo, build, cấu hình môi trường, PostgreSQL, Flyway, `ApiCode`/`ResponseJson`/exception handling, prefix API, OpenAPI.
2. **Đăng nhập và phạm vi:** user/role, campus/class assignment, liên kết guardian-child, test quyền.
3. **Luồng ưu tiên:** điểm danh → BGH xác nhận số ăn → bếp xem số liệu.
4. **Hồ sơ trẻ:** observation/health record, chia sẻ phụ huynh, audit, lịch sử.
5. **Mở rộng:** facility report, notification, thực đơn; AI chỉ sau khi dữ liệu và baseline sẵn sàng.

## 16. Khi guide chưa đủ

- KHÔNG tự đặt convention mới. Theo pattern đang có trong source; nếu chưa có, nêu khoảng trống và hỏi nhóm.
- Không tự biến chi tiết nghiệp vụ chưa được xác nhận thành business rule.
- Khi đã thống nhất convention mới, cập nhật file này cùng commit với code.

## 17. Checklist trước khi hoàn thành

Chạy trên diff trước khi tạo PR hoặc báo task xong. Mục không áp dụng thì ghi rõ "không áp dụng".

- [ ] Controller không inject/gọi repository; không chứa logic nghiệp vụ.
- [ ] Không trả entity qua API; dùng DTO + mapper.
- [ ] Service không dùng `HttpStatus`, `ResponseEntity`, `ResponseJson`; lỗi nghiệp vụ ném `GlobalException(ApiCode, desc)`.
- [ ] Controller không hardcode prefix API; prefix lấy từ `carenest.api.prefix`.
- [ ] Mọi response (kể cả lỗi mới thêm) có dạng `{code, desc, data}`, `code` = HTTP status, lấy từ `ApiCode`.
- [ ] Exception mới được `GlobalExceptionHandler` map đúng `ApiCode`, không rơi vào 500 ngoài ý muốn.
- [ ] Request body/param có `@Valid` / constraint phù hợp.
- [ ] Quyền theo campus/class/child được kiểm tra ở server, đối chiếu assignment.
- [ ] Log theo format `[MethodName]|param=value|...`; không có password, token, dữ liệu sức khỏe/cá nhân trẻ.
- [ ] Không có catch rỗng; khi ném lại có giữ cause.
- [ ] Đổi schema bằng migration Flyway mới; không sửa migration đã chạy.
- [ ] Không gọi SDK AI/storage/notification trực tiếp ngoài `integration/`.
- [ ] Quy tắc nghiệp vụ ở mục 13 liên quan được giữ (idempotent điểm danh, suất ăn từ trẻ có mặt + đăng ký ăn, AI không quyết định cuối...).
- [ ] Có test cho hành vi chính, lỗi quan trọng và trường hợp bị từ chối quyền.
- [ ] OpenAPI cập nhật nếu đổi endpoint/DTO; đã nêu tác động FE/APP.
- [ ] Comment chỉ ở logic chính và ngắn (mục 6.2); không code comment-out, không thông tin AI.
- [ ] Không commit secret, `.env`, dữ liệu thật.
