---
paths:
  - "src/main/java/com/carenest/controller/**"
---

# Controller — CareNest_BE

Nguồn chuẩn: guide mục 5, 7, 8, 10; ADR-0016.

- Chỉ gọi service; không inject repository; không logic nghiệp vụ; không trả entity.
- `@RequestMapping("/resource")` — không ghi prefix API (prefix từ `carenest.api.prefix`).
- `@Valid` request DTO; trả `ResponseJson.toJsonWithData(ApiCode.X, desc, data)` / `ResponseJson.toJson(...)`. Lỗi ⇒ để service ném `GlobalException`; controller không `try-catch`, `GlobalExceptionHandler` trả lỗi.
- `LOG_PREFIX = "[TênController]"`; mỗi endpoint: `logPrefix = LOG_PREFIX + "|tênMethod|id=..."` → `log.info` START → gán kết quả service vào biến → `log.info` END → `return ResponseJson...`. Không `finally`, không log DTO/token/cookie/email.
- Danh sách: phân trang, `data` = `PageResponse`.
- Xác nhận/duyệt: `POST /{id}/confirm|approve`. DTO phụ huynh riêng, chỉ field được phép (ADR-0010).
- Đổi endpoint/DTO ⇒ hỏi nhóm, cập nhật snapshot `docs/api/openapi.yaml`, nêu tác động FE/APP (`.ai/workflows/update-api.md`).
