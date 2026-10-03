# Package Structure

**Nguồn chuẩn:** `docs/backend-coding-guide.md` mục 3–5 (layered monolith, đã có trong source `src/main/java/com/carenest/`). File này chỉ bổ sung cách **module nghiệp vụ** (docs/modules) ánh xạ vào cấu trúc lớp đó. Mâu thuẫn ⇒ guide và source thắng.

## Cấu trúc (theo guide)

```text
com.carenest
├── controller/<feature>/   # REST, @Valid, gọi service, trả ResponseJson
├── service/<feature>/      # nghiệp vụ, @Transactional, kiểm tra quyền theo scope
├── repository/<feature>/   # Spring Data JPA
├── entity/<feature>/       # JPA entity — không trả qua API
├── dto/<feature>/          # request/response DTO (dto/common: PageResponse, FieldErrorResponse)
├── mapper/<feature>/       # Entity <-> DTO
├── integration/            # interface + adapter AI (AiClient), storage (StorageService), notification
├── security/               # xác thực, kiểm tra quyền (chưa triển khai — auth chưa chốt)
├── config/                 # WebMvcConfig (prefix API), OpenApiConfig
├── exception/              # GlobalException, GlobalExceptionHandler
├── utils/                  # ApiCode, ResponseJson
└── common/
```

Không tạo package `domain/`, không tách module theo DDD (guide mục 3).

## Module nghiệp vụ → feature sub-package

Một "module" trong `docs/modules/` = một **feature** trải qua các lớp (`controller/<feature>`, `service/<feature>`, ...). Tên feature: guide đã nêu ví dụ `attendance`, `child`, `meals`, `facilities`; các tên còn lại là **PROPOSED** — dùng tên đã có trong source nếu khác.

| Module (docs) | Feature sub-package | Ghi chú |
| --- | --- | --- |
| identity-access | `account` (PROPOSED) + `security/` | Auth chưa chốt |
| school-structure | `organization` (PROPOSED) | Campus, Class, StaffAssignment |
| child | `child` | |
| attendance | `attendance` | Gồm đơn nghỉ |
| nutrition | `meals` | Thực đơn (MealPlan), số suất, định lượng |
| health | `health` (PROPOSED) | |
| learning-observation | `observation` (PROPOSED) | |
| facility-issue | `facilities` | FacilityReport |
| notification | `notification` (PROPOSED) + `integration/` | |
| audit | `audit` (PROPOSED) | AuditEvent |
| ai-assistance | `integration/` (`AiClient`) | Không có service nghiệp vụ riêng |
| reporting | `report` (PROPOSED) | Chỉ đọc |

## Quy tắc ranh giới giữa feature (bổ sung guide mục 4)

1. Service của feature A **không** inject repository của feature B; cần dữ liệu B ⇒ gọi service B (guide cho phép service gọi service).
2. Giữ chiều phụ thuộc theo `docs/system/MODULE_MAP.md`; không tạo vòng (vd. `MealCountService → AttendanceService` được, chiều ngược lại không).
3. Khi cần phản ứng ngược chiều (attendance đổi sau khi suất đã xác nhận) ⇒ Spring application event thay vì gọi service ngược (ADR-0004, PROPOSED).
4. Entity tham chiếu entity feature khác: ưu tiên lưu ID; quan hệ JPA xuyên feature chỉ khi có lý do và không tạo vòng — chốt khi vẽ ERD.
5. Test đặt cùng cấu trúc trong `src/test/java/com/carenest/`.
